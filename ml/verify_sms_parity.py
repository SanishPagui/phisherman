"""
Verify the Kotlin SmsModel reconstruction matches scikit-learn.

Reimplements SmsModel.kt's scoring in Python (word + char_wb n-grams, sublinear tf-idf,
per-block L2 norm, linear model) from sms_model.json, and compares it to a freshly fitted
scikit-learn pipeline's predict_proba on held-out messages. If this agrees, the Kotlin port
is correct too (same algorithm). Not a unit test — a cross-check run during development.
"""

import csv
import json
import math
import os
import re

import numpy as np
from scipy.sparse import hstack
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression

from normalize import normalize

HERE = os.path.dirname(__file__)
ASSETS = os.path.join(HERE, "..", "android", "app", "src", "main", "assets")

WORD_TOKEN = re.compile(r"\b\w\w+\b", re.UNICODE)
WS = re.compile(r"\s+")


def word_ngrams(doc):
    tokens = WORD_TOKEN.findall(doc)
    out = list(tokens)
    for i in range(len(tokens) - 1):
        out.append(tokens[i] + " " + tokens[i + 1])
    return out


def char_wb_ngrams(doc):
    doc = WS.sub(" ", doc).strip()
    out = []
    for word in doc.split(" "):
        if not word:
            continue
        w = " " + word + " "
        wlen = len(w)
        for n in range(3, 6):
            offset = 0
            out.append(w[offset:offset + n])
            while offset + n < wlen:
                offset += 1
                out.append(w[offset:offset + n])
            if offset == 0:
                break
    return out


def weighted_block(ngrams, part):
    vocab, idf = part["vocab"], part["idf"]
    counts = {}
    for g in ngrams:
        idx = vocab.get(g)
        if idx is not None:
            counts[idx] = counts.get(idx, 0) + 1
    weights = {}
    ssq = 0.0
    for idx, c in counts.items():
        tf = 1.0 + math.log(c)
        w = tf * idf[idx]
        weights[idx] = w
        ssq += w * w
    norm = math.sqrt(ssq)
    if norm > 0:
        for k in weights:
            weights[k] /= norm
    return weights


def kotlin_prob(model, raw_text):
    doc = normalize(raw_text).lower()
    wv = weighted_block(word_ngrams(doc), model["word"])
    cv = weighted_block(char_wb_ngrams(doc), model["char"])
    z = model["intercept"]
    coef = model["coef"]
    for idx, v in wv.items():
        z += coef[idx] * v
    for idx, v in cv.items():
        z += coef[model["wordDim"] + idx] * v
    return 1.0 / (1.0 + math.exp(-z))


def main():
    model = json.load(open(os.path.join(ASSETS, "sms_model.json"), encoding="utf-8"))

    # Rebuild the same sklearn pipeline for ground truth.
    rows = list(csv.DictReader(open(os.path.join(HERE, "data", "sms_train.csv"), encoding="utf-8")))
    texts = [normalize(r["text"]) for r in rows]
    y = np.array([int(r["label"]) for r in rows])
    rng = np.random.RandomState(42)
    idx = rng.permutation(len(texts))
    cut = int(len(texts) * 0.8)
    tr, te = idx[:cut], idx[cut:]
    wv = TfidfVectorizer(analyzer="word", ngram_range=(1, 2), min_df=2, sublinear_tf=True)
    cv = TfidfVectorizer(analyzer="char_wb", ngram_range=(3, 5), min_df=3, sublinear_tf=True)
    Xtr = hstack([wv.fit_transform([texts[i] for i in tr]),
                  cv.fit_transform([texts[i] for i in tr])]).tocsr()
    clf = LogisticRegression(max_iter=2000, class_weight="balanced", C=3.0).fit(Xtr, y[tr])

    max_diff = 0.0
    print("  sklearn   kotlin    diff   message")
    for i in te[:15]:
        raw = rows[i]["text"]
        Xi = hstack([wv.transform([texts[i]]), cv.transform([texts[i]])]).tocsr()
        sk = float(clf.predict_proba(Xi)[0, 1])
        ko = kotlin_prob(model, raw)
        d = abs(sk - ko)
        max_diff = max(max_diff, d)
        print(f"  {sk:.4f}    {ko:.4f}   {d:.4f}  {raw[:52]}")
    print(f"\nMax |sklearn - kotlin_reconstruction| = {max_diff:.5f} "
          f"({'OK (<0.01)' if max_diff < 0.01 else 'FAIL'})")


if __name__ == "__main__":
    main()
