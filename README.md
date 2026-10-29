# Phisherman

On-device phishing detection and prevention for Android — Hacksploit 2026, Problem Statement 4.

Phisherman scores links, SMS and call transcripts with one shared risk engine, warns **before** a suspicious link opens, and gives the user a one-tap path to report (cybercrime.gov.in, 1930).

## Layout

```
ml/        Python: features, training, evaluation, exports to android assets
android/   Kotlin + Jetpack Compose app
docs/      slides, demo script, backup video
```

## Datasets

Every dataset used, with exact name, version, source and date. Raw files live in `ml/data/raw/` and are not committed.

### Public datasets

| Dataset | Size / classes | Used for | Version / date | Licence | Source | Status |
|---|---|---|---|---|---|---|
| URL-Phish (`Dataset.csv`) | 116,600 URLs: 100,000 benign / 16,600 phishing. Phishing from PhishTank (Nov 2024 – Sep 2025), benign from a Zenodo research-organisation list | Link model: training and held-out test (split by registrable domain) | v2, downloaded 2026-10-28 | CC BY 4.0 (Mendeley Data default; verify on page) | https://data.mendeley.com/datasets/65z9twcx3r/2 | Downloaded |
| pirocheto/phishing-url (Hannousse & Yahiouche, 2021) | 11,430 URLs: 5,715 legitimate / 5,715 phishing; 62% of benign URLs have real paths | Link model: training and held-out test, added to offset URL-Phish path leakage | HF `data/train.parquet` + `data/test.parquet`, downloaded 2026-10-28 | CC BY 4.0 | https://huggingface.co/datasets/pirocheto/phishing-url | Downloaded |
| PhiUSIIL Phishing URL Dataset | 235,795 URLs: 100,945 phishing / 134,850 legitimate. **`label` is inverted: 1 = legitimate, 0 = phishing; flip it on load** | Link model: training (weight 0.2, because every legitimate URL is a bare homepage and at full weight the model learns "has a path = phishing") and held-out test (split by domain) | UCI id 967, downloaded 2026-10-28 | CC BY 4.0 | https://archive.ics.uci.edu/dataset/967 | Downloaded |
| Phishing.Database: `phishing-domains-ACTIVE.txt` | Phishing domains only, no benign | On-device blocklist layer: 392,151 domains in a 0.70 MB Bloom filter (`blocklist.bloom`). Allowlisted, official, shortener and shared-platform domains are removed | master, downloaded 2026-10-28 | MIT | https://github.com/Phishing-Database/Phishing.Database | Downloaded, built |
| Phishing.Database: `phishing-links-ACTIVE.txt` | ~66 MB of phishing URLs, no benign | Fresh-phishing recall check (model-only numbers reported separately from blocklist hits). Processed as text only; never opened | master | MIT | same as above | Planned |
| UCI SMS Spam Collection | 5,574 English SMS: ham / spam | SMS model: training and test (English volume); its normal messages are also call-model negatives | v1, downloaded 2026-10-28 | CC BY 4.0 | https://archive.ics.uci.edu/dataset/228 | Downloaded |

| Indian Cyber Scam PhoneCall Hinglish (`ysangam`) | 10,000 rows but **only 743 unique texts** (633 scam / 110 normal; the rest are template copies). Hinglish call snippets: digital arrest, police blackmail, KYC, Aadhaar, lottery, relative. Its `scam_category` column is unreliable, so reasons are re-derived from the text | On-device LLM and call model: training / test (call model splits by template sentence) | downloaded 2026-10-28 | Apache-2.0 | https://huggingface.co/datasets/ysangam/Indian_Cyber_Scam_PhoneCall_Hinglish_Dataset | Downloaded |
| BothBosu/scam-dialogue | 1,598 synthetic English phone conversations (800 scam / 798 normal; SSN, refund, tech support, reward, delivery, insurance, telemarketing, wrong number). Each is cut to a ~600-character window from a third of the way in | On-device LLM and call model (caller turns): training / test. Its call type decides its label (every delivery call is normal, every refund call is a scam), so scores on it are optimistic | downloaded 2026-10-28 | Apache-2.0 | https://huggingface.co/datasets/BothBosu/scam-dialogue | Downloaded |
| DailyDialog (Li et al., 2017; `roskoN/dailydialog` parquet copy) | 13,118 everyday English conversations, no scams | Call model: what a normal call sounds like (negatives), training / test split by dialogue | downloaded 2026-10-29 | **CC BY-NC-SA 4.0 (non-commercial)** | https://huggingface.co/datasets/roskoN/dailydialog | Downloaded |
| CloveAI/india-spam-sms | 20,010 Indian SMS (14,059 normal / 5,951 spam) but only ~307 templates (bank debits, UPI, bills, recharges, KYC / Aadhaar / prize scams with names and numbers swapped) | SMS model: at most 5 rows per template; train / test split by template | downloaded 2026-10-29 | MIT | https://huggingface.co/datasets/CloveAI/india-spam-sms | Downloaded |
| bolewara/hinglish-scam-text-dataset | ~3,800 texts, mostly copies of UCI; only the 196 rows not in UCI are used | On-device LLM: extra SMS / Hinglish | downloaded 2026-10-28 | CC BY 4.0 | https://huggingface.co/bolewara/hinglish-scam-text-dataset | Downloaded |
| Gemma 3 270M instruction-tuned (`unsloth/gemma-3-270m-it`, ungated mirror of `google/gemma-3-270m-it`) | Base model for the on-device scam LLM | Fine-tuned with LoRA | downloaded 2026-10-28 | Gemma Terms of Use | https://huggingface.co/unsloth/gemma-3-270m-it | Downloaded |

