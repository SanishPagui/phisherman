"""
gen_data.py — reproducible SYNTHETIC datasets for the link and SMS models.

IMPORTANT (honesty, PS4 Section 6.3 / 11.4): these are *synthetic* examples generated
locally so the whole pipeline runs offline end-to-end and produces reproducible numbers.
They are NOT the real URL-Phish / Kaggle / Mendeley datasets named in the guide. Every
row is marked synthetic=1. Replace data/*.csv with the real, licensed datasets before
quoting any metric as a result — the training/eval scripts read the same CSV schema.

Schema: url,label,synthetic   (label: 1 = phishing, 0 = benign)
        text,label,synthetic   (label: 1 = phishing, 0 = normal/promo)

A second "unseen" source is generated with a deliberately different style so the
cross-source generalisation drop in Section 11.2 is visible.
"""

import csv
import os
import random

HERE = os.path.dirname(__file__)
DATA = os.path.join(HERE, "data")
os.makedirs(DATA, exist_ok=True)

BRANDS = ["sbi", "hdfc", "icici", "axis", "kotak", "paytm", "phonepe", "amazon",
          "flipkart", "irctc", "indiapost", "incometax", "epfo", "uidai"]
SUSP_WORDS = ["login", "verify", "kyc", "update", "secure", "account", "confirm",
              "refund", "reward", "bonus", "otp", "suspend", "netbanking", "wallet"]
RISKY_TLDS = ["xyz", "top", "icu", "cfd", "click", "buzz", "sbs", "rest", "online", "info"]
SHORTENERS = ["bit.ly", "tinyurl.com", "cutt.ly", "rb.gy", "t.co", "is.gd"]
HOMOGLYPH = {"o": "0", "l": "1", "e": "3", "a": "4", "s": "5", "i": "1"}

# Real-ish benign registrable domains (used only for shape; no network contact).
BENIGN_DOMAINS = [
    "wikipedia.org", "google.com", "youtube.com", "github.com", "stackoverflow.com",
    "amazon.in", "flipkart.com", "irctc.co.in", "hdfcbank.com", "sbi.co.in",
    "icicibank.com", "paytm.com", "phonepe.com", "indiapost.gov.in", "incometax.gov.in",
    "linkedin.com", "medium.com", "reddit.com", "microsoft.com", "apple.com",
    "netflix.com", "swiggy.com", "zomato.com", "makemytrip.com", "cleartrip.com",
]
BENIGN_PATHS = [
    "/", "/wiki/India", "/search?q=weather+today", "/account/login", "/help/support",
    "/in/kuldeep", "/products/12345", "/watch?v=dQw4w9WgXcQ", "/orders/history",
    "/settings/profile", "/news/2026/election-results", "/maps/place/Connaught+Place",
]


def leet(s):
    return "".join(HOMOGLYPH.get(c, c) if random.random() < 0.5 else c for c in s)


def gen_phish_url(rng, style="a"):
    brand = rng.choice(BRANDS)
    word = rng.choice(SUSP_WORDS)
    tld = rng.choice(RISKY_TLDS)
    kind = rng.random()
    if kind < 0.22:  # brand + keyword hyphenated lookalike
        host = f"{brand}-{word}-{rng.choice(['india','online','support','care'])}.{tld}"
        path = f"/{word}/{rng.choice(['index','account','secure'])}.php?id={rng.randint(1000,99999)}"
    elif kind < 0.40:  # typosquat / homoglyph of a brand
        host = f"{leet(brand)}{rng.choice(['bank','pay','secure',''])}.{tld}"
        path = f"/{word}?ref={rng.randint(100,999)}"
    elif kind < 0.55:  # deep subdomain nesting hiding attacker domain
        host = f"{word}.{brand}.com.{rng.choice(['secure','verify'])}-{rng.randint(1,99)}.{tld}"
        path = f"/{word}/login"
    elif kind < 0.68:  # raw IP host
        host = ".".join(str(rng.randint(1, 254)) for _ in range(4))
        path = f"/{word}/account?user={rng.randint(1,9999)}"
    elif kind < 0.80:  # shortener
        host = rng.choice(SHORTENERS)
        path = "/" + "".join(rng.choice("abcdefghijkmnpqrstuvwxyz0123456789") for _ in range(6))
    elif kind < 0.90:  # high-entropy random host
        host = "".join(rng.choice("abcdefghijklmnopqrstuvwxyz0123456789") for _ in range(rng.randint(12, 22))) + "." + tld
        path = f"/{word}/{word}"
    else:  # userinfo @ trick
        host = f"{brand}.com@{leet(brand)}-{word}.{tld}"
        path = f"/{word}"
    if style == "b":  # unseen source: longer query strings, different TLD mix
        path += "&" + "&".join(f"p{i}={rng.randint(1,9999)}" for i in range(rng.randint(1, 4)))
    scheme = "http" if rng.random() < 0.5 else "https"
    return f"{scheme}://{host}{path}"


