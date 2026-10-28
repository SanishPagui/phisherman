# Phisherman — On-Device Phishing Detection & Prevention

> Hacksploit 2026 · Problem Statement 4 (“Kavach”). A native Android app that scores
> **links, SMS and scam-call speech** with one shared on-device risk engine, shows a
> **warning before a dangerous link opens**, and helps the user **report and recover**
> afterwards. Everything that matters runs on the phone, so the demo works offline.

Built in phases following the PS4 guide (`PS4.pdf`). Working name was *Kavach*; shipped as
**Phisherman** (package `com.phisherman.kavach`).

---

## What maps to the four deliverables

| Deliverable | Where it lives |
|---|---|
| 1. Analyse links, SMS & voice, detect phishing | `engine/`, `link/`, `sms/`, `voice/` |
| 2. Warning **before** a suspicious link redirects | `link/LinkRouterActivity.kt` → `ui/WarningActivity.kt` |
| 3. PoC to prevent fraud (friction + reporting) | `ui/ReportActivity.kt` (portal + 1930 + evidence pack) |
| 4. Metrics: precision, recall, fraud-detection rate | `ml/` pipeline → `assets/results.json` → `ui/.../MetricsScreen.kt` |

## Architecture (one engine, three channels, three outputs)

```
Link tapped  ─▶ UrlAnalyser  (18 features + ONNX RandomForest) ─┐
SMS / paste  ─▶ SmsAnalyser  (TF-IDF+LogReg JSON + rules)       ├─▶ RiskEngine ─▶ Verdict ─▶ Warning screen
Call audio   ─▶ VoiceAnalyser(phrase categories, rolling score) ─┘   (score/level/reasons)   Notification
                                                                                             Report pack + Room log
```

Every analyser returns the same `Verdict` (`engine/Models.kt`), so the warning screen, the
decision log and the metrics code are written once.

---

## Repository layout

```
phisherman/
├── ml/                     # Phase 1: Python pipeline (trains models, exports assets)
│   ├── features.py         #   SOURCE OF TRUTH for the 18 URL features
│   ├── normalize.py        #   SOURCE OF TRUTH for SMS text normalisation
│   ├── gen_data.py         #   reproducible SYNTHETIC datasets (replace with real data)
│   ├── train_link.py       #   RandomForest → link_model.onnx + thresholds + feature_order
│   ├── train_sms.py        #   TF-IDF + LogReg → sms_model.json
│   ├── eval_report.py      #   → results.json (read by the app + slides)
│   ├── make_parity.py      #   parity fixtures for the Kotlin test
│   ├── test_features.py    #   15 feature unit tests
│   └── verify_sms_parity.py#   cross-checks the Kotlin SMS scorer vs scikit-learn
├── android/                # Phase 2–6: native Android app (Kotlin + Jetpack Compose)
│   └── app/src/main/
│       ├── assets/         #   generated models + hand-written brands.json / voice_rules.json
│       └── java/com/phisherman/kavach/
│           ├── engine/     #   Verdict, Level, Reason, RiskEngine, Engine facade, Thresholds
│           ├── link/       #   UrlFeatures (port of features.py), UrlAnalyser(ONNX), Router, Forwarder
│           ├── sms/        #   Normalizer (port), SmsModel (TF-IDF), SmsAnalyser, Receiver
│           ├── voice/      #   VoiceAnalyser, CallGuardService (foreground mic service)
│           ├── notify/     #   notification channels + builders
│           ├── data/       #   Room decision log + Prefs
│           └── ui/         #   Compose: Home, Warning, Report, Metrics, History, Settings
└── docs/                   # demo script, scam-call script, test links, pitch outline
```

---

## Build & run

### Phase 1 — the ML pipeline (runs on a laptop, no Android needed)

```bash
cd ml
python -m pip install -r requirements.txt     # numpy, scikit-learn, skl2onnx, onnxruntime
python gen_data.py          # writes synthetic data/*.csv
python train_link.py        # → assets/link_model.onnx, feature_order.json, thresholds.json
python train_sms.py         # → assets/sms_model.json
python eval_report.py       # → assets/results.json
python make_parity.py       # → URL feature parity fixtures for the Kotlin test
python make_sms_parity.py   # → SMS normalisation parity fixtures for the Kotlin test
python test_features.py     # 15/15 feature tests
python verify_sms_parity.py # SMS scorer parity vs scikit-learn (expect max diff 0.00000)
```

> **Honesty note.** `gen_data.py` produces **synthetic** data so the pipeline runs offline
> end-to-end; every row is marked `synthetic=1`. The numbers in `results.json` are therefore
> placeholders. Drop the real, licensed datasets (URL-Phish / Kaggle / Mendeley smishing)
> into `ml/data/` with the same CSV schema and re-run before quoting any metric as a result
> (PS4 §6.3, §11.4).

### Phase 2–6 — the Android app

1. Open the **`android/`** folder in Android Studio (Giraffe or newer). It will create the
   Gradle wrapper and sync. (Or run `gradle wrapper` once if you have Gradle installed.)
2. The `assets/` already contain the models produced in Phase 1.
3. Run on a **real phone** (not an emulator — permission and link-handling behaviour differ).
4. Open the app once, then set it as the default browser: **Settings → Apps → Default apps →
   Browser app → Phisherman**, or tap **Set** on the home screen.
5. Run the unit tests: `./gradlew test` (feature parity + engine rules).

---

## Tests & verification done

| Check | Result |
|---|---|
| Python feature unit tests (`test_features.py`) | 15/15 pass |
| ONNX model vs scikit-learn probabilities | max diff **0.00000** (< 0.01 gate) |
| SMS TF-IDF reconstruction vs scikit-learn | max diff **0.00000** (< 0.01 gate) |
| Link cross-source generalisation | test precision 1.00 → unseen 0.76 (honest drop) |
| Kotlin feature parity (`UrlFeaturesParityTest`) | features within 1e-4 of Python (run in Android Studio) |
| Engine rule escalation (`EngineRulesTest`) | IP / typosquat / deep-nesting → DANGEROUS |

---

## Privacy & responsible claims

- **On-device.** Core checks need no network. The decision log stores only verdicts,
  reasons and a subject (host/sender) — never message bodies or audio.
- Uses the **dial** intent for 1930 (no call permission) and the share-to-app + paste-box
  fallbacks when it isn’t the default browser or SMS permission is denied.
- Voice is a **prototype** that scores *what is said* on a played script — it does **not**
  claim to detect cloned/deepfake voices or capture other apps’ live call audio.
- See `docs/` for the demo script, the scam-call script, test links, and the pitch outline.