Not used, for the record: Spam SMS in Hindi (IEEE DataPort; licence unclear, needs an account), COVA / COVA-X synthetic multi-turn smishing (download not located), ScamBench (broad aggregate including prompt-injection data; needs filtering).

### Hand-made datasets (ours)

| Dataset | Contents | Used for | Status |
|---|---|---|---|
| `ml/sms_india_synthetic.tsv` | 148 hand-written Indian messages, **all synthetic**: 60 phishing (fake KYC, bank block, reward, courier fee, electricity cut-off, loan / job, digital arrest, OTP ask, APK / AnyDesk), 43 normal (OTP delivery, debit / credit alerts, deliveries, family chats), 45 promotional. English, Hinglish and Devanagari; phishing links use non-resolving `.example` domains | SMS model training (70%) and test (30%), stratified by class | Done |
| `ml/hard_negatives.txt` | 40 benign URLs on official sites: bank and government login portals, OAuth sign-in pages, long queries | Link FPR on hard negatives | Done |
| `ml/parity_urls.txt` | 30 URLs (10 safe, 10 phishing-like, 10 tricky) | Python vs Kotlin feature and probability parity test; expected values in `parity_expected.csv` | Done |
| `allowlist.json` | 66 popular / official registrable domains, never applied to free-hosting platforms | Instant SAFE verdicts, fewer false alarms | Done; verify entries |
| `ml/test_links.txt` | 10 device test links L1–L10 from PS4 §13.2; phishing-like ones use non-resolving `.example` domains | Device acceptance tests | Done (not committed) |
| Voice scripts | About 12 scam-call scripts and 12 normal-call scripts, recorded in our own voices | Voice detection rate, time to amber / red, false alarms | To create (Phase 4) |
| `brands.json` | Brand tokens, official registrable domains, homoglyph map | URL features 16–17 and SMS rule R_BRAND_LINK | Done; every official domain still needs manual verification |
| `voice_rules.json` | Phrase categories and weights in English / Hindi / Hinglish (credential, remote access, payment, authority, threat, lure, secrecy) and two combination bonuses | Voice scorer | Done |
| `voice_scripts.tsv` | 18 scripted calls: 7 scam (PS4 V1 plus digital arrest, CVV reward, electricity, customs, Paytm, TRAI), 6 normal, 5 customer-care / genuine bank (G1–G3 mention OTPs without asking for them); `[PAY]` marks the payment demand | Voice tests and in-app demo scripts | Done |
| `ml/llm_scenarios.tsv` | 58 hand-written cases for the LLM: no-link scams (family impersonation, fake CEO, OLX QR), multi-message chats, call transcripts, and tricky genuine ones (bank credit alerts, office payments, family money requests). All synthetic | On-device LLM training / test | Done |

### Known leakage: URL-Phish

Phishing URLs come from PhishTank; benign URLs come from a research-organisation list, so the classes differ in shape, not just intent:

| | benign | phishing |
|---|---|---|
| median URL length | 26 | 38 |
| median path length | 1 | 6 |
| share with a real path | 23% | 56% |
| median is_https | 0 | 1 |

A model can learn "has a path / uses https" instead of phishing behaviour. Mitigations: add benign URLs with realistic paths, split by registrable domain, and report the PhiUSIIL (unseen) result alongside the in-dataset result.

