"""
Unit tests for the URL feature parser (PS4 Section 13.1). Runs under pytest, or directly
with `python test_features.py`. Covers the edge cases that break naive parsers: IP hosts,
userinfo '@' tricks, multi-part suffixes, punycode, scheme-missing, deep nesting.
"""

import json
import os

from features import FEATURE_ORDER, features, parse_meta, split_host

MULTI = set(json.load(open(os.path.join(os.path.dirname(__file__), "..", "android", "app", "src", "main", "assets", "brands.json"), encoding="utf-8"))["multiSuffixes"])

HERE = os.path.dirname(__file__)
BRANDS = json.load(open(os.path.join(HERE, "..", "android", "app", "src", "main",
                                     "assets", "brands.json"), encoding="utf-8"))


def fd(url):
    return dict(zip(FEATURE_ORDER, features(url, BRANDS)))


def test_ip_host_detected():
    f = fd("http://203.0.113.10/verify/account")
    assert f["has_ip_host"] == 1
    assert f["subdomain_depth"] == 0
    assert f["brand_min_lev"] == 1.0  # no brand distance on an IP


def test_userinfo_trick_uses_host_after_at():
    m = parse_meta("https://examplebank.com@login-check.example/", BRANDS)
    assert m["host"] == "login-check.example"
    f = fd("https://examplebank.com@login-check.example/")
    assert f["has_at"] == 1


def test_multipart_suffix_registrable():
    reg, sub = split_host("login.sbi.co.in", MULTI)
    assert reg == "sbi.co.in"
    assert sub == ["login"]


def test_plain_two_label_registrable():
    reg, sub = split_host("attacker.example", MULTI)
    assert reg == "attacker.example"
    assert sub == []


def test_punycode_flagged_by_tld_risk():
    f = fd("https://xn--80ak6aa92e.example/")
    assert f["tld_risk"] == 1


def test_scheme_missing_still_parses():
    m = parse_meta("bank.example/login", BRANDS)
    assert m["scheme_missing"] is True
    assert m["host"] == "bank.example"
    assert m["is_https"] is False


def test_deep_nesting_depth():
    f = fd("https://login.secure.examplebank.com.attacker.example/")
    assert f["subdomain_depth"] == 4


def test_shortener_flag():
    f = fd("https://bit.ly/abc123")
    assert f["is_shortener"] == 1


def test_https_flag():
    assert fd("https://www.wikipedia.org/")["is_https"] == 1
    assert fd("http://example.xyz/")["is_https"] == 0


def test_risky_tld():
    assert fd("http://foo.xyz/")["tld_risk"] == 1
    assert fd("https://wikipedia.org/")["tld_risk"] == 0


def test_susp_words_counted():
    # susp_words counts host + decoded PATH only (not the query string).
    f = fd("https://host.example/login/verify/kyc")
    assert f["susp_words"] >= 3  # login, verify, kyc


def test_at_sets_special_chars():
    f = fd("https://a.com@b.example/")
    assert f["special_chars"] >= 1


def test_digit_ratio_range():
    f = fd("http://12345678.example/")
    assert 0.0 <= f["digit_ratio"] <= 1.0


def test_feature_vector_length():
    assert len(features("https://example.com/", BRANDS)) == 18


def test_brand_lookalike_small_distance():
    # paytrn is one edit from paytm after homoglyph fold
    f = fd("https://paytrn.example/")
    assert f["brand_min_lev"] < 0.3


def _run():
    tests = [v for k, v in sorted(globals().items()) if k.startswith("test_")]
    passed = 0
    for t in tests:
        t()
        passed += 1
        print(f"  PASS {t.__name__}")
    print(f"{passed}/{len(tests)} tests passed")


if __name__ == "__main__":
    _run()
