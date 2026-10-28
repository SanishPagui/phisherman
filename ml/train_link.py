"""
train_link.py — train the link model, choose thresholds, export device assets.

Outputs (into android/app/src/main/assets/ unless --assets overrides):
  link_model.onnx     RandomForest over the 18-feature vector (zipmap off)
  feature_order.json  ordered feature names + version (UrlFeatures asserts on load)
  thresholds.json     suspicious/dangerous bands per channel (RiskEngine reads this)
And a local results_link.json with test + unseen metrics.

Split is BY REGISTRABLE DOMAIN (not by row) so no host appears in both train and
test — the leakage guard from PS4 Section 6.3. No pandas (blocked here); stdlib csv.
"""

import argparse
import csv
import json
import os

import numpy as np
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import (confusion_matrix, f1_score, precision_recall_curve,
                             precision_score, recall_score)
from skl2onnx import convert_sklearn
from skl2onnx.common.data_types import FloatTensorType

from features import FEATURE_ORDER, FEATURE_VERSION, features, split_host

HERE = os.path.dirname(__file__)


def load_csv(path):
    with open(path, encoding="utf-8") as fh:
        return list(csv.DictReader(fh))


def reg_key(url, multi):
    """Registrable-domain key for grouping (keeps a host out of both splits)."""
    u = url.strip()
    if "://" not in u:
        u = "http://" + u
    host = u.split("://", 1)[1].split("/", 1)[0].split("@")[-1].split(":")[0].lower()
    if host and host.replace(".", "").isdigit():
        return host  # IP: group by the IP itself
    reg, _ = split_host(host, multi)
    return reg or host