## Link model (Phase 1)

### Rebuild

```
cd ml
python -m pytest -q test_features.py   # 22 feature tests
python train_link.py                   # trains, evaluates, exports link_model.onnx, feature_order.json, thresholds.json
python build_blocklist.py              # blocklist.bloom + blocklist.json
python eval_link_extras.py             # parity file, hard negatives, test links, full-engine unseen check
```

Results are written to `ml/results_link.json`. Decision order, mirrored in Kotlin (`ml/link_engine.py` is the reference): non-http(s) ignored → allowlist → blocklist → shortener (always SUSPICIOUS) → Random Forest over 19 URL features (two thresholds) → brand rules raise SAFE to SUSPICIOUS on non-official domains: `brand_lookalike` (registrable label close to a brand, e.g. `paytrn`) and `brand_in_host` (any host part equal to a brand or one edit away, e.g. `sbi-kyc.example`, added after a live test where that link passed as SAFE).

### Results (2026-10-29, positive class = phishing, FDR = recall)

Trained on URL-Phish + pirocheto + PhiUSIIL (~290k URLs after the split), split by registrable domain. Thresholds are chosen on validation URLs from URL-Phish + pirocheto, whose legitimate URLs have real paths like the links people actually receive.

| Test set | Level (threshold) | Precision | Recall (FDR) | FPR | N (phishing) |
|---|---|---|---|---|---|
| Held-out domains with real paths (URL-Phish + pirocheto) | Suspicious (0.817) | 0.871 | 0.649 | 1.54% | 24,071 (3,311) |
| Same | Dangerous (0.936) | 0.918 | 0.451 | 0.64% | 24,071 (3,311) |
| Held-out PhiUSIIL domains (homepages) | Suspicious | 0.999 | 0.453 | 0.01% | 41,320 (14,582) |
| All held-out domains, full engine (model + lists + rules), 40k sample | Suspicious | 0.959 | 0.546 | 0.88% | 40,000 |
| Hard negatives, model + rules without lists | any warning | | | 20% | 40 |
| Hard negatives, full engine | any warning | | | 0% | 40 |
| Device test links L1–L10 | | 10 / 10 pass | | | 10 |

Before PhiUSIIL was added (previous split and thresholds): 0.834 precision / 0.895 recall / 4.1% FPR on URL-Phish + pirocheto, and 0.952 / 0.394 on PhiUSIIL as an unseen set. On the same split, adding PhiUSIIL at weight 0.2 beat leaving it out on every source (precision up, PhiUSIIL recall 0.32 → 0.45, fewer hard-negative false alarms); at full weight it raised false alarms on normal links with paths to 9–43%. The new threshold rule trades recall for fewer false alarms.

What these numbers mean:
- **Recall on bare-domain phishing is low.** Most PhiUSIIL phishing URLs are bare domains (`www.photopayz.com`) with nothing suspicious in the string; no URL-only model can catch them. The blocklist adds coverage for known campaigns; content (SMS) and user friction cover the rest.
- **The model alone over-flags real login pages** (GitHub, Microsoft sign-in, Google Forms) because the training benign data has few login pages. The allowlist removes these false alarms on major sites; long-tail legitimate login pages remain a known weakness.
- Thresholds are chosen on a **validation split carved from the training data** (by domain) for precision ≥ 0.90 (suspicious) and ≥ 0.98 (dangerous); the test set is only scored. An earlier version chose them on the test set itself, which reported 90.0% precision / 2.1% FPR; on untouched data the same target gives 83.4% / 4.1%.

## Android app: link protection (Phase 2)

How it works on the phone:
1. Phisherman is set as the phone's browser app ("Turn on link protection" on the home screen). Links can also be shared to Phisherman, or pasted into the home-screen box.
2. `LinkRouterActivity` (invisible) receives the tap, scores it on device, and either forwards a safe link to the real browser (Chrome by default, changeable on the home screen) or opens `WarningActivity`.
3. The warning shows the real registrable domain in bold, a 0–100 risk meter, up to three reasons (English / Hindi), a dominant **Go back** button, **Report this link** (Phase 5), and a small **Open anyway** that needs a second confirmation.
4. Every verdict goes to a local Room log (time, level, score, host, reason ids, latency, user action). No message text or full URL is stored.

