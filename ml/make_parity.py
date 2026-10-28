"""
make_parity.py — write the parity fixtures the Kotlin unit test asserts against.

Produces:
  parity_urls.txt         30 URLs (10 safe, 10 phishing-like, 10 tricky)
  parity_expected.csv     url + 18 features + model probability, from the Python side

The Kotlin test (UrlFeaturesParityTest) loads parity_expected.csv from test resources
and asserts every feature within 1e-4 and the probability within 0.01 (PS4 Section 6.7).
Run this AFTER train_link.py so link_model.onnx exists.
"""

import csv
import json
import os

import numpy as np
import onnxruntime as ort

from features import FEATURE_ORDER, features

HERE = os.path.dirname(__file__)
ASSETS = os.path.join(HERE, "..", "android", "app", "src", "main", "assets")

URLS = [
    # 10 safe
    "https://www.wikipedia.org/",
    "https://www.google.com/search?q=weather",
    "https://github.com/explore",
    "https://www.hdfcbank.com/",
    "https://amazon.in/orders",
    "https://irctc.co.in/nget/train-search",
    "https://en.wikipedia.org/wiki/Phishing",
    "https://login.microsoftonline.com/common/oauth2/authorize?client_id=abc",
    "https://www.netflix.com/browse",
    "https://paytm.com/offers",
    # 10 phishing-like
    "https://examplebank-kyc-update.example/login",
    "http://203.0.113.10/verify/account",
    "https://examplebank.com@login-check.example/",
    "https://login.secure.examplebank.com.attacker.example/",
    "https://bit.ly/3xampLe",
    "https://paytrn.example/",
    "http://sbi-secure-login.xyz/netbanking/account.php?id=9921",
    "https://hdfc0bank.top/kyc/verify",
    "http://g00gle-rewards.click/claim?ref=551",
    "https://ak39fjq2mxzl.buzz/login/login",
    # 10 tricky
    "https://accounts.google.com/signin/v2/identifier?hl=en&flowName=GlifWebSignIn",
    "https://www.amazon.in/gp/css/order-history?ref_=nav_orders&longparam=1&x=2&y=3",
    "bank.example/login",
    "https://xn--80ak6aa92e.example/",
    "https://sub.sub.sub.wikipedia.org/wiki/Test",
    "https://incometax.gov.in/iec/foportal/",
    "https://flipkart.com/account/orders?sid=abc&tid=def&uid=123",
    "tel:1930",
    "https://t.co/abcdef",
    "https://indiapost.gov.in/VAS/Pages/track.aspx",
]


def main():
    with open(os.path.join(ASSETS, "brands.json"), encoding="utf-8") as fh:
        brands = json.load(fh)
    sess = ort.InferenceSession(os.path.join(ASSETS, "link_model.onnx"),
                                providers=["CPUExecutionProvider"])
    out_names = [o.name for o in sess.get_outputs()]
    prob_idx = out_names.index("probabilities") if "probabilities" in out_names else len(out_names) - 1

    with open(os.path.join(HERE, "parity_urls.txt"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(URLS) + "\n")

    rows = [["url"] + FEATURE_ORDER + ["prob"]]
    for url in URLS:
        f = features(url, brands)
        x = np.array([f], dtype=np.float32)
        prob = float(np.array(sess.run(None, {"input": x})[prob_idx])[0][1])
        rows.append([url] + [f"{v:.6f}" for v in f] + [f"{prob:.6f}"])

    out = os.path.join(HERE, "parity_expected.csv")
    with open(out, "w", newline="", encoding="utf-8") as fh:
        csv.writer(fh).writerows(rows)

    # Also drop copies into test resources so the Kotlin parity test can read them.
    test_res = os.path.join(HERE, "..", "android", "app", "src", "test", "resources")
    os.makedirs(test_res, exist_ok=True)
    with open(os.path.join(test_res, "parity_expected.csv"), "w", newline="", encoding="utf-8") as fh:
        csv.writer(fh).writerows(rows)
    with open(os.path.join(ASSETS, "brands.json"), encoding="utf-8") as src, \
            open(os.path.join(test_res, "brands.json"), "w", encoding="utf-8") as dst:
        dst.write(src.read())

    print(f"Wrote {out} and test resources copy ({len(URLS)} URLs).")
    print("Sample (url, prob):")
    for r in rows[1:6]:
        print(f"  {r[-1]}  {r[0]}")


if __name__ == "__main__":
    main()
