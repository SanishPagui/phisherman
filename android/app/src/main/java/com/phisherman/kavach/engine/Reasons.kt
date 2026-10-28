package com.phisherman.kavach.engine

/**
 * Catalogue of bilingual reasons. Every non-safe verdict carries at least one plain
 * reason — "Looks like HDFC Bank but the domain is not hdfcbank.com" persuades far
 * better than "risk 0.93" (PS4 Section 3.6, Explainable).
 */
object Reasons {
    // ---- Link ----
    fun brandLookalike(brand: String, realDomain: String?) = Reason(
        "L_BRAND_LOOKALIKE",
        "Looks like $brand but the domain is not ${realDomain ?: "its official site"}",
        "यह $brand जैसा दिखता है पर डोमेन ${realDomain ?: "असली साइट"} नहीं है",
    )
    val BRAND_IN_SUBDOMAIN = Reason(
        "L_BRAND_IN_SUBDOMAIN",
        "A trusted brand name appears, but the real domain belongs to someone else",
        "भरोसेमंद ब्रांड नाम दिखता है, पर असली डोमेन किसी और का है",
    )
    val IP_HOST = Reason(
        "L_IP_HOST",
        "Uses a raw IP address instead of a real site name",
        "असली साइट नाम के बजाय सीधे IP पते का उपयोग करता है",
    )
    val AT_IN_URL = Reason(
        "L_AT_IN_URL",
        "Hides the real destination with an '@' in the address",
        "पते में '@' के ज़रिये असली गंतव्य छिपाता है",
    )
    val DEEP_NESTING = Reason(
        "L_DEEP_NESTING",
        "The address is deeply nested to mimic a trusted site",
        "भरोसेमंद साइट की नकल के लिए पता बहुत गहराई तक नेस्टेड है",
    )
    val SHORTENER = Reason(
        "L_SHORTENER",
        "Shortened link hides its real destination",
        "छोटा किया गया लिंक अपना असली गंतव्य छिपाता है",
    )
    val SUSPICIOUS_WORDS = Reason(
        "L_SUSP_WORDS",
        "Contains lure words like login, verify or KYC",
        "login, verify या KYC जैसे लुभावने शब्द शामिल हैं",
    )
    val RISKY_TLD = Reason(
        "L_RISKY_TLD",
        "Uses a domain ending often seen in throwaway scam sites",
        "ऐसा डोमेन-अंत जो अक्सर फेंक-दो स्कैम साइटों में दिखता है",
    )
    val HIGH_ENTROPY = Reason(
        "L_HIGH_ENTROPY",
        "The site name looks random, like an auto-generated domain",
        "साइट नाम यादृच्छिक लगता है, जैसे अपने-आप बना डोमेन",
    )
    val MODEL_HIGH = Reason(
        "L_MODEL",
        "Our on-device model rates this link as risky",
        "हमारा ऑन-डिवाइस मॉडल इस लिंक को जोखिम भरा मानता है",
    )
    val KNOWN_SAFE = Reason(
        "L_ALLOWLIST",
        "Known, trusted domain",
        "ज्ञात, भरोसेमंद डोमेन",
    )
    val MODEL_UNAVAILABLE = Reason(
        "L_MODEL_FALLBACK",
        "Could not fully check this link, treating it with caution",
        "इस लिंक की पूरी जाँच नहीं हो सकी, सावधानी से लिया जा रहा है",
    )

    // ---- SMS ----
    val OTP_ASK = Reason(
        "R_OTP_ASK",
        "Asks you to share an OTP, PIN or card details — a bank never does this",
        "OTP, PIN या कार्ड विवरण साझा करने को कहता है — बैंक ऐसा कभी नहीं करता",
    )
    val URGENT_LINK = Reason(
        "R_URGENT_LINK",
        "Pressure to act fast, combined with a link",
        "जल्दी कार्रवाई का दबाव, साथ में एक लिंक",
    )
    val SENDER_MISMATCH = Reason(
        "R_SENDER_MISMATCH",
        "Claims to be a bank but comes from a personal number",
        "बैंक होने का दावा करता है पर व्यक्तिगत नंबर से आया है",
    )
    val BRAND_LINK_MISMATCH = Reason(
        "R_BRAND_LINK",
        "Names a brand but the link does not go to its official site",
        "ब्रांड का नाम लेता है पर लिंक उसकी आधिकारिक साइट पर नहीं जाता",
    )
    val APP_INSTALL = Reason(
        "R_APP_INSTALL",
        "Asks you to install an app from a link",
        "किसी लिंक से ऐप इंस्टॉल करने को कहता है",
    )
    val SMS_MODEL_HIGH = Reason(
        "R_MODEL",
        "The message wording matches known phishing patterns",
        "संदेश की भाषा ज्ञात फ़िशिंग पैटर्न से मेल खाती है",
    )
    val SMS_HAS_LINK_RISK = Reason(
        "R_LINK_RISK",
        "The link inside this message is itself risky",
        "इस संदेश के अंदर का लिंक स्वयं जोखिम भरा है",
    )

    // ---- Voice ----
    fun voiceCategory(label: String, labelHi: String?) =
        Reason("V_CATEGORY", label, labelHi)
}