Code: `link/UrlParser.kt`, `UrlFeatures.kt` (Kotlin twin of `features.py`), `Blocklist.kt`, `LinkDecider.kt` (twin of `link_engine.py`), `LinkReasons.kt`, `LinkAnalyser.kt` (assets, ONNX, cache, fail-safe), `LinkRouterActivity.kt`, `Forwarder.kt`, `ui/WarningActivity.kt`, `data/Decision.kt`.

Tests:

```
cd android
.\gradlew testDebugUnitTest            # JVM: 19 features within 1e-4 of Python on 30 URLs, Bloom filter, rule decisions
.\gradlew connectedDebugAndroidTest    # on the phone: ONNX probability within 0.01 of Python, same level + reason for all 30
```

Results on the demo phone (S21 FE, Android 14), 2026-10-28:

| Check | Result |
|---|---|
| JVM parity (features, blocklist, rules) | 3 / 3 tests pass |
| On-device parity (ONNX + engine, 30 URLs) | pass |
| Device links via a real VIEW intent | L1–L5 and L9 warn, L6–L7 forwarded to Chrome (L8 and L10 checked in the parity tests; `tel:` never reaches Phisherman because it only registers for http/https) |
| Scoring latency (decision log) | median 2 ms, max 110 ms (first call after load) |
| Tap-to-screen time (`am start -W`) | ~150–400 ms, warning or forward |

## SMS analysis (Phase 3)

### Pipeline

1. **Normalise** (`ml/normalize.py`, Kotlin twin `sms/SmsNormalizer.kt`): lower-case, emoji removed, links extracted and replaced with `urltoken`, long numbers → `numtoken`, 4–8 digit codes → `otpcode`, `cl1ck` → `click`, Hinglish variants unified (`band`/`blocked` → `block`, `turant` → `jaldi`, `aadhar` → `aadhaar` …).
2. **Model**: TF-IDF word 1–2 grams + character 3–5 grams, logistic regression, exported as `sms_model.json` (1.0 MB, no ONNX needed).
3. **Rules** (`ml/sms_engine.py`, Kotlin `sms/SmsDecider.kt`): `R_OTP_ASK` and `R_APP_INSTALL` → DANGEROUS; `R_BRAND_LINK` → at least SUSPICIOUS; `R_URGENT_LINK` and `R_SENDER_MISMATCH` add +0.15 to the score; every link is also scored by the link engine and the message is at least as risky as its worst link; `R_ALLOW_SENDER` caps a message from a registered header (e.g. `AD-HDFCBK`) at SAFE when every link in it is safe and no OTP / app / brand rule fired.
4. **RCS and chat apps**: a live test showed that a message sent from another Android phone arrived as an **RCS chat** in Google Messages, which never triggers the SMS broadcast. `MessageNotificationListener` therefore also reads new-message notifications from Google Messages, Samsung Messages, WhatsApp, WhatsApp Business and Telegram and sends them through the same engine (the user enables it from the home screen; text is analysed on the phone and never stored). Duplicates of the same text from the SMS broadcast and the notification are dropped. A second live test showed that on Samsung One UI the listener stopped receiving once the app went to sleep, and only resumed when the app was opened. Fixes: the listener asks to be re-bound whenever it is disconnected and on app start, and the home screen asks for a battery-optimisation exemption ("Keep protection running"). With the exemption, a test message was caught while the phone was forced into Doze with the app in the background.
5. **On the phone**: `SmsReceiver` (protected by `BROADCAST_SMS`, multipart joined, dual-SIM duplicates dropped) → heads-up notification with the top reason → tap opens the warning screen with the message, sender and reasons. The paste box on the home screen runs the same pipeline. Phisherman shows a warning right after arrival; it does not block or hide the SMS.

Rebuild: `cd ml && python train_sms.py` (writes `sms_model.json`, `thresholds.json["sms"]`, `results_sms.json`, `sms_parity_expected.tsv`).

### Results (2026-10-28, positive class = phishing / scam)

Trained on UCI + CloveAI/india-spam-sms (template-split) + our Indian set. Thresholds are chosen on validation messages from UCI + our set (the template rows are too easy to set a threshold on).

| Test set | Precision | Recall (FDR) | FPR | N (phishing) |
|---|---|---|---|---|
| Full engine, all test messages | 0.982 | 0.987 | 0.32% | 2,583 (392) |
| Full engine, UCI held-out | 0.952 | 0.960 | 0.66% | 1,029 (125) |
| Full engine, Indian bank / UPI / scam messages, unseen templates | 1.000 | 1.000 | 0% | 1,506 (249) |
| Full engine, Indian synthetic held-out | 0.947 | 1.000 | 3.3% | 48 (18) |
| Model only, Indian synthetic held-out | 0.692 | 1.000 | 27% | 48 (18) |
| Promotional messages flagged (Indian held-out) | | | 0 / 14 | 14 |

