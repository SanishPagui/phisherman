"""Email phishing on the phone's message engine (PS4: phishing arrives by email, SMS or voice).

The app reads emails from Gmail / Outlook / Samsung Email notifications and the open mail screen,
and checks sender + subject + preview with the same engine as SMS and chats. ml/email_test.tsv is
hand-written (30 phishing, 30 genuine, Indian context, made-up .example links): no email was used
for training, so this is an unseen set, but it is small and synthetic. Writes ml/results_email.json.
    python email_eval.py
"""
import csv
import json
import time
from pathlib import Path

from link_engine import LinkEngine
from sms_engine import SmsEngine

ML = Path(__file__).resolve().parent


def load():
    with open(ML / "email_test.tsv", encoding="utf-8") as f:
        return list(csv.DictReader(f, delimiter="\t", quoting=csv.QUOTE_NONE))


def email_text(row):
    """What the notification shows: subject, then the preview."""
    return f"{row['subject']}. {row['body']}"


def counts(rows, flagged):
    c = {"tp": 0, "fp": 0, "fn": 0, "tn": 0}
    for r, f in zip(rows, flagged):
        scam = r["label"] == "phishing"
        c["tp" if scam and f else "fn" if scam else "fp" if f else "tn"] += 1
    return c


def main():
    rows = load()
    eng = SmsEngine(LinkEngine())
    decided = [eng.decide(r["sender"], email_text(r)) for r in rows]
    levels = [d[0] for d in decided]
    res = {
        "generated": time.strftime("%Y-%m-%d"),
        "dataset": "ml/email_test.tsv: 30 phishing + 30 genuine emails, hand-written, synthetic",
        "engine": {
            "alert": counts(rows, [lv in ("SUSPICIOUS", "DANGEROUS") for lv in levels]),
            "red": counts(rows, [lv == "DANGEROUS" for lv in levels]),
        },
        "rows": [{"label": r["label"], "sender": r["sender"], "subject": r["subject"], "level": lv, "hits": d[1]}
                 for r, lv, d in zip(rows, levels, decided)],
    }
    json.dump(res, open(ML / "results_email.json", "w", encoding="utf-8"), indent=2, ensure_ascii=False)
    for k, c in res["engine"].items():
        p = c["tp"] / max(c["tp"] + c["fp"], 1); r = c["tp"] / max(c["tp"] + c["fn"], 1)
        print(f"email {k:5s} {c}  precision {p:.3f}  recall {r:.3f}")
    for row in res["rows"]:
        wrong = (row["label"] == "phishing") != (row["level"] != "SAFE")
        if wrong:
            print(f"  MISS  {row['label']:8s} {row['level']:10s} {row['sender']}: {row['subject']}  {row['hits']}")


if __name__ == "__main__":
    main()
