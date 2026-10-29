"""Labelled sample for the "Run on this phone" benchmark in the Results tab.

Every row is scored here by the Python engines (the reference) and again on the phone by the
Kotlin engines; the app shows both, so the phone's own detection is measured, not only claimed.
Only sets that are in the repo and were never used for training:
  links    ml/parity_urls.txt (first 20: 10 safe, 10 phishing), ml/hard_negatives.txt (40 real
           bank / government pages), the PS4 phone test links L1-L9
  messages the held-out 30% of ml/sms_india_synthetic.tsv (ml/sms_parity_expected.tsv) and the
           message / chat rows of ml/llm_scenarios.tsv (SCAM / SAFE only)
  email    ml/email_test.tsv
Calls are benchmarked on the phone from assets/voice_scripts.tsv (scored by the Kotlin call guard).
Writes android/app/src/main/assets/bench.tsv:  channel  label(1 = scam)  sender  text  py_level
    python bench_export.py
"""
import csv
import json
from pathlib import Path

from email_eval import email_text, load as load_emails
from link_engine import LinkEngine
from sms_engine import SmsEngine

ML = Path(__file__).resolve().parent
OUT = ML.parent / "android/app/src/main/assets/bench.tsv"


def url_lines(path):
    return [l.strip() for l in open(path, encoding="utf-8") if l.strip() and not l.startswith("#")]


def links():
    rows = []
    parity = url_lines(ML / "parity_urls.txt")
    rows += [(u, 0) for u in parity[:10]] + [(u, 1) for u in parity[10:20]]  # the "tricky" 10 have no label
    rows += [(u, 0) for u in url_lines(ML / "hard_negatives.txt")]
    for t in json.load(open(ML / "results_link.json"))["extras"]["test_links"]:
        if t["url"].startswith("http"):
            rows.append((t["url"], 0 if t["expected"].startswith("SAFE") else 1))
    seen, out = set(), []
    for u, y in rows:
        if u not in seen:
            seen.add(u); out.append((u, y))
    return out


def messages():
    labels = {r["text"]: r["label"] for r in csv.DictReader(open(ML / "sms_india_synthetic.tsv", encoding="utf-8"),
                                                            delimiter="\t", quoting=csv.QUOTE_NONE)}
    out = []
    for r in csv.DictReader(open(ML / "sms_parity_expected.tsv", encoding="utf-8"), delimiter="\t", quoting=csv.QUOTE_NONE):
        if r["text"] in labels:
            out.append((r["sender"], r["text"], 1 if labels[r["text"]] == "phishing" else 0))
    for r in csv.DictReader(open(ML / "llm_scenarios.tsv", encoding="utf-8"), delimiter="\t", quoting=csv.QUOTE_NONE):
        if r["kind"] in ("msg", "chat") and r["label"] in ("SCAM", "SAFE"):
            out.append((r["sender"], r["text"], 1 if r["label"] == "SCAM" else 0))
    return out


def clean(s):
    return s.replace("\t", " ").replace("\n", " ").strip()


def main():
    le = LinkEngine()
    se = SmsEngine(le)
    rows = [("link", y, "", u, le.decide(u)[0]) for u, y in links()]
    rows += [("message", y, s, t, se.decide(s, t)[0]) for s, t, y in messages()]
    rows += [("email", 1 if e["label"] == "phishing" else 0, e["sender"], email_text(e), se.decide(e["sender"], email_text(e))[0])
             for e in load_emails()]
    with open(OUT, "w", encoding="utf-8", newline="") as f:
        f.write("channel\tlabel\tsender\ttext\tpy_level\n")
        for ch, y, s, t, lv in rows:
            f.write(f"{ch}\t{y}\t{clean(s)}\t{clean(t)}\t{lv}\n")
    for ch in ("link", "message", "email"):
        part = [r for r in rows if r[0] == ch]
        tp = sum(1 for r in part if r[1] == 1 and r[4] != "SAFE"); fp = sum(1 for r in part if r[1] == 0 and r[4] != "SAFE")
        fn = sum(1 for r in part if r[1] == 1 and r[4] == "SAFE")
        print(f"{ch:8s} n={len(part):3d}  precision {tp / max(tp + fp, 1):.3f}  recall {tp / max(tp + fn, 1):.3f}")
    print("wrote", OUT)


if __name__ == "__main__":
    main()