- The Indian set is small (45 test messages) and synthetic; treat its numbers as indicative only.
- The rules matter: on Indian messages the model alone over-flags genuine bank and brand messages (33% FPR); the trusted-header and link rules bring this to 3.7%.
- Live false alarm, fixed: any chat message that was mostly a link (YouTube, Wikipedia, thisisnotawebsitedotcom.com) scored ~1.0, because in UCI almost every message with a link is spam and the model learned "link = scam". Links are now removed before the text model (normaliser `sms-norm-2`), each link is judged only by the link engine, and a message with fewer than 4 words next to a link takes the link's verdict. Cost: recall 0.986 → 0.965 on the full test set. Ten everyday chat messages with legitimate links were added to the Indian set.
- Live false alarm, fixed: a genuine Indian Overseas Bank message arrived as a verified RCS business chat (sender shown as a name, not a header like `VM-IOBCHN`), so the trusted-sender rule did not apply and the model alone flagged it. In Google / Samsung Messages a sender shown as a name is now treated like a registered header (saved contact or verified business); WhatsApp names are not trusted. Numbers written with a leading 0 (`099752 17785`) now count as personal mobile numbers.
- Live false alarm, fixed: a short Konkani WhatsApp from family ("To ata panji vata khai") scored 0.36, just over the amber threshold. A text under 8 words with no link and no rule hit now needs the red threshold before the word model alone may warn (`SHORT_TEXT_WORDS`); on the held-out set this raised precision 0.965 → 0.982 with recall unchanged.
- Live false alarm, fixed: LinkedIn's own "Missed Notifications: See updates from LinkedIn that you may have missed" was flagged on wording. Notifications are now split into a **person's message** (MessagingStyle chat bubble, SMS app, email) and an **app's own notice**; an app notice is never flagged on wording alone, only on hard evidence (risky link, OTP / PIN request, APK, brand name with a fake link).
- Known misses: a "Hi mom, new number, send money" impersonation scam (no link, no credential words), and a harmless Hinglish delivery note flagged amber because of the word "parcel".
- UCI spam is treated as scam; it contains many prize / premium-rate scams but is English and UK-centric.

### Tests

- `SmsParityTest` (JVM): normaliser text and extracted links identical to Python, probability within 1e-6, same level and rule hits on 52 messages (all Indian test messages plus edge cases).
- On the phone, the PS4 §13.3 messages S1–S7 were fed through the real receiver pipeline (debug-only `DebugSmsReceiver`, because a real SMS cannot be injected over adb): S1, S4, S6, S7 raised a scam notification with the right reason; S2 (OTP delivery), S3 (debit alert) and S5 (promo) stayed silent.

## Call guard on real calls (Phase 4)

Android gives third-party apps silence from the microphone during a phone call (measured on the S21 FE: MIC and VOICE_COMMUNICATION both -100 dBFS during a live call; the same code hears normally outside calls). Being the default dialer or using Shizuku does not change that: the caller's audio needs `CAPTURE_AUDIO_OUTPUT`, which only system apps hold.

What works, with the phone at the ear and no speakerphone: the system **Live Caption** feature transcribes the caller. Phisherman's accessibility service reads the caption box during a call and feeds the call guard (same phrase scoring, on-device AI every ~10 s, strong vibration plus an earpiece beep, red banner). With `WRITE_SECURE_SETTINGS` granted once over adb, Phisherman switches Live Caption on when a call connects and off when it ends.

Live test (2026-10-29, normal call at the ear): the caller asked for an OTP; captions reached Phisherman, the on-device AI answered `SCAM | credential_request` in 837 ms, and the guard went red with vibration. A bug kept the phone vibrating after the call (the alert was started and stopped through two different `Vibrator` objects, so `cancel()` did not match); fixed by using one shared `Vibrator`.

Second live test: **every call was flagged**, even a call about a picnic. Live Caption shows its own placeholder, "[Call started. Captions will appear here.] English", before anyone speaks; Phisherman passed that to the on-device AI, which had never seen such text and answered `SCAM | other_scam`. Fixes: the caption reader drops Live Caption's UI text; the AI is asked only after 8+ words of real speech; its vague `other_scam` answer is ignored on calls; and on a call the AI alone can reach at most amber; red needs the phrase rules to have heard warning signs too (`VoiceScorerTest.aiAloneNeverMakesACallRed`).

