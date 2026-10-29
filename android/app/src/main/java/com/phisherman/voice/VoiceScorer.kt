package com.phisherman.voice

import com.phisherman.engine.Level
import com.phisherman.engine.Verdict
import com.phisherman.sms.SmsModel
import com.phisherman.sms.SmsNormalizer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Phrase categories from voice_rules.json (PS4 9.3). */
class VoiceRules(json: String) {
    class Category(val id: String, val weight: Int, val en: String, val hi: String, val latin: List<Regex>, val devanagari: List<String>)

    private val root = Json.parseToJsonElement(json).jsonObject
    val version: String = root["version"]!!.jsonPrimitive.content
    val windowMs: Long = root["window_seconds"]!!.jsonPrimitive.int * 1000L
    val suspicious: Int = root["bands"]!!.jsonObject["suspicious"]!!.jsonPrimitive.int
    val dangerous: Int = root["bands"]!!.jsonObject["dangerous"]!!.jsonPrimitive.int
    class Combo(val id: String, val bonus: Int, val anyOf: Set<String>, val withAnyOf: Set<String>)

    /** Each combo adds its bonus once when a category from both sets was heard in the window. */
    val combos: List<Combo> = root["combos"]!!.jsonArray.map { e ->
        val o = e.jsonObject
        Combo(o["id"]!!.jsonPrimitive.content, o["bonus"]!!.jsonPrimitive.int,
            o["any_of"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet(),
            o["with_any_of"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet())
    }
    val categories: List<Category> = root["categories"]!!.jsonArray.map { e ->
        val o = e.jsonObject
        Category(
            o["id"]!!.jsonPrimitive.content, o["weight"]!!.jsonPrimitive.int,
            o["en"]!!.jsonPrimitive.content, o["hi"]!!.jsonPrimitive.content,
            o["latin"]!!.jsonArray.map { Regex("(?<![a-z])" + Regex.escape(it.jsonPrimitive.content) + "(?![a-z])") },
            o["devanagari"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
    }
}

data class VoiceScore(val score: Int, val level: Level, val categories: List<String>)

class VoiceScorer(private val rules: VoiceRules) {

    /** Scores one transcript window. Stateless; CallSession adds the window and hysteresis. */
    fun score(transcript: String): VoiceScore {
        val text = normalize(transcript)
        val found = rules.categories.filter { c -> matches(c, text) }.map { it.id }
        var score = rules.categories.filter { it.id in found }.sumOf { it.weight }
        for (c in rules.combos) if (found.any { it in c.anyOf } && found.any { it in c.withAnyOf }) score += c.bonus
        score = score.coerceAtMost(100)
        val level = when {
            score >= rules.dangerous -> Level.DANGEROUS
            score >= rules.suspicious -> Level.SUSPICIOUS
            else -> Level.SAFE
        }
        return VoiceScore(score, level, found)
    }

    private fun matches(c: VoiceRules.Category, text: String): Boolean {
        // OTP / PIN / CVV counts only when someone ASKS for it ("tell me the OTP", "OTP batao"),
        // not when a genuine bank mentions it ("you'll get an OTP, enter it in the app") or warns
        val credential = c.id == "credential"
        fun counts(at: Int) = !credential || (!negated(text, at) && asked(text, at))
        for (rx in c.latin) {
            for (m in rx.findAll(text)) if (counts(m.range.first)) return true
        }
        for (p in c.devanagari) {
            var i = text.indexOf(p)
            while (i >= 0) {
                if (counts(i)) return true
                i = text.indexOf(p, i + 1)
            }
        }
        return false
    }

    /** An asking verb within a few words of the credential word. */
    private fun asked(text: String, at: Int): Boolean =
        ASK.containsMatchIn(text.substring(maxOf(0, at - 40), minOf(text.length, at + 45)))

    /** "never share your OTP", "OTP kisi ko mat batana": a warning, not a request. */
    private fun negated(text: String, at: Int): Boolean {
        val before = text.substring(maxOf(0, at - 25), at)
        val after = text.substring(at, minOf(text.length, at + 30))
        return NEG_BEFORE.containsMatchIn(before) || NEG_AFTER.containsMatchIn(after)
    }

    companion object {
        private val NEG_BEFORE = Regex("(?<![a-z])(never|don't|dont|do not|not to)(?![a-z])|मत|कभी")
        private val ASK = Regex(
            "(?<![a-z])(tell|say|share|give|giving|gimme|send|read|confirm|forward|provide|need|want|batao|bataiye|bataye|bata|bolo|boliye|bol do|dijiye|de do|do na|bhejo|chahiye)(?![a-z])" +
                "|बताओ|बताइए|बताइये|बोलिए|बोलो|दीजिए|भेजो|चाहिए")
        private val NEG_AFTER =Regex("(?<![a-z])(mat|nahi|na) (batana|batao|bataiye|batayein|share|dena|dijiye)(?![a-z])|मत बताना|न बताएं")

        /** Lower-case, punctuation to spaces ("o.t.p" -> "o t p"), single spaces; Devanagari kept. */
        fun normalize(s: String): String {
            val sb = StringBuilder(s.length)
            for (c in s.lowercase()) {
                sb.append(if (c.isLetterOrDigit() || c == '\'' || c in 'ऀ'..'ॿ') c else ' ')
            }
            return sb.toString().split(' ').filter { it.isNotEmpty() }.joinToString(" ")
        }
    }
}

/**
 * The call model trained by ml/train_calls.py on public scam-call datasets (call_model.json, same
 * format as the SMS model). It scores the whole call so far, like the training prefixes.
 */
class CallModel(private val model: SmsModel, val suspicious: Double, val dangerous: Double, val minWords: Int) {
    fun score(transcript: String): Pair<Double, Level> {
        val words = SmsNormalizer.splitWs(transcript)
        if (words.size < minWords) return 0.0 to Level.SAFE
        val p = model.prob(SmsNormalizer.normalize(words.takeLast(MAX_WORDS).joinToString(" ")).text)
        return p to when {
            p >= dangerous -> Level.DANGEROUS
            p >= suspicious -> Level.SUSPICIOUS
            else -> Level.SAFE
        }
    }

    companion object {
        const val ID = "call_model"
        private const val MAX_WORDS = 400

        fun load(modelJson: String, thresholdsJson: String): CallModel {
            val th = Json.parseToJsonElement(thresholdsJson).jsonObject["call"]!!.jsonObject
            return CallModel(SmsModel(modelJson), th["suspicious"]!!.jsonPrimitive.double,
                th["dangerous"]!!.jsonPrimitive.double, th["min_words"]!!.jsonPrimitive.int)
        }
    }
}

/** What a context model (the on-device LLM) said about the window. */
data class Judgement(val level: Level, val reason: String)

/**
 * One guarded call: a rolling transcript window, the rule score, an optional LLM opinion that can
 * only raise the level, and hysteresis (once DANGEROUS, the rest of the call stays DANGEROUS).
 */
class CallSession(
    private val rules: VoiceRules,
    private val model: CallModel? = null,
    /** The link engine: judges web addresses read out on the call (null = not checked). */
    private val linkCheck: ((String) -> Verdict?)? = null,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val scorer = VoiceScorer(rules)
    private val finals = ArrayDeque<Pair<Long, String>>()
    private var partial = ""
    var llm: Judgement? = null
        private set
    var latched: Level = Level.SAFE
        private set
    var maxScore = 0
        private set
    val seen = LinkedHashSet<String>()  // every category heard during the call
    val transcriptLog = mutableListOf<String>()

    fun addFinal(text: String) {
        if (text.isBlank()) return
        finals.addLast(now() to text)
        transcriptLog += text
        partial = ""
    }

    fun setPartial(text: String) { partial = text }

    fun windowText(): String {
        val cutoff = now() - rules.windowMs
        while (finals.isNotEmpty() && finals.first().first < cutoff) finals.removeFirst()
        return (finals.map { it.second } + partial).filter { it.isNotBlank() }.joinToString(" ")
    }

    /** Raise-only: the LLM can make things riskier, never safer. */
    fun applyJudgement(j: Judgement) {
        if (llm == null || j.level > llm!!.level) llm = j
    }

    /** The whole call so far (the trained model looks at all of it, the rules at the last minute). */
    fun callText(): String = (transcriptLog + partial).filter { it.isNotBlank() }.joinToString(" ")

    var modelProb = 0.0
        private set

    /** Every web address heard on the call and what the link engine said about it. */
    val links = LinkedHashMap<String, Level>()
    /** Addresses that are not a known official site (allowlist); captions often garble the name. */
    private val unknownLinks = LinkedHashSet<String>()
    val riskyLinks: List<String> get() = links.filter { (u, lv) -> lv >= Level.SUSPICIOUS || u in unknownLinks && VISIT in seen }
        .keys.map { it.removePrefix("https://") }

    /**
     * Links read out on the call (only from finished caption lines, so a half-said "amazon dot co"
     * is never judged). A two-word name is also tried joined ("sbi rewards" -> sbi-rewards.com),
     * unless the address as heard is on the allowlist.
     */
    private fun checkDictatedLinks(check: (String) -> Verdict?): Level {
        for (c in SpokenLinks.find(transcriptLog.joinToString(" "))) {
            if (c.url in links) continue
            fun official(x: Verdict?) = x?.ruleHits?.firstOrNull() == "allowlist"
            val v = check(c.url)
            val alts = if (official(v)) emptyList() else c.alternatives.mapNotNull(check)
            // any reading that is a known official site ("book my show" -> bookmyshow.com) wins
            val known = official(v) || alts.any(::official)
            links[c.url] = if (known) Level.SAFE else (listOfNotNull(v) + alts).maxOfOrNull { it.level } ?: Level.SAFE
            if (!known && v != null) unknownLinks += c.url
        }
        return links.values.maxOrNull() ?: Level.SAFE
    }

    fun update(): VoiceScore {
        val s = scorer.score(windowText())
        seen += s.categories
        maxScore = maxOf(maxScore, s.score)
        var level = s.level
        model?.let { m ->
            val (p, lv) = m.score(callText())
            modelProb = maxOf(modelProb, p)
            if (lv >= Level.SUSPICIOUS) seen += CallModel.ID
            if (lv > level) level = lv
        }
        linkCheck?.let { check ->
            val flagged = checkDictatedLinks(check)
            if (flagged >= Level.SUSPICIOUS) {
                seen += DICTATED_LINK
                // a site the link engine flags, read out with "log in here" or other warning signs: red
                val lv = if (level >= Level.SUSPICIOUS || VISIT in seen) Level.DANGEROUS else flagged
                if (lv > level) level = lv
            } else if (unknownLinks.isNotEmpty() && VISIT in seen) {
                // "that's the link, log in here" about a site that is not a known official one is a
                // warning even when the link engine has nothing against it (captions garble names)
                seen += DICTATED_LINK
                val lv = if (level >= Level.SUSPICIOUS) Level.DANGEROUS else Level.SUSPICIOUS
                if (lv > level) level = lv
            }
        }
        llm?.let { j ->
            // the AI alone reaches at most amber; it can lift amber (rules or the call model already
            // heard warning signs) to red, but a call with no other signal never goes red on its word
            val cap = if (level >= Level.SUSPICIOUS) Level.DANGEROUS else Level.SUSPICIOUS
            val ai = if (j.level > cap) cap else j.level
            if (ai > level) level = ai
        }
        if (level > latched) latched = level
        if (latched == Level.DANGEROUS) level = Level.DANGEROUS  // no flicker once red
        return s.copy(level = level)
    }

    companion object {
        const val DICTATED_LINK = "dictated_link"
        private const val VISIT = "visit_link"
    }
}
