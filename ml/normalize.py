"""
normalize.py — SMS text normalisation (SOURCE OF TRUTH).

The SAME rules are ported to `android/.../sms/Normalizer.kt`. Keep them identical or
the TF-IDF vocabulary lookups diverge between Python and Kotlin. See Section 8.2.
"""

import re

NORM_VERSION = "sms-norm-1"

# URLs -> URLTOKEN (the link itself is scored separately by the URL model).
_URL_RE = re.compile(r"(https?://\S+|www\.\S+|\b\S+\.(?:com|in|net|org|xyz|top|info|link|co)\b\S*)", re.I)

# 4-8 digit codes look like OTPs; longer/other digit runs are generic numbers.
_OTP_RE = re.compile(r"\b\d{4,8}\b")
_NUM_RE = re.compile(r"\b\d{2,}\b")

# Collapse Hinglish/spelling variants to one canonical form (small data needs fewer
# variants). Applied as whole-word replacements after lower-casing.
_VARIANTS = {
    "k.y.c": "kyc", "kyc": "kyc",
    "aadhar": "aadhaar", "aadhaar": "aadhaar",
    "blocked": "block", "band": "block", "blokced": "block",
    "jaldi": "turant", "turant": "turant",
    "verifcation": "verification", "verify": "verify",
    "a/c": "account", "acc": "account", "ac": "account",
    "rs.": "rupees", "inr": "rupees",
}

_WS_RE = re.compile(r"\s+")
_PUNCT_RE = re.compile(r"[^\w\s]")


def normalize(text: str) -> str:
    """Return the normalised token stream used for TF-IDF featurisation."""
    if not text:
        return ""
    t = text.lower().strip()
    t = _URL_RE.sub(" URLTOKEN ", t)
    t = _OTP_RE.sub(" OTPCODE ", t)
    t = _NUM_RE.sub(" NUM ", t)
    # Deobfuscation: spaced/dotted letters and leet digits inside words.
    t = t.replace("1", "i").replace("0", "o").replace("3", "e").replace("@", "a")
    # Drop punctuation but keep our ALLCAPS tokens intact (they are letters only).
    t = _PUNCT_RE.sub(" ", t)
    words = []
    for w in t.split():
        words.append(_VARIANTS.get(w, w))
    t = " ".join(words)
    t = _WS_RE.sub(" ", t).strip()
    return t


def extract_urls(text: str):
    """Return raw URLs found in the message (scored by the URL model separately)."""
    if not text:
        return []
    return [m.group(0) for m in _URL_RE.finditer(text)]


if __name__ == "__main__":
    for s in [
        "Your a/c will be blocked today! Update KYC at http://sbi-kyc.xyz/login",
        "Your OTP is 123456. Do not share with anyone.",
        "Aapka khata band ho jayega, turant KYC update karein www.verify-now.top",
    ]:
        print(repr(s))
        print("  ->", repr(normalize(s)))
        print("  urls:", extract_urls(s))