### Trained call model (2026-10-29)

The phrase rules alone caught 22% of held-out scam calls. `ml/train_calls.py` now trains a call model (same TF-IDF + logistic regression format as the SMS model, `assets/call_model.json`, 1.7 MB) on the caller's turns from BothBosu and the Hinglish call set, with DailyDialog conversations and real normal SMS as "normal speech". It is trained on every growing prefix of a call, because the phone scores the call so far after each caption line. Calls are split by call / template sentence; thresholds come from validation calls. On the phone, `CallSession` takes the higher of the rules and the model; the on-device AI can still only raise.

Held-out calls replayed line by line through the phone's `CallSession` (`VoiceDatasetEval`, alert = amber or red):

| Held-out set | Rules only (before) | Rules + model (now) |
|---|---|---|
| All: 261 scam calls, 2,829 normal calls / conversations | 57 / 261 caught, 4 false alarms | **261 / 261 caught, 4 false alarms** (the 4 come from the rules) |
| Hinglish scam calls (unseen templates) | 21 / 108 | 108 / 108, 0 / 27 false |
| English scam dialogues | 36 / 153 | 153 / 153, 0 / 157 false |
| Our 18 hand-written Indian calls (never trained on), model alone | | 6 / 7 scams, 0 / 11 false alarms (rules catch the 7th) |

Honest caveat: the public call datasets are synthetic and easy (perfect scores mean they are separable, not that real calls will be). The first version, trained without everyday conversations, scored 100% on the datasets yet flagged **every** normal hand-written call; adding DailyDialog and real SMS as normal speech fixed that. Real calls through Live Caption still need live testing.

### Links read out on a call (2026-10-29)

A scammer may not ask for an OTP at all, only dictate a website. `voice/SpokenLinks.kt` rebuilds addresses from the caption text ("s b i dash rewards dot com slash claim" → `sbi-rewards.com/claim`; spelled letters, dot / slash / dash / hyphen / "w w w", and links the captioner already wrote as text). Each one goes through the same link engine as the browser (allowlist, 392k blocklist, model, brand rules). An unsafe address makes the call amber, or red when other warning signs were heard, and the alert names the site. Only finished caption lines are checked, so a half-said "amazon dot co" is never judged. A two-word name ("sbi rewards dot com") is also tried joined (`sbi-rewards.com`), unless the address as heard is allowlisted. A new phrase category `visit_link` ("open the link", "type this website", "website pe jao") adds weight, with a bonus when heard alongside authority / threat / lure / credential / payment. Demo scripts: S7 (dictated fake SBI site, red before the payment line) and N7 (a friend mentions BookMyShow and YouTube, no alert). Tests: `SpokenLinksTest`, `VoiceScorerTest.dictatedFakeLinkIsCaught`.

### Live tests, 2026-10-29: WhatsApp calls and garbled links

- **WhatsApp calls work.** On the S21 FE, WhatsApp reports its calls to Android (call state goes OFFHOOK) and Live Caption captions the WhatsApp audio. As a second route, `CallWatcher` also treats an internet call as live when an ongoing call notification from WhatsApp / Telegram / Signal / Messenger / Instagram / Meet / Skype is showing AND the audio is in voice-call mode (`MODE_IN_COMMUNICATION`). Test call: detected, captions read, Live Caption switched off after hang-up.
- **Urgent OTP demand only reached amber.** Captions heard "give me" as "Giving us some OTP, please right now". Added "giving" / "gimme" as asking words and an `urgency` category ("right now", "immediately", "jaldi", "turant"…). Urgency has weight 0 on its own (bank-transfer chats say "immediately" too; at weight 15 it added 6 false alarms on held-out conversations) and only adds +30 together with an OTP / remote-app request, which makes that red.
- **Dictated link garbled.** Live Caption wrote "SBI **bash** reward.com" and "**dashreward**.com". "bash" now counts as "dash", a "dash" glued to the next word is split, up to 3 words before a domain are tried as its name ("book my show dot com" → bookmyshow.com), and any reading that is an official site wins. A site the link engine flags, read out with "log in here / that's the link", is red; a site that is not a known official one, with "that's the link / log in here", is amber. On both live calls the on-device AI had already answered SCAM (prize_lottery, link_lure) and the calls ended red; now the alert also names the site.
- Held-out calls after these changes: 261 / 261 scams alerted, 4 / 2,829 false alarms (unchanged). Tests: `garbledDictatedLinkFromLiveCaption`, `urgentOtpDemandFromLiveCaptionIsRed`.

