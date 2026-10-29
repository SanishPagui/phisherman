"""Reference SMS decision logic (PS4 8.4). Kotlin sms/SmsAnalyser.kt must give the same results.

score = model probability on the normalised text
      + 0.15 if R_URGENT_LINK, + 0.15 if R_SENDER_MISMATCH        -> level by SMS thresholds
then  R_OTP_ASK / R_APP_INSTALL      -> DANGEROUS
      R_BRAND_LINK                   -> at least SUSPICIOUS
      any link verdict               -> message level is at least the link's level
      R_ALLOW_SENDER (header sender, every link SAFE, no OTP ask / app install / brand mismatch)
                                     -> capped at SAFE
"""
import json
import math
import re
from pathlib import Path

from normalize import normalize

ASSETS = Path(__file__).resolve().parent.parent / "android/app/src/main/assets"
LEVELS = ["SAFE", "SUSPICIOUS", "DANGEROUS"]
BONUS = 0.15
SHORT_MSG_WORDS = 4  # fewer words than this next to a link: ignore the text model
# a short text with no link ("Mummy call kar", Konkani / slang the model has never seen) is too
# little evidence for the word model alone: it must be sure enough for red before anything shows
SHORT_TEXT_WORDS = 8

A = "(?<![a-z])"   # ASCII word edges: same meaning in Python and Java
Z = "(?![a-z])"

ASK = r"(?:share|sharing|send|tell|give|forward|provide|reply with|enter)"
CRED = r"(?:otp|upi pin|atm pin|pin|cvv|password|passcode|card number|card details|bank details|account details|expiry date)"
ASK_RE = re.compile(f"{A}{ASK}{Z}[^.!?]{{0,40}}?{A}{CRED}{Z}")
HI_ASK_RE = re.compile(f"{A}{CRED}{Z}[^.!?]{{0,30}}?{A}(?:batao|bhejo|share karo|share karein|share kare|de do|dijiye|bata do){Z}")
NEGATION_RE = re.compile(f"{A}(?:not|never|don't|dont|mat|na|nahi|no one){Z}")

# an Android app file sent as a message / attachment, e.g. "KYC_Update.apk" or disguised "Form.pdf.apk"
APK_FILE_RE = re.compile(r"[a-z0-9_()\-.]+\.(?:apk|xapk|apks|apkm)(?![a-z0-9])")
APK_DISGUISED_RE = re.compile(r"\.(?:pdf|jpe?g|png|docx?|xlsx?|mp4|txt)\.(?:apk|xapk|apks|apkm)(?![a-z0-9])")
REMOTE_APPS_RE = re.compile(f"{A}(?:anydesk|teamviewer|quicksupport|quick support|rustdesk|airdroid){Z}")
INSTALL_APP_RE = re.compile(f"{A}(?:install|download){Z}[^.!?]{{0,50}}?{A}(?:app|apk|application){Z}")

URGENT_RE = re.compile(
    f"{A}(?:urgent|jaldi|today|tonight|expire|expires|expiring|expired|last warning|final notice|"
    f"block|suspend|deactivated|disconnected|cancelled|within [0-9]+ hours?|aaj|abhi){Z}|तुरंत|आज")
CLAIM_RE = re.compile(
    f"{A}(?:bank|sbi|hdfc|icici|axis|kotak|paytm|phonepe|rbi|income tax|uidai|aadhaar|police|cbi|customs|"
    f"trai|electricity|bijli|india post|courier|irctc|customer care|kyc){Z}|बैंक|खाता")
MOBILE_RE = re.compile(r"^(?:\+?91|0)?[6-9][0-9]{9}$")
HEADER_RE = re.compile(r"^[A-Z]{2}-[A-Z0-9]{3,9}(?:-[A-Z])?$")


def _asks(rx, s):
    """A match counts unless a negation word sits in it or just before it."""
    for m in rx.finditer(s):
        window = s[max(0, m.start() - 20):m.end()]
        if not NEGATION_RE.search(window):
            return True
    return False


def with_scheme(u):
    return u if "://" in u else "http://" + u