def gen_benign_url(rng, style="a"):
    dom = rng.choice(BENIGN_DOMAINS)
    path = rng.choice(BENIGN_PATHS)
    sub = ""
    if rng.random() < 0.3:
        sub = rng.choice(["www.", "login.", "accounts.", "m.", "secure."])
    if style == "b":  # unseen benign: add a couple of realistic long query params
        if rng.random() < 0.6:
            path += ("?" if "?" not in path else "&") + f"utm_source=mail&sid={rng.randint(10000,99999)}"
    return f"https://{sub}{dom}{path}"


def write_urls(path, rng, n_phish, n_benign, style):
    rows = [("url", "label", "synthetic")]
    for _ in range(n_phish):
        rows.append((gen_phish_url(rng, style), 1, 1))
    for _ in range(n_benign):
        rows.append((gen_benign_url(rng, style), 0, 1))
    body = rows[1:]
    rng.shuffle(body)
    rows[1:] = body
    with open(path, "w", newline="", encoding="utf-8") as fh:
        csv.writer(fh).writerows(rows)
    return len(rows) - 1


# ---------------------------------------------------------------------------- SMS

PHISH_SMS = [
    "Your {b} account will be blocked today. Update KYC now at {u}",
    "Dear customer, your {b} netbanking is suspended. Verify at {u}",
    "Aapka {b} khata band ho jayega. Turant KYC update karein {u}",
    "Congratulations! You won Rs {n} reward. Claim now at {u}",
    "Your package is held. Pay customs fee Rs {n} at {u}",
    "{b} KYC expired. Complete verification within 24 hours {u}",
    "URGENT: Share the OTP {c} received to confirm your {b} account",
    "Your electricity will be disconnected tonight. Pay bill at {u}",
    "Income tax refund of Rs {n} pending. Verify bank details at {u}",
    "Aapke naam pe loan approve hua hai Rs {n}. Apply {u}",
    "Your debit card is blocked. Call and share CVV to reactivate {u}",
    "Job offer confirmed! Pay registration Rs {n} to {u}",
]
NORMAL_SMS = [
    "Your OTP is {c}. Do not share it with anyone. - {b}",
    "Debit alert: Rs {n} spent at a shop using {b} card ending {n2}",
    "Dear customer, Rs {n} credited to your {b} account. Avl bal Rs {n3}",
    "Your {b} statement for this month is ready in the app",
    "Reminder: your appointment is tomorrow at 10 AM",
    "Hi, are we still meeting for lunch today?",
    "Your order has been delivered. Thank you for shopping with us",
    "Mummy ne kaha hai jaldi ghar aao, khana ready hai",
    "Your recharge of Rs {n} is successful. Validity 28 days",
    "Flight AI{n2} is on time. Boarding gate will be announced soon",
]
PROMO_SMS = [
    "Big Sale this weekend! Flat 40% off on all items. Visit store now",
    "Get 2x cashback on your next UPI payment this Friday only",
    "New collection just dropped! Shop the latest styles today",
    "Limited offer: buy 1 get 1 free on all beverages",
    "Upgrade to our premium plan and save 30% this month",
]


def gen_sms(rng, template):
    b = rng.choice(["SBI", "HDFC", "ICICI", "Axis", "Paytm", "PhonePe", "Amazon"])
    u = rng.choice([
        f"http://{rng.choice(BRANDS)}-kyc.{rng.choice(RISKY_TLDS)}/login",
        f"https://{rng.choice(SHORTENERS)}/{''.join(rng.choice('abc123xyz') for _ in range(6))}",
        f"http://verify-{rng.choice(SUSP_WORDS)}.{rng.choice(RISKY_TLDS)}/",
    ])
    return template.format(b=b, u=u, n=rng.randint(499, 99999),
                           n2=rng.randint(10, 99), n3=rng.randint(1000, 50000),
                           c=rng.randint(100000, 999999))


def write_sms(path, rng, n_phish, n_normal, n_promo):
    rows = [("text", "label", "synthetic")]
    for _ in range(n_phish):
        rows.append((gen_sms(rng, rng.choice(PHISH_SMS)), 1, 1))
    for _ in range(n_normal):
        rows.append((gen_sms(rng, rng.choice(NORMAL_SMS)), 0, 1))
    for _ in range(n_promo):
        rows.append((gen_sms(rng, rng.choice(PROMO_SMS)), 0, 1))
    body = rows[1:]
    rng.shuffle(body)
    rows[1:] = body
    with open(path, "w", newline="", encoding="utf-8") as fh:
        csv.writer(fh).writerows(rows)
    return len(rows) - 1


def main():
    rng = random.Random(42)
    n1 = write_urls(os.path.join(DATA, "train_source.csv"), rng, 3000, 3000, "a")
    n2 = write_urls(os.path.join(DATA, "unseen_source.csv"), rng, 800, 800, "b")
    n3 = write_sms(os.path.join(DATA, "sms_train.csv"), rng, 500, 400, 200)
    print(f"train_source.csv : {n1} urls")
    print(f"unseen_source.csv: {n2} urls (different style)")
    print(f"sms_train.csv    : {n3} messages")
    print("NOTE: all rows synthetic=1 — replace with real licensed datasets before quoting results.")


if __name__ == "__main__":
    main()