## Prevention (Phase 5) and results (Phase 6)

**Prevention.** Friction at the risky moment: on a link warning, **Go back** is the large default and **Open anyway** needs a second confirmation with an OTP / PIN reminder; scam calls get strong vibration plus an earpiece beep; risky messages get a red banner over the current app. If a warning happened in the last 15 minutes, the home screen reminds the user to call their bank if they already entered details. The PDF's "report this" screen (portal + 1930 dialler) was dropped by team decision.

**Results screen** (home → Open results) reads `assets/results.json`, written by `ml/eval_report.py`: precision, recall (fraud detection rate), FPR and N per channel and test set; a link-threshold slider over the held-out precision / recall curve; voice-script results; and latency measured on the phone from the decision log (median and 95th percentile per channel).

Email reading was tightened after a live false alarm: an email is read only when it is fully open (exactly one subject on screen and a Reply button), never from the inbox list; and when the SMS word model is the only signal on an email, the on-device AI decides (SAFE clears it).

## On-device AI second opinion (fine-tuned Gemma 3 270M)

Word rules miss scams with no link and no trigger words ("Hi mom, new number, send money", fake CEO, slow-burn investment chats). A small LLM, fine-tuned for one job and run fully offline on the phone, reads the message, the recent chat, an open email or the call transcript and answers `LABEL | reason` (SAFE / SUSPICIOUS / SCAM + a reason code).