def report(clf, X, y, thr):
    p = clf.predict_proba(X)[:, 1]
    yhat = (p >= thr).astype(int)
    tn, fp, fn, tp = confusion_matrix(y, yhat, labels=[0, 1]).ravel()
    denom_fpr = fp + tn
    return {
        "precision": round(float(precision_score(y, yhat, zero_division=0)), 4),
        "recall": round(float(recall_score(y, yhat, zero_division=0)), 4),
        "f1": round(float(f1_score(y, yhat, zero_division=0)), 4),
        "fpr": round(float(fp / denom_fpr) if denom_fpr else 0.0, 4),
        "n": int(len(y)), "tp": int(tp), "fp": int(fp), "fn": int(fn), "tn": int(tn),
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--assets", default=os.path.join(HERE, "..", "android", "app", "src", "main", "assets"))
    ap.add_argument("--train", default=os.path.join(HERE, "data", "train_source.csv"))
    ap.add_argument("--unseen", default=os.path.join(HERE, "data", "unseen_source.csv"))
    ap.add_argument("--target-precision", type=float, default=0.95)
    args = ap.parse_args()

    with open(os.path.join(args.assets, "brands.json"), encoding="utf-8") as fh:
        brands = json.load(fh)

    rng = np.random.RandomState(42)
    train_rows = load_csv(args.train)
    unseen_rows = load_csv(args.unseen)

    # Domain-grouped 80/20 split.
    keys = np.array([reg_key(r["url"], set(brands["multiSuffixes"])) for r in train_rows])
    uniq = np.unique(keys)
    rng.shuffle(uniq)
    test_keys = set(uniq[: max(1, int(len(uniq) * 0.2))])
    is_test = np.array([k in test_keys for k in keys])

    print(f"Featurising {len(train_rows)} train + {len(unseen_rows)} unseen URLs ...")
    Xall = np.array([features(r["url"], brands) for r in train_rows], dtype=np.float32)
    yall = np.array([int(r["label"]) for r in train_rows], dtype=np.int64)
    Xun = np.array([features(r["url"], brands) for r in unseen_rows], dtype=np.float32)
    yun = np.array([int(r["label"]) for r in unseen_rows], dtype=np.int64)

    Xtr, ytr = Xall[~is_test], yall[~is_test]
    Xte, yte = Xall[is_test], yall[is_test]
    print(f"train={len(ytr)}  test={len(yte)}  unseen={len(yun)}  "
          f"({len(uniq)} domains, {len(test_keys)} held out)")

    clf = RandomForestClassifier(n_estimators=200, max_depth=14, class_weight="balanced",
                                 n_jobs=-1, random_state=42).fit(Xtr, ytr)

    # Choose DANGEROUS threshold: lowest that keeps precision >= target on the test split.
    # Floor at 0.5 so a cleanly-separating model does not collapse the band to ~0 (which
    # would flag almost everything). Keep the SUSPICIOUS band strictly below DANGEROUS.
    pr, rc, th = precision_recall_curve(yte, clf.predict_proba(Xte)[:, 1])
    ok = [t for p_, t in zip(pr[:-1], th) if p_ >= args.target_precision]
    thr_danger_raw = float(min(ok)) if ok else 0.5
    thr_danger = round(max(0.5, thr_danger_raw), 4)
    thr_susp = round(thr_danger * 0.6, 4)  # amber band below the red band

    results = {
        "feature_version": FEATURE_VERSION,
        "target_precision": args.target_precision,
        "threshold_suspicious": thr_susp,
        "threshold_dangerous": thr_danger,
        "test": report(clf, Xte, yte, thr_danger),
        "unseen": report(clf, Xun, yun, thr_danger),
    }
    print(json.dumps(results, indent=2))

    # ---- Export device assets ----
    with open(os.path.join(args.assets, "feature_order.json"), "w", encoding="utf-8") as fh:
        json.dump({"version": FEATURE_VERSION, "order": FEATURE_ORDER}, fh, indent=2)

    thr_path = os.path.join(args.assets, "thresholds.json")
    existing = json.load(open(thr_path, encoding="utf-8")) if os.path.exists(thr_path) else {}
    thresholds = {
        "version": "thr-2",
        "link": {"suspicious": thr_susp, "dangerous": thr_danger},
        "sms": existing.get("sms", {"suspicious": 0.45, "dangerous": 0.70}),  # set by train_sms.py
        "rules": {
            "deepNestDepth": 3.0, "typosquatLev": 0.25, "highEntropy": 3.6,
            "ipFloor": 80, "atFloor": 65, "deepNestFloor": 60, "brandInSubFloor": 70,
            "typosquatFloor": 78, "shortenerFloor": 45,
        },
    }
    with open(os.path.join(args.assets, "thresholds.json"), "w", encoding="utf-8") as fh:
        json.dump(thresholds, fh, indent=2)

    onx = convert_sklearn(
        clf,
        initial_types=[("input", FloatTensorType([None, Xtr.shape[1]]))],
        options={id(clf): {"zipmap": False}},
        target_opset=15,
    )
    onnx_path = os.path.join(args.assets, "link_model.onnx")
    with open(onnx_path, "wb") as fh:
        fh.write(onx.SerializeToString())

    with open(os.path.join(HERE, "results_link.json"), "w", encoding="utf-8") as fh:
        json.dump(results, fh, indent=2)

    # ---- Self-check: ONNX reproduces sklearn probabilities within 0.01 (accept gate P1) ----
    import onnxruntime as ort
    sess = ort.InferenceSession(onnx_path, providers=["CPUExecutionProvider"])
    out_names = [o.name for o in sess.get_outputs()]
    probe = Xte[:50]
    sk = clf.predict_proba(probe)[:, 1]
    onnx_out = sess.run(None, {"input": probe})
    probs = onnx_out[out_names.index("probabilities")] if "probabilities" in out_names else onnx_out[-1]
    on = np.array(probs)[:, 1]
    max_diff = float(np.max(np.abs(sk - on)))
    print(f"ONNX output names: {out_names}")
    print(f"ONNX vs sklearn max prob diff on 50 samples: {max_diff:.5f} "
          f"({'OK' if max_diff < 0.01 else 'FAIL'})")
    print(f"Wrote: link_model.onnx, feature_order.json, thresholds.json, results_link.json")


if __name__ == "__main__":
    main()