class SmsEngine:
    def __init__(self, link_engine=None, model=None):
        self.model = model or json.load(open(ASSETS / "sms_model.json", encoding="utf-8"))
        th = json.load(open(ASSETS / "thresholds.json"))["sms"]
        self.thr_s, self.thr_d = th["suspicious"], th["dangerous"]
        self.link = link_engine
        brands = json.load(open(ASSETS / "brands.json", encoding="utf-8"))
        self.brand_domains = brands["brand_domains"]
        self.brand_res = {t: re.compile(f"(?<![a-z0-9]){re.escape(t)}(?![a-z0-9])") for t in brands["tokens"]}

    # TF-IDF + logistic regression, written out so Kotlin can copy it line for line
    def prob(self, norm):
        z = self.model["intercept"]
        for part in ("word", "char"):
            m = self.model[part]
            grams = word_ngrams(norm, *m["ngram"]) if part == "word" else char_wb_ngrams(norm, *m["ngram"])
            tf = {}
            for g in grams:
                j = m["vocab"].get(g)
                if j is not None:
                    tf[j] = tf.get(j, 0) + 1
            vals = {j: (1.0 + math.log(c)) * m["idf"][j] for j, c in tf.items()}
            norm2 = math.sqrt(sum(v * v for v in vals.values())) or 1.0
            z += sum(m["coef"][j] * v / norm2 for j, v in vals.items())
        return 1.0 / (1.0 + math.exp(-z))

    def decide(self, sender, body, trusted_sender=False):
        """Return (level, hits, score, prob, link_level).

        trusted_sender: the app knows the sender is a saved contact or a verified business
        (a named sender in Google / Samsung Messages); treated like a registered header.
        """
        norm, urls = normalize(body)
        p = self.prob(norm)
        hits = []
        sender = (sender or "").strip()
        # a message that is basically just a link: the link verdict decides (PS4 12.2)
        score = 0.0 if urls and len(TOKEN_RE.findall(norm)) < SHORT_MSG_WORDS else p
        if urls and URGENT_RE.search(norm):
            hits.append("R_URGENT_LINK"); score += BONUS
        if MOBILE_RE.match(sender.replace(" ", "")) and CLAIM_RE.search(norm):
            hits.append("R_SENDER_MISMATCH"); score += BONUS
        score = min(score, 1.0)
        level = 2 if score >= self.thr_d else 1 if score >= self.thr_s else 0
        if not urls and not hits and len(TOKEN_RE.findall(norm)) < SHORT_TEXT_WORDS and level < 2:
            level = 0

        link_level = None
        for u in urls:
            lv = self.link.decide(with_scheme(u))[0] if self.link else None
            if lv is not None:
                li = LEVELS.index(lv)
                link_level = li if link_level is None else max(link_level, li)

        if _asks(ASK_RE, norm) or _asks(HI_ASK_RE, norm):
            hits.append("R_OTP_ASK"); level = 2
        if APK_FILE_RE.search(norm):
            hits.append("R_APK_FILE"); level = 2
        if (REMOTE_APPS_RE.search(norm) or ".apk" in " ".join(urls)
                or (INSTALL_APP_RE.search(norm) and link_level is not None and link_level > 0)):
            hits.append("R_APP_INSTALL"); level = 2
        for t, rx in self.brand_res.items():
            if rx.search(norm) and urls:
                official = set(self.brand_domains.get(t, []))
                regs = [self.link_reg(u) for u in urls]
                if any(r and r not in official for r in regs):
                    hits.append("R_BRAND_LINK"); level = max(level, 1)
                    break
        if link_level is not None and link_level > level:
            level = link_level
            hits.append("LINK")
        links_safe = not urls or link_level == 0
        if ((HEADER_RE.match(sender) or trusted_sender) and links_safe
                and not {"R_OTP_ASK", "R_APP_INSTALL", "R_BRAND_LINK", "R_APK_FILE"} & set(hits)):
            if level > 0:
                hits.append("R_ALLOW_SENDER")
            level = 0
        return LEVELS[level], hits, score, p, None if link_level is None else LEVELS[link_level]

    def link_reg(self, u):
        from features import registrable_domain
        return registrable_domain(with_scheme(u))


def word_ngrams(text, lo, hi):
    """sklearn word analyzer with token_pattern [\\w\\u0900-\\u097F]{2,} (see train_sms.py)."""
    toks = TOKEN_RE.findall(text)
    out = []
    for n in range(lo, hi + 1):
        for i in range(len(toks) - n + 1):
            out.append(" ".join(toks[i:i + n]))
    return out


def char_wb_ngrams(text, lo, hi):
    """sklearn char_wb analyzer, copied from VectorizerMixin._char_wb_ngrams."""
    out = []
    for w in text.split():
        w = " " + w + " "
        for n in range(lo, hi + 1):
            offset = 0
            out.append(w[offset:offset + n])
            while offset + n < len(w):
                offset += 1
                out.append(w[offset:offset + n])
            if offset == 0:
                break
    return out


TOKEN_PATTERN = r"(?u)[\wऀ-ॿ]{2,}"
TOKEN_RE = re.compile(TOKEN_PATTERN)