- **Guardrails:** it can only **raise** a level, never lower it; it is skipped for trusted senders (bank headers, verified names in SMS apps) and when the rules already said DANGEROUS; links are removed from its input (the link engine judges links); its answer is forced into a fixed format.
- **Pipeline:** `ml/llm_data.py` (data + prompt format) → `ml/train_llm.py` (LoRA r=16 on the laptop GPU, ~10 min on an RTX 3050; loss on answer tokens only, because Gemma's 262k vocabulary does not fit full-sequence logits in 6 GB) → `ml/convert_llm.sh` (WSL, litert-torch, int8) → `ml/bundle_llm.sh` (LiteRT-LM `.litertlm` with the Gemma turn template) → `ml/test_litertlm.py` (runs the converted file on the laptop) → app asset `phisherman_scam.litertlm` (284 MB, not committed) → `engine/ScamLlm.kt` (`litertlm-android`).
- **Data:** 4,633 examples: UCI SMS, our Indian SMS, 58 hand-written no-link / chat / call scenarios, three Hugging Face datasets, and 60 templated genuine OTP / bank alerts.

| Held-out test set | v1 (P / R / FPR) | v2 | **v3 (shipped)** |
|---|---|---|---|
| All | 0.95 / 0.95 / 2.9% (N=431) | 0.98 / 0.98 / 2.0% (N=959) | **0.98 / 0.98 / 1.4%** (N=977) |
| Indian SMS (ours) | 0.82 / 1.00 / 13% | 0.75 / 1.00 / 20% | **0.94 / 0.94 / 3.3%** |
| No-link / chat / call scenarios | 0.90 / 0.82 / 14% | 0.91 / 0.91 / 14% | 0.91 / 0.91 / 14% (N=18, small) |
| Genuine OTP / bank alerts | — | — | 0 false alarms / 18 |

The untuned base model scored 0 on every set (it could not follow the answer format). v2 → v3: links removed from the model input and genuine OTP alerts added, which cut Indian false alarms from 20% to 3.3%. The 1.00 scores on the Hugging Face sets are not trustworthy (templated / machine-generated). Known misses: a slow-burn crypto "teacher" chat; a debit-alert scam whose only signal was its link (caught by the link engine in the app).

On the phone (S21 FE, CPU): about **0.9–1.1 s per check**. The converted file was checked on the laptop first: prompt template identical to training, 5 / 6 smoke-test cases correct.

## External dependencies

### Toolchain

| Tool | Version | Notes |
|---|---|---|
| Android Studio | 2026.2.1.8 | Installed via `winget install Google.AndroidStudio` |
| JDK | OpenJDK 25.0.3 (Android Studio bundled JBR) | `JAVA_HOME` = `C:\Program Files\Android\Android Studio\jbr` |
| Android SDK Platform | API 37.2 (`platforms;android-37.2`) and API 36 | `compileSdk` 37.2 (required by core-ktx 1.19 and other current AndroidX), `targetSdk` 36, `minSdk` 26; APK built for `arm64-v8a` only |
| Android SDK Build-Tools | 36.0.0 | |
| Android SDK Platform-Tools (adb) | 37.0.1 | |
| Android SDK Command-line Tools | 12.0 (`commandlinetools-win-11076708`) | `sdkmanager` |
| Gradle (wrapper) | 9.8.2 | `android/gradlew` |
| Python | 3.12.10 | ML training and evaluation |
| Git | system | |

### Android libraries (`android/gradle/libs.versions.toml`)

| Library / plugin | Version | Used for |
|---|---|---|
| Android Gradle Plugin `com.android.application` | 9.4.1 | Build |
| Kotlin (`kotlin.plugin.compose`, `kotlin.plugin.serialization`) | 2.4.21 | Language, Compose compiler, JSON |
| KSP `com.google.devtools.ksp` | 2.3.12 | Room annotation processing |
| `androidx.core:core-ktx` | 1.19.1 | Android helpers |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.11.0 | `lifecycleScope` for off-main-thread scoring |
| `androidx.activity:activity-compose` | 1.13.0 | Compose activities |
| `androidx.compose:compose-bom` (ui, material3, tooling) | 2026.09.00 | UI |
| `androidx.room:room-runtime` / `room-ktx` / `room-compiler` | 2.8.5 | Local decision log |
| `com.microsoft.onnxruntime:onnxruntime-android` | 1.31.1 | Runs `link_model.onnx` on device |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.12.0 | Loads JSON assets (SMS model, thresholds, rules, results) |
| `com.google.ai.edge.litertlm:litertlm-android` | 0.18.1 | Runs the fine-tuned Gemma 3 270M (`.litertlm`) on the phone |
| `junit:junit` | 4.13.2 | Kotlin unit and parity tests |
| `com.alphacephei:vosk-android` | not added | Optional offline speech fallback (Phase 4, only if on-device recognition is poor) |

### Python packages (`ml/`)

| Package | Version | Used for |
|---|---|---|
| scikit-learn | 1.9.1 | Random Forest (links), TF-IDF + logistic regression (SMS), metrics |
| skl2onnx | 1.20.0 | Export link model to ONNX |
| onnx | 1.23.2 | ONNX model format |
| onnxruntime | 1.31.1 | Check exported model reproduces sklearn probabilities |
| pandas | 3.1.0 | Data loading and cleaning |
| numpy | 2.5.4 | Feature arrays |
| pytest | 9.2.0 | Feature unit tests |
| matplotlib | 3.11.2 | PR curve and confusion matrix plots |
| jupyter / notebook | 7.6.3 | Exploration notebooks |
| pyarrow | 26.0.0 | Reading the pirocheto Parquet files |
| torch | 2.11.0+cu128 | LLM fine-tuning on the laptop GPU |
| transformers / peft | 5.21.0 / 0.21.2 | Gemma 3 270M loading and LoRA |
| litert-torch, litert-lm, tensorflow-cpu (WSL Ubuntu, Python 3.12 via uv) | 0.10.0 / 0.18.1 / latest | Convert the fine-tuned model to int8 `.litertlm` and test it on the laptop (`ml/convert_llm.sh`, `ml/bundle_llm.sh`, `ml/test_litertlm.py`) |

### Platform services and external endpoints

| Dependency | Used for | Network? |
|---|---|---|
| Android `SpeechRecognizer` (on-device recogniser preferred, `en-IN` / `hi-IN`) | Call guard transcription | On-device where the phone supports it |
| Android default-browser role, SMS broadcast, notifications, foreground service | Link interception, SMS analysis, warnings, call guard | No |
| National Cyber Crime Reporting Portal: https://cybercrime.gov.in | "Report now" button (opened in the browser) | Opened by the user only |
| National cyber-fraud helpline 1930 | "Call 1930" button (`ACTION_DIAL`; the user presses call) | No |
| Shortened-link expansion (HEAD request, 2 s timeout, max 3 redirects) | Optional, opt-in only | Yes, opt-in |

Core detection makes no network calls. Verify the portal address and helpline number from official government sources on demo day.

## Demo phone

| Model | Android version | Default browser before setup |
|---|---|---|
| Samsung Galaxy S21 FE (SM-G990E), arm64-v8a | Android 14 (API 34), One UI 6.1 | Chrome (`com.android.chrome`), restore it after the demo |
