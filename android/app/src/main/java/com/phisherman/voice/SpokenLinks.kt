package com.phisherman.voice

/**
 * Web addresses read out on a call, rebuilt from Live Caption text so the link engine can judge
 * them: "s b i dash rewards dot com slash claim" -> sbi-rewards.com/claim. Also picks up links the
 * captioner already wrote as text ("sbi-rewards.com").
 *
 * A name said as two words ("sbi rewards dot com") is ambiguous, so the word before the domain is
 * offered as an alternative (sbirewards.com, sbi-rewards.com) unless it is an ordinary word.
 */
object SpokenLinks {
    data class Candidate(val url: String, val alternatives: List<String>)

    private const val JOIN = '\u0001'  // marks separators that were SPOKEN, so only those glue words together

    private val SPOKEN = listOf(
        Regex("(?<![a-z])(?:dot|daat|डॉट)(?![a-z])") to ".",
        Regex("(?<![a-z])slash(?![a-z])|स्लैश") to "/",
        // Live Caption often hears "dash" as "bash", or glues it to the next word ("dashreward.com")
        Regex("(?<![a-z])(?:dash|hyphen|bash)(?![a-z])|डैश") to "-",
        Regex("(?<![a-z])(?:dash|bash)(?=[a-z0-9]{2,}\\.(?:$TLDS)(?![a-z]))") to "-",
        Regex("(?<![a-z])underscore(?![a-z])") to "_",
    )
    private val WWW = Regex("(?<![a-z])(?:w w w|triple w|double u double u double u|double you double you double you)(?![a-z])")
    private val SCHEME = Regex("(?<![a-z])h t t p s?(?![a-z])")
    private val AROUND_JOIN = Regex("\\s*$JOIN([./_-])$JOIN\\s*")
    private val HINDI_TLD = mapOf(".कॉम" to ".com", ".इन" to ".in", ".नेट" to ".net")

    private const val TLDS = "com|in|net|org|co|info|xyz|top|online|site|link|live|app|shop|store|club|me|io|biz|gov|bank|cc|tk|icu|buzz|support|help|click|vip|win"
    private val DOMAIN = Regex(
        "(?<![a-z0-9._-])((?:https?://)?(?:[a-z0-9-]+\\.)+(?:$TLDS)(?:/[a-z0-9._~/?=&%-]*)?)(?![a-z0-9])")

    /** Ordinary words that come before a domain ("go to", "open"): never part of the name. */
    /** Small words that can sit inside a spoken name ("book my show"), but never start one. */
    private val WEAK = setOf("my", "your", "our", "of", "for", "and")
    private val STOP = setOf(
        "go", "to", "open", "visit", "on", "at", "the", "a", "an", "website", "site", "link", "type", "this", "is",
        "our", "my", "your", "official", "and", "or", "just", "now", "please", "sir", "madam", "ma'am", "ji", "par", "pe",
        "jao", "kholo", "karo", "www", "http", "https", "called", "named", "address", "url", "in", "of", "from", "for",
        "it's", "its", "here", "there", "browser", "chrome", "app", "search", "enter", "write", "say", "says", "then",
    )

    fun find(transcript: String): List<Candidate> {
        var s = transcript.lowercase()
        s = SCHEME.replace(s) { m -> m.value.replace(" ", "") }
        s = WWW.replace(s, "www")
        for ((rx, sep) in SPOKEN) s = rx.replace(s, " $JOIN$sep$JOIN ")
        s = s.trim().split(Regex("\\s+")).joinToString(" ")
        s = spelledLetters(s)
        s = AROUND_JOIN.replace(s) { it.groupValues[1] }
        for ((h, t) in HINDI_TLD) s = s.replace(h, t)
        s = s.replace(JOIN.toString(), "")
        s = s.replace(Regex("(?<=[a-z0-9])[,!?;)]+(?=\\s|$)"), "").replace(Regex("\\.(?=\\s|$)"), " ")
        val out = LinkedHashMap<String, Candidate>()
        for (m in DOMAIN.findAll(s)) {
            val raw = m.groupValues[1].trimEnd('/', '.', '-')
            val url = withScheme(raw)
            if (url in out) continue
            // up to 3 words before the domain may belong to the name ("book my show dot com")
            val name = s.substring(0, m.range.first).trimEnd().split(' ').asReversed().asSequence()
                .takeWhile { w -> w.length >= 2 && w.all { it in 'a'..'z' || it in '0'..'9' } && (w !in STOP || w in WEAK) }
                .take(3).toList()
            val alts = if (name.isNotEmpty() && !name.all { it in WEAK } && !raw.contains("://")) {
                val bare = raw.removePrefix("www.")
                (1..name.size).flatMap { k ->
                    val parts = name.take(k).asReversed()
                    listOf(withScheme(parts.joinToString("") + bare), withScheme(parts.joinToString("-") + "-" + bare))
                }
            } else emptyList()
            out[url] = Candidate(url, alts)
        }
        return out.values.toList()
    }

    private fun withScheme(u: String) = if (u.startsWith("http://") || u.startsWith("https://")) u else "https://$u"

    /** "s b i" -> "sbi": runs of 3+ single letters / digits are a spelled-out word. */
    private fun spelledLetters(s: String): String {
        val toks = s.split(' ')
        val out = mutableListOf<String>()
        var run = mutableListOf<String>()
        fun flush() {
            if (run.size >= 3) out += run.joinToString("") else out += run
            run = mutableListOf()
        }
        for (t in toks) {
            if (t.length == 1 && (t[0] in 'a'..'z' || t[0] in '0'..'9')) run += t else { flush(); out += t }
        }
        flush()
        return out.joinToString(" ")
    }
}
