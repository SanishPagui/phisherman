# Demo script (≈2 minutes) & pitch outline

Record this exact sequence as a **backup video** before the first rehearsal. If anything
fails on stage, play the video without apology.

## 2-minute live demo
1. **Setup shot.** Home screen shows “Protection is on”, default-browser set, call guard
   ready. Say the phone model and Android version.
2. **Link.** Open a messaging app, tap the fake bank link (L1). The warning appears **before
   anything loads** — read one reason aloud, tap **Go back**. Tap a safe link (L6): it opens
   your normal browser with no visible delay.
3. **SMS.** Paste S6 (Hinglish KYC) and S2 (genuine OTP) into the home paste box. S6 is
   flagged DANGEROUS with reasons; S2 stays SAFE — the notification explains why.
4. **Voice.** Tap **Start call guard**, play the scam script (`scam_call_script.txt`). Point
   to the meter going amber → red **before** the payment demand. (Or feed the lines by text.)
5. **Prevention.** Tap **Report** on any warning → evidence pack, cybercrime.gov.in button,
   1930 dialler.
6. **Results.** Open the **Results** tab; drag the sensitivity slider in **Settings** to show
   the precision/recall trade-off.

## Six slides (< 5 minutes)
1. **Problem** — phishing is the first step of financial fraud; cite one or two Indian
   figures *with* “according to”. We stop the click, the code and the call.
2. **Solution** — one app, three channels, one risk engine; warns before the link opens.
3. **Live demo** — the sequence above, phone mirrored on screen.
4. **How it works** — 18-feature URL model (ONNX), Hinglish TF-IDF SMS classifier + rules,
   speech → phrase scoring; all on-device; Python↔Kotlin parity tested.
5. **Results** — precision, recall (fraud-detection rate), FPR & latency per channel; the
   **unseen-dataset drop**; one failure and the fix. (Replace synthetic numbers with your
   measured ones.)
6. **Limits & roadmap** — Android call-audio limits, small Hinglish data; next: transformer
   SMS, deepfake-voice detection, payment-moment guard, bank/telecom integration.

## Honest Q&A anchors
- **False alarm?** Second-tap “Open anyway”, logged as a hard negative; measured FPR is X.
- **Brand-new domain?** Lexical + brand features need no blocklist; paired with content and
  user-friction layers.
- **Why on-device?** Speed, privacy, offline; visible consent for the mic.
- **Unseen accuracy lower?** Different sources differ; we split by domain and report the drop.
- **Deepfakes / iPhone?** Not in this build — scored *what is said*; roadmap items.
