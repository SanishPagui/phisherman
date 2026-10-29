"""Copy the headline metrics from results_link.json / results_sms.json into the app's
assets/results.json, which the Results tab reads. Run after train_link.py / train_sms.py.
Voice metrics are not exported: the app scores voice_scripts.tsv live on the phone."""
import json
from pathlib import Path

HERE = Path(__file__).parent
OUT = HERE.parent / "android" / "app" / "src" / "main" / "assets" / "results.json"

KEEP = ("precision", "recall", "fpr", "f1", "n", "n_phishing", "tp", "fp", "fn", "tn")


def pick(m):
    return {k: m[k] for k in KEEP if k in m}


def main():
    link = json.loads((HERE / "results_link.json").read_text(encoding="utf-8"))
    sms = json.loads((HERE / "results_sms.json").read_text(encoding="utf-8"))
    hard = link.get("extras", {}).get("hard_negatives", {})
    out = {
        "generated": max(link["generated"], sms["generated"]),
        "link": {
            "model": link["model"],
            "dataset": link["datasets"]["train_test"],
            "unseen_dataset": link["datasets"]["unseen"],
            "test": pick(link["test"]["suspicious"]),
            "test_dangerous": pick(link["test"]["dangerous"]),
            "unseen": pick(link["unseen"]["suspicious"]),
            "hard_negatives": {"n": hard.get("n", 0), "fpr_engine": hard.get("fpr_engine", 0.0)},
        },
        "sms": {
            "dataset": sms["datasets"]["uci"] + " + " + sms["datasets"]["india_synthetic"],
            "test": pick(sms["engine"]["all"]),
            "uci": pick(sms["engine"]["uci"]),
            "india": pick(sms["engine"]["india_synthetic"]),
            "promotional": sms.get("engine_promotional_fpr", {}),
        },
    }
    OUT.write_text(json.dumps(out, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("wrote", OUT)


if __name__ == "__main__":
    main()
