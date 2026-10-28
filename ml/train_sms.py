"""
train_sms.py — TF-IDF + logistic regression SMS classifier, exported as JSON.

Exports android/.../assets/sms_model.json: word + char vocabularies, IDF weights,
coefficients, intercept, n-gram ranges and a version string. The Kotlin SmsAnalyser
rebuilds the exact same TF-IDF vectors (sublinear tf, L2 norm) and applies the linear
model — no tokenizer porting, tiny on-device footprint. See PS4 Section 8.3.

numpy ints in the sklearn vocabulary are cast to int before dumping.
"""

import argparse
import csv
import json
import os

import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression

from normalize import NORM_VERSION, normalize

HERE = os.path.dirname(__file__)


def load_csv(path):
    with open(path, encoding="utf-8") as fh:
        rows = list(csv.DictReader(fh))
    texts = [normalize(r["text"]) for r in rows]
    labels = np.array([int(r["label"]) for r in rows], dtype=np.int64)
    return texts, labels, rows


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--assets", default=os.path.join(HERE, "..", "android", "app", "src", "main", "assets"))
    ap.add_argument("--data", default=os.path.join(HERE, "data", "sms_train.csv"))
    args = ap.parse_args()

    texts, y, rows = load_csv(args.data)
    n_syn = sum(int(r.get("synthetic", 0)) for r in rows)
    print(f"Loaded {len(texts)} messages ({n_syn} synthetic, "
          f"{int(y.sum())} phishing / {int((1 - y).sum())} non-phishing)")

    # Hold out 20% for an honest in-app number.
    rng = np.random.RandomState(42)
    idx = rng.permutation(len(texts))
    cut = int(len(texts) * 0.8)
    tr, te = idx[:cut], idx[cut:]

    word_vec = TfidfVectorizer(analyzer="word", ngram_range=(1, 2), min_df=2, sublinear_tf=True)
    char_vec = TfidfVectorizer(analyzer="char_wb", ngram_range=(3, 5), min_df=3, sublinear_tf=True)

    Xw = word_vec.fit_transform([texts[i] for i in tr])
    Xc = char_vec.fit_transform([texts[i] for i in tr])
    from scipy.sparse import hstack
    Xtr = hstack([Xw, Xc]).tocsr()
    ytr = y[tr]

    clf = LogisticRegression(max_iter=2000, class_weight="balanced", C=3.0).fit(Xtr, ytr)

    # Evaluate on held-out.
    Xte = hstack([word_vec.transform([texts[i] for i in te]),
                  char_vec.transform([texts[i] for i in te])]).tocsr()
    yte = y[te]
    p = clf.predict_proba(Xte)[:, 1]
    yhat = (p >= 0.70).astype(int)
    tp = int(((yhat == 1) & (yte == 1)).sum()); fp = int(((yhat == 1) & (yte == 0)).sum())
    fn = int(((yhat == 0) & (yte == 1)).sum()); tn = int(((yhat == 0) & (yte == 0)).sum())
    metrics = {
        "precision": round(tp / max(tp + fp, 1), 4),
        "recall": round(tp / max(tp + fn, 1), 4),
        "fpr": round(fp / max(fp + tn, 1), 4),
        "f1": round(2 * tp / max(2 * tp + fp + fn, 1), 4),
        "n": int(len(yte)), "tp": tp, "fp": fp, "fn": fn, "tn": tn,
        "n_synthetic": n_syn,
    }
    print(json.dumps(metrics, indent=2))

    # Derive SMS bands from the hold-out (lowest threshold with precision >= target, floored 0.5).
    from sklearn.metrics import precision_recall_curve
    pr, _, th = precision_recall_curve(yte, p)
    ok = [t for p_, t in zip(pr[:-1], th) if p_ >= 0.95]
    thr_d = round(max(0.5, float(min(ok)) if ok else 0.7), 4)
    thr_path = os.path.join(args.assets, "thresholds.json")
    thr = json.load(open(thr_path, encoding="utf-8"))
    thr["sms"] = {"suspicious": round(thr_d * 0.6, 4), "dangerous": thr_d}
    json.dump(thr, open(thr_path, "w", encoding="utf-8"), indent=2)
    print("SMS thresholds ->", thr["sms"])

    n_word = len(word_vec.get_feature_names_out())
    export = {
        "version": "sms-1",
        "normVersion": NORM_VERSION,
        "word": {
            "vocab": {k: int(v) for k, v in word_vec.vocabulary_.items()},
            "idf": [round(float(x), 6) for x in word_vec.idf_.tolist()],
            "ngram": [1, 2],
        },
        "char": {
            "vocab": {k: int(v) for k, v in char_vec.vocabulary_.items()},
            "idf": [round(float(x), 6) for x in char_vec.idf_.tolist()],
            "ngram": [3, 5],
        },
        # Coefficients are [word-block | char-block]; wordDim tells Kotlin where to split.
        "wordDim": int(n_word),
        "coef": [round(float(x), 6) for x in clf.coef_[0].tolist()],
        "intercept": round(float(clf.intercept_[0]), 6),
        "sublinearTf": True,
    }
    out = os.path.join(args.assets, "sms_model.json")
    with open(out, "w", encoding="utf-8") as fh:
        json.dump(export, fh)
    size_kb = os.path.getsize(out) / 1024
    print(f"Wrote sms_model.json ({size_kb:.0f} KB, {n_word} word + "
          f"{len(char_vec.get_feature_names_out())} char features)")

    with open(os.path.join(HERE, "results_sms.json"), "w", encoding="utf-8") as fh:
        json.dump(metrics, fh, indent=2)


if __name__ == "__main__":
    main()
