package app.xrtm8.athar.model

enum class FilmCategory(val labelAr: String) {
    ALL("الكل"),
    FAVORITES("المفضلة"),
    CLASSIC_CAMERAS("كاميرات كلاسيكية"),
    INSTANT_VIDEO("فورية وفيديو"),
    MONOCHROME("أحادي"),
    CINEMA("سينمائي"),
    PICS_STYLE("فلاتر عصرية")
}

enum class FilmPreset(
    val id: String,
    val titleAr: String,
    val brand: String,
    val description: String,
    val category: FilmCategory = FilmCategory.CLASSIC_CAMERAS,
    val isMonochrome: Boolean = false
) {
    NONE(
        id = "none",
        titleAr = "بدون فلتر",
        brand = "أصلي",
        description = "الصورة الطبيعية بدون تعديل لوني",
        category = FilmCategory.ALL
    ),
    FUJI_CLASSIC_CHROME(
        id = "fuji_classic_chrome",
        titleAr = "فوجي كروم كلاسيك",
        brand = "Fujifilm",
        description = "ألوان هادئة وسينمائية مع تباين ناعم للظلال",
        category = FilmCategory.CLASSIC_CAMERAS
    ),
    FUJI_VELVIA(
        id = "fuji_velvia",
        titleAr = "فوجي فيلفيا 50",
        brand = "Fujifilm",
        description = "تشبع عالٍ وألوان حية للطبيعة والسماء",
        category = FilmCategory.CLASSIC_CAMERAS
    ),
    FUJI_PROVIA(
        id = "fuji_provia",
        titleAr = "فوجي بروفيا 100F",
        brand = "Fujifilm",
        description = "تدرج متزن وألوان طبيعية متعادلة",
        category = FilmCategory.CLASSIC_CAMERAS
    ),
    KODAK_PORTRA_400(
        id = "kodak_portra_400",
        titleAr = "كوداك بورترا 400",
        brand = "Kodak",
        description = "دفء ناعم وألوان بشرة ذهبية متوازنة",
        category = FilmCategory.CLASSIC_CAMERAS
    ),
    KODAK_TRI_X(
        id = "kodak_tri_x",
        titleAr = "كوداك تري-إكس 400",
        brand = "Kodak",
        description = "أبيض وأسود عالي التباين بطابع وثائقي عتيق",
        category = FilmCategory.MONOCHROME,
        isMonochrome = true
    ),
    LEICA_MONOCHROME(
        id = "leica_mono",
        titleAr = "لايكا مونوكروم",
        brand = "Leica",
        description = "أبيض وأسود نقي بتدرجات رمادية ناعمة وعميقة",
        category = FilmCategory.MONOCHROME,
        isMonochrome = true
    ),
    CINEMATIC_TEAL_ORANGE(
        id = "teal_orange",
        titleAr = "سينمائي تيل & أورانج",
        brand = "Cinema",
        description = "تدرج سينمائي بظلال تركواز وإضاءات دافئة",
        category = FilmCategory.CINEMA
    ),
    VINTAGE_WARMTH(
        id = "vintage_warmth",
        titleAr = "دفء السبعينات",
        brand = "Vintage",
        description = "ألوان باهتة دافئة مع لمسة ذهبية نوستالجية",
        category = FilmCategory.CINEMA
    ),
    DREAM_GLOW(
        id = "dream_glow",
        titleAr = "إشراقة حالمة",
        brand = "Optic",
        description = "ظلال ناعمة ووهج دافئ متوازن للإضاءات",
        category = FilmCategory.CINEMA
    ),
    HYBRID_CCD_2005(
        id = "hybrid_ccd_2005",
        titleAr = "ديجيتال ٢٠٠٥",
        brand = "CCD",
        description = "طابع كاميرات الديجيتال الأولى بألوان حادة ونقية",
        category = FilmCategory.CLASSIC_CAMERAS
    ),
    MINIDV_CAM_2002(
        id = "minidv_cam_2002",
        titleAr = "كاميرا MiniDV",
        brand = "Camcorder",
        description = "كاميرا فيديو ٢٠٠٢ بدرجات باردة ونعومة ألوان الشريط",
        category = FilmCategory.INSTANT_VIDEO
    ),
    SLIDE_CHROME_100(
        id = "slide_chrome_100",
        titleAr = "شرائح عرض 100",
        brand = "Slide",
        description = "تباين شرائح العرض العميق وألوان مشبعة متماسكة",
        category = FilmCategory.CLASSIC_CAMERAS
    ),
    DISPOSABLE_COOL_400(
        id = "disposable_cool_400",
        titleAr = "فيلم فوري بارد",
        brand = "35mm",
        description = "كاميرا ٣٥مم فورية بظلال خضراء باردة وإضاءة واضحة",
        category = FilmCategory.INSTANT_VIDEO
    ),
    DISPOSABLE_WARM_800(
        id = "disposable_warm_800",
        titleAr = "فيلم فوري دافئ",
        brand = "35mm",
        description = "كاميرا فورية بلمسة فلاش ذهبية وظلال دافئة",
        category = FilmCategory.INSTANT_VIDEO
    ),
    STREET_MONO_GR(
        id = "street_mono_gr",
        titleAr = "أبيض وأسود حاد",
        brand = "Street",
        description = "أحادي عالي الحدة والتباين لتصوير الشوارع",
        category = FilmCategory.MONOCHROME,
        isMonochrome = true
    ),
    TOY_MEDIUM_120(
        id = "toy_medium_120",
        titleAr = "عدسة توي ١٢٠",
        brand = "Medium 120",
        description = "كاميرا متوسطة الحجم بألوان متقاطعة ونعومة حواف",
        category = FilmCategory.CLASSIC_CAMERAS
    ),
    RANGEFINDER_400(
        id = "rangefinder_400",
        titleAr = "رينج فايندر ٤٠٠",
        brand = "Rangefinder",
        description = "محاكاة كلاسيكية بدرجات بشرة طبيعية وخضرة متزنة",
        category = FilmCategory.CLASSIC_CAMERAS
    ),
    NTRL1(
        id = "ntrl1",
        titleAr = "NTRL1",
        brand = "Natural",
        description = "إضاءة طبيعية ناعمة وتوازن ألوان نقي",
        category = FilmCategory.PICS_STYLE
    ),
    NTRL2(
        id = "ntrl2",
        titleAr = "NTRL2",
        brand = "Natural",
        description = "وضوح نهاري متوازن مع تباين لطيف",
        category = FilmCategory.PICS_STYLE
    ),
    NTRL3(
        id = "ntrl3",
        titleAr = "NTRL3",
        brand = "Natural",
        description = "ألوان طبيعية هادئة بلمسة مطفأة ناعمة",
        category = FilmCategory.PICS_STYLE
    ),
    BRNZ1(
        id = "brnz1",
        titleAr = "BRNZ1",
        brand = "Bronze",
        description = "إشراقة برونزية دافئة بلمسات ذهبية",
        category = FilmCategory.PICS_STYLE
    ),
    BRNZ2(
        id = "brnz2",
        titleAr = "BRNZ2",
        brand = "Bronze",
        description = "برونز عميق بتباين غني وظلال دافئة",
        category = FilmCategory.PICS_STYLE
    ),
    BRNZ4(
        id = "brnz4",
        titleAr = "BRNZ4",
        brand = "Bronze",
        description = "نحاسي سينمائي بدرجات خضراء مخففة",
        category = FilmCategory.PICS_STYLE
    ),
    TL1(
        id = "tl1",
        titleAr = "TL1",
        brand = "Teal",
        description = "ظلال تيل باردة وإضاءات نقية متوازنة",
        category = FilmCategory.PICS_STYLE
    ),
    MIL1(
        id = "mil1",
        titleAr = "MIL1",
        brand = "Milk",
        description = "طابع حليبي ناعم بظلال مرفوعة وباستيل هادئ",
        category = FilmCategory.PICS_STYLE
    )
}
