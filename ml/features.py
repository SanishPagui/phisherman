"""
features.py — URL feature extraction (SOURCE OF TRUTH).

This module defines the 18 lexical URL features used by the link model. It is the
canonical reference implementation: the Kotlin port in
`android/.../link/UrlFeatures.kt` must reproduce every value within 1e-4 (see the
parity test in Section 6.7 of the PS4 guide).

No network calls are made. Every feature is computed from the URL string alone, so
the analyser works fully offline on the device.
"""

import math
import re
from urllib.parse import urlsplit, unquote

# Bump this string whenever the feature set, order, or parsing rules change. The
# Android app asserts this against feature_order.json at startup and refuses to run
# on a stale model (prevents the "trained on one order, called with another" bug).
FEATURE_VERSION = "link-1"

# All word/suffix/TLD/homoglyph lists live in brands.json (shared with the Android app),
# so Python and Kotlin read ONE copy and cannot drift apart. Nothing is hardcoded here.
def _cfg(brands: dict) -> dict:
    return {
        "multi": set(brands["multiSuffixes"]),
        "shorteners": set(brands["shorteners"]),
        "susp": list(brands["suspWords"]),
        "risky_tld": set(brands["riskyTlds"]),
        "hg_multi": [tuple(p) for p in brands["homoglyphMulti"]],
        "hg_single": dict(brands["homoglyphSingle"]),
    }


# Ordered feature names. The Android side asserts this exact order on load.
FEATURE_ORDER = [
    "url_len", "host_len", "dot_count", "subdomain_depth", "digit_ratio",
    "hyphen_count_host", "special_chars", "has_at", "has_ip_host", "is_https",
    "host_entropy", "is_shortener", "susp_words", "path_len", "num_params",
    "brand_min_lev", "brand_in_subdomain", "tld_risk",
]

# Features are capped so a pathological multi-kilobyte URL cannot blow up memory or
# latency (edge case 12.1 "Very long URL").
MAX_URL_LEN_FOR_FEATURES = 4000


def entropy(s: str) -> float:
    """Shannon entropy (bits per symbol) of a string."""
    if not s:
        return 0.0
    n = len(s)
    freq: dict = {}
    for ch in s:
        freq[ch] = freq.get(ch, 0) + 1
    return -sum((v / n) * math.log2(v / n) for v in freq.values())


def lev(a: str, b: str) -> int:
    """Levenshtein (edit) distance between two strings."""
    if a == b:
        return 0
    if not a:
        return len(b)
    if not b:
        return len(a)
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]


def dehomoglyph(s: str, cfg: dict) -> str:
    """Fold common look-alike characters to their plain form (both sides share this)."""
    for a, b in cfg["hg_multi"]:
        s = s.replace(a, b)
    return "".join(cfg["hg_single"].get(ch, ch) for ch in s)


def split_host(host: str, multi: set):
    """Return (registrable_domain, [subdomain labels]) using the MULTI suffix list."""
    labels = host.split(".")
    n = 3 if len(labels) >= 3 and ".".join(labels[-2:]) in multi else 2
    if len(labels) < n:
        return host, []
    return ".".join(labels[-n:]), labels[:-n]


def is_ip_host(host: str) -> bool:
    """True for a dotted IPv4 host or an IPv6 host (contains ':')."""
    return bool(re.fullmatch(r"\d{1,3}(\.\d{1,3}){3}", host)) or ":" in host


def features(url: str, brands: dict):
    """
    Compute the 18-feature vector for `url`.

    `brands` is the parsed brands.json with keys "tokens" (list) and "official" (list).
    Returns a list[float] in FEATURE_ORDER.
    """
    cfg = _cfg(brands)
    raw = url.strip()
    # Cap length used for feature computation (keep the real length for url_len).
    url_len = len(raw)
    work = raw[:MAX_URL_LEN_FOR_FEATURES]

    u = work
    if "://" not in u:
        u = "http://" + u
    sp = urlsplit(u)
    host = (sp.hostname or "").lower().rstrip(".")
    is_ip = is_ip_host(host)
    reg, sub = ("", []) if is_ip else split_host(host, cfg["multi"])
    label = reg.split(".")[0] if reg else ""
    path = unquote(sp.path or "").lower()
    low = host + path

    # Brand features (16, 17).
    tokens = brands.get("tokens", [])
    official = brands.get("official", [])
    folded = dehomoglyph(label, cfg)
    lev_min = 1.0
    for t in tokens:
        if folded:
            lev_min = min(lev_min, lev(folded, t) / max(len(folded), len(t)))
    is_official = reg in official
    in_sub = int(any(t in host for t in tokens) and not is_official)

    return [
        float(url_len),                                              # 1 url_len
        float(len(host)),                                            # 2 host_len
        float(host.count(".")),                                      # 3 dot_count
        float(len(sub)),                                             # 4 subdomain_depth
        sum(c.isdigit() for c in work) / max(len(work), 1),          # 5 digit_ratio
        float(host.count("-")),                                      # 6 hyphen_count_host
        float(sum(work.count(c) for c in "@?=&%_")),                 # 7 special_chars
        float(int("@" in sp.netloc)),                                # 8 has_at
        float(int(is_ip)),                                           # 9 has_ip_host
        float(int(sp.scheme == "https")),                            # 10 is_https
        entropy(host),                                               # 11 host_entropy
        float(int(host in cfg["shorteners"])),                              # 12 is_shortener
        float(sum(low.count(w) for w in cfg["susp"])),                      # 13 susp_words
        float(len(sp.path or "")),                                   # 14 path_len
        float(len([p for p in (sp.query or "").split("&") if p])),   # 15 num_params
        float(lev_min),                                              # 16 brand_min_lev
        float(in_sub),                                               # 17 brand_in_subdomain
        float(int(reg.split(".")[-1] in cfg["risky_tld"] or "xn--" in host)),  # 18 tld_risk
    ]


def feature_dict(url: str, brands: dict) -> dict:
    """Same as features() but returns {name: value} — handy for debugging/parity logs."""
    return dict(zip(FEATURE_ORDER, features(url, brands)))


def parse_meta(url: str, brands: dict):
    """
    Return small human-readable parsing facts used by the reason generator
    (registrable domain, full host, scheme-missing flag). Mirrors UrlFeatures.kt.
    """
    raw = url.strip()
    scheme_missing = "://" not in raw
    u = ("http://" + raw) if scheme_missing else raw
    sp = urlsplit(u)
    host = (sp.hostname or "").lower().rstrip(".")
    is_ip = is_ip_host(host)
    reg, sub = ("", []) if is_ip else split_host(host, _cfg(brands)["multi"])
    return {
        "host": host,
        "registrable": reg,
        "subdomains": sub,
        "is_ip": is_ip,
        "scheme_missing": scheme_missing,
        "is_https": sp.scheme == "https",
    }


if __name__ == "__main__":
    import json
    import os

    here = os.path.dirname(__file__)
    brands_path = os.path.join(here, "..", "android", "app", "src", "main", "assets", "brands.json")
    with open(brands_path, encoding="utf-8") as fh:
        brands = json.load(fh)

    samples = [
        "https://www.wikipedia.org/",
        "https://examplebank-kyc-update.example/login",
        "http://203.0.113.10/verify/account",
        "https://paytrn.example/",
        "https://bit.ly/example",
    ]
    for s in samples:
        print(s)
        for name, val in feature_dict(s, brands).items():
            print(f"    {name:20s} {val}")
        print()
