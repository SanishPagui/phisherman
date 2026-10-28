"""
Write SMS normalisation parity fixtures. The Kotlin NormalizerParityTest asserts its port
reproduces these exactly (PS4 Section 8.3 "Add a parity test with ten messages"). Tab-
separated so commas in messages are safe: raw<TAB>normalized.
"""

import csv
import os

from normalize import normalize

HERE = os.path.dirname(__file__)
TEST_RES = os.path.join(HERE, "..", "android", "app", "src", "test", "resources")

MESSAGES = [
    "Your account will be blocked today. Update KYC at http://sbi-kyc.xyz/login",
    "Your OTP is 123456. Do not share with anyone.",
    "Aapka khata band ho jayega, turant KYC update karein www.verify-now.top",
    "Congratulations! You won Rs 50000 reward. Claim now at bit.ly/abc123",
    "Debit alert: Rs 500 spent at a shop using card ending 1234",
    "Share the OTP you received to confirm delivery",
    "Big Sale this weekend! Flat 40% off on all items.",
    "Dear customer your a/c KYC expired, verify at https://kyc-update.icu/now",
    "Hi, are we still meeting for lunch today?",
    "आपका खाता बंद हो जाएगा, तुरंत KYC अपडेट करें",
]


def main():
    os.makedirs(TEST_RES, exist_ok=True)
    out = os.path.join(TEST_RES, "sms_parity_expected.tsv")
    with open(out, "w", newline="", encoding="utf-8") as fh:
        w = csv.writer(fh, delimiter="\t")
        for m in MESSAGES:
            w.writerow([m, normalize(m)])
    print(f"Wrote {out} ({len(MESSAGES)} messages)")
    for m in MESSAGES[:4]:
        print(f"  {m[:40]!r:44s} -> {normalize(m)!r}")


if __name__ == "__main__":
    main()
