package com.mariotatis.lockcontrol.data

import org.json.JSONObject

enum class BackgroundType { GRADIENT, IMAGE, VIDEO }

/** Typeface family; thickness is a separate weight (100..900). */
enum class ClockFont { SANS, CONDENSED, SERIF, MONO }

enum class ClockLayout { INLINE, STACKED }

/** Date styles; [pattern] null means the device's own short numeric date. */
enum class DateFormat(val pattern: String?) {
    LONG("EEEE, MMMM d"),
    SHORT("EEE d MMM"),
    WEEKDAY_DAY("EEEE d"),
    MONTH_DAY("MMMM d"),
    WEEKDAY("EEEE"),
    FULL("EEEE, MMMM d, yyyy"),
    NUMERIC(null),
    ISO("yyyy-MM-dd"),
}

/** Look of the now-playing widget. */
enum class MediaStyle { CARD, SQUARE, WAVE, PILL }

/** How the notification group is drawn. */
enum class NotifStyle { LIST, STACK, ICONS }

/** Look of the passcode keys. */
enum class KeyStyle { GLASS, OUTLINE, SQUARE, MINIMAL }

/** Styling for a free-text element (unlock hint, passcode prompt). */
data class TextSpec(
    val font: ClockFont = ClockFont.SANS,
    val weight: Int = 500,
    val color: Int = 0xFFFFFFFF.toInt(),
    val alpha: Float = 1f,
    val size: Float = 16f,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("font", font.name)
        put("weight", weight)
        put("color", color)
        put("alpha", if (alpha.isFinite()) alpha.toDouble() else 1.0)
        put("size", if (size.isFinite()) size.toDouble() else 16.0)
    }

    companion object {
        fun fromJson(o: JSONObject?, default: TextSpec): TextSpec {
            if (o == null) return default
            return TextSpec(
                font = ClockFont.entries.firstOrNull { it.name == o.optString("font") } ?: default.font,
                weight = o.optInt("weight", default.weight),
                color = o.optInt("color", default.color),
                alpha = o.optDouble("alpha", default.alpha.toDouble()).toFloat(),
                size = o.optDouble("size", default.size.toDouble()).toFloat(),
            )
        }
    }
}

/** Allowed ranges for widget sizing, shared by sliders and corner-drag resizing. */
object WidgetLimits {
    val clockSize = 32f..400f
    val dateSize = 10f..120f
    val stretch = 0.4f..3f
    val weight = 100f..900f
    val bgScale = 1f..5f
    val keypadScale = 0.5f..1f
    val lockIconSize = 12f..96f
    /** Uniform scale for box widgets (music, notifications), stored as a percentage. */
    val boxScale = 50f..200f
    /** Text / button size multipliers inside the music and notification widgets. */
    val innerScale = 0.7f..2.2f
}

/**
 * Everything that describes the lock screen. Widget positions are stored as
 * the normalized center of the widget (0..1 on each axis). The background
 * offset is a fraction of the screen size, applied after scaling.
 */
data class LockConfig(
    val enabled: Boolean = true,
    val lockOnBoot: Boolean = true,
    val backgroundType: BackgroundType = BackgroundType.GRADIENT,
    val backgroundFile: String? = null,
    val gradientIndex: Int = 0,
    val dim: Float = 0.15f,
    val bgScale: Float = 1f,
    val bgOffsetX: Float = 0f,
    val bgOffsetY: Float = 0f,
    val showClock: Boolean = true,
    val clockX: Float = 0.5f,
    val clockY: Float = 0.36f,
    val clockSize: Float = 120f,
    val clockStretch: Float = 1f,
    val clockFont: ClockFont = ClockFont.SANS,
    val clockWeight: Int = 200,
    val clockLayout: ClockLayout = ClockLayout.INLINE,
    val clockColor: Int = 0xFFFFFFFF.toInt(),
    val clockAlpha: Float = 1f,
    val use24h: Boolean = false,
    val showSeconds: Boolean = false,
    val showAmPm: Boolean = false,
    val clockLeadingZero: Boolean = false,
    val showDate: Boolean = true,
    val dateX: Float = 0.5f,
    val dateY: Float = 0.17f,
    val dateSize: Float = 22f,
    val dateStretch: Float = 1f,
    val dateFont: ClockFont = ClockFont.SANS,
    val dateWeight: Int = 600,
    val dateColor: Int = 0xFFFFFFFF.toInt(),
    val dateAlpha: Float = 0.9f,
    val dateFormat: DateFormat = DateFormat.LONG,
    val clockShadow: Boolean = true,
    val showBattery: Boolean = true,
    val showLockIcon: Boolean = true,
    val lockIconColor: Int = 0xFFFFFFFF.toInt(),
    val lockIconAlpha: Float = 0.9f,
    val lockIconSize: Float = 22f,
    val lockIconX: Float = 0.5f,
    val lockIconY: Float = 0.045f,
    val showHint: Boolean = true,
    val showHintChevron: Boolean = true,
    val showHintBar: Boolean = true,
    val hintText: String = "",
    val hintStyle: TextSpec = TextSpec(weight = 500, alpha = 0.8f, size = 14f),
    val showPrompt: Boolean = true,
    val showPromptIcon: Boolean = true,
    val promptText: String = "",
    val promptStyle: TextSpec = TextSpec(weight = 500, alpha = 1f, size = 21f),
    val showKeyLetters: Boolean = true,
    val passcodeBlur: Float = 40f,
    val showMedia: Boolean = true,
    val mediaX: Float = 0.24f,
    val mediaY: Float = 0.72f,
    val mediaScale: Float = 80f,
    val mediaStyle: MediaStyle = MediaStyle.CARD,
    val mediaBgAlpha: Float = 0.35f,
    val mediaColor: Int = 0xFFFFFFFF.toInt(),
    val mediaTextScale: Float = 1.3f,
    val mediaButtonScale: Float = 1.35f,
    val showNotifications: Boolean = true,
    val notifX: Float = 0.76f,
    val notifY: Float = 0.72f,
    val notifScale: Float = 80f,
    val notifStyle: NotifStyle = NotifStyle.LIST,
    val notifMax: Int = 2,
    val notifHideContent: Boolean = false,
    val notifBgAlpha: Float = 0.35f,
    val notifTextScale: Float = 1.25f,
    val notifShowClear: Boolean = true,
    val keyStyle: KeyStyle = KeyStyle.GLASS,
    val keyColor: Int = 0xFFFFFFFF.toInt(),
    val keyAlpha: Float = 0.16f,
    val keyTextColor: Int = 0xFFFFFFFF.toInt(),
    val keypadScale: Float = 1f,
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val pinLength: Int = 0,
    /** Portrait positions/sizes ([LAYOUT_KEYS]); null until first edited in portrait. Top-level fields are landscape. */
    val portraitLayout: Map<String, Double>? = null,
) {
    /** This config as it should look in the given orientation (portrait swaps in its own layout). */
    fun forOrientation(portrait: Boolean): LockConfig {
        val layout = portraitLayout
        if (!portrait || layout == null) return this
        val o = JSONObject(toJson())
        layout.forEach { (k, v) -> if (v.isFinite()) o.put(k, v) }
        return fromJson(o.toString())
    }

    /**
     * Folds an [edited] copy of `forOrientation(portrait)` back into this config: layout fields go to
     * that orientation only, everything else (styles, colors, toggles) is shared.
     */
    fun mergeEdit(portrait: Boolean, edited: LockConfig): LockConfig {
        if (!portrait) return edited.copy(portraitLayout = portraitLayout)
        val editedJson = JSONObject(edited.toJson())
        val mine = JSONObject(toJson())
        val layout = LAYOUT_KEYS.associateWith { editedJson.optDouble(it) }.filterValues { it.isFinite() }
        LAYOUT_KEYS.forEach { if (mine.has(it)) editedJson.put(it, mine.get(it)) }
        return fromJson(editedJson.toString()).copy(portraitLayout = layout)
    }

    val hasPin: Boolean get() = pinHash != null && pinSalt != null && pinLength > 0

    fun hintLabel(): String = hintText.ifBlank {
        if (hasPin) "Swipe up or press any button to unlock" else "Swipe up or press any button to open"
    }

    fun promptLabel(): String = promptText.ifBlank { "Enter Passcode" }

    fun toJson(): String = JSONObject().apply {
        // org.json rejects NaN/Infinity; never let one bad gesture value break saving.
        fun num(v: Float): Double = if (v.isFinite()) v.toDouble() else 0.0

        put("enabled", enabled)
        put("lockOnBoot", lockOnBoot)
        put("backgroundType", backgroundType.name)
        put("backgroundFile", backgroundFile ?: JSONObject.NULL)
        put("gradientIndex", gradientIndex)
        put("dim", num(dim))
        put("bgScale", num(bgScale))
        put("bgOffsetX", num(bgOffsetX))
        put("bgOffsetY", num(bgOffsetY))
        put("showClock", showClock)
        put("clockX", num(clockX))
        put("clockY", num(clockY))
        put("clockSize", num(clockSize))
        put("clockStretch", num(clockStretch))
        put("clockFont", clockFont.name)
        put("clockWeight", clockWeight)
        put("clockLayout", clockLayout.name)
        put("clockColor", clockColor)
        put("clockAlpha", num(clockAlpha))
        put("use24h", use24h)
        put("showSeconds", showSeconds)
        put("showAmPm", showAmPm)
        put("clockLeadingZero", clockLeadingZero)
        put("showDate", showDate)
        put("dateX", num(dateX))
        put("dateY", num(dateY))
        put("dateSize", num(dateSize))
        put("dateStretch", num(dateStretch))
        put("dateFont", dateFont.name)
        put("dateWeight", dateWeight)
        put("dateColor", dateColor)
        put("dateAlpha", num(dateAlpha))
        put("dateFormat", dateFormat.name)
        put("clockShadow", clockShadow)
        put("showBattery", showBattery)
        put("showLockIcon", showLockIcon)
        put("lockIconColor", lockIconColor)
        put("lockIconAlpha", num(lockIconAlpha))
        put("lockIconSize", num(lockIconSize))
        put("lockIconX", num(lockIconX))
        put("lockIconY", num(lockIconY))
        put("showHint", showHint)
        put("showHintChevron", showHintChevron)
        put("showHintBar", showHintBar)
        put("hintText", hintText)
        put("hintStyle", hintStyle.toJson())
        put("showPrompt", showPrompt)
        put("showPromptIcon", showPromptIcon)
        put("promptText", promptText)
        put("promptStyle", promptStyle.toJson())
        put("showKeyLetters", showKeyLetters)
        put("passcodeBlur", num(passcodeBlur))
        put("showMedia", showMedia)
        put("mediaX", num(mediaX))
        put("mediaY", num(mediaY))
        put("mediaScale", num(mediaScale))
        put("mediaStyle", mediaStyle.name)
        put("mediaBgAlpha", num(mediaBgAlpha))
        put("mediaColor", mediaColor)
        put("mediaTextScale", num(mediaTextScale))
        put("mediaButtonScale", num(mediaButtonScale))
        put("showNotifications", showNotifications)
        put("notifX", num(notifX))
        put("notifY", num(notifY))
        put("notifScale", num(notifScale))
        put("notifStyle", notifStyle.name)
        put("notifMax", notifMax)
        put("notifHideContent", notifHideContent)
        put("notifBgAlpha", num(notifBgAlpha))
        put("notifTextScale", num(notifTextScale))
        put("notifShowClear", notifShowClear)
        put("keyStyle", keyStyle.name)
        put("keyColor", keyColor)
        put("keyAlpha", num(keyAlpha))
        put("keyTextColor", keyTextColor)
        put("keypadScale", num(keypadScale))
        put("pinHash", pinHash ?: JSONObject.NULL)
        put("pinSalt", pinSalt ?: JSONObject.NULL)
        put("pinLength", pinLength)
        portraitLayout?.let { layout ->
            put("portraitLayout", JSONObject().apply { layout.forEach { (k, v) -> if (v.isFinite()) put(k, v) } })
        }
    }.toString()

    companion object {
        /** Fields that are stored separately for portrait: where things are and how big. */
        val LAYOUT_KEYS = listOf(
            "clockX", "clockY", "clockSize", "clockStretch",
            "dateX", "dateY", "dateSize", "dateStretch",
            "mediaX", "mediaY", "mediaScale",
            "notifX", "notifY", "notifScale",
            "lockIconX", "lockIconY", "lockIconSize",
            "bgScale", "bgOffsetX", "bgOffsetY",
            "keypadScale",
        )

        /** Fonts saved by v1, where weight was baked into the font choice. */
        private val legacyFonts = mapOf(
            "THIN" to (ClockFont.SANS to 100),
            "LIGHT" to (ClockFont.SANS to 300),
            "REGULAR" to (ClockFont.SANS to 400),
            "BOLD" to (ClockFont.SANS to 700),
        )

        fun fromJson(json: String): LockConfig {
            val o = JSONObject(json)
            val d = LockConfig()
            fun f(key: String, def: Float) = o.optDouble(key, def.toDouble()).toFloat()

            val legacy = legacyFonts[o.optString("clockFont")]
            return LockConfig(
                enabled = o.optBoolean("enabled", d.enabled),
                lockOnBoot = o.optBoolean("lockOnBoot", d.lockOnBoot),
                backgroundType = enumOr(o.optString("backgroundType"), d.backgroundType),
                backgroundFile = o.optStringOrNull("backgroundFile"),
                gradientIndex = o.optInt("gradientIndex", d.gradientIndex),
                dim = f("dim", d.dim),
                bgScale = f("bgScale", d.bgScale),
                bgOffsetX = f("bgOffsetX", d.bgOffsetX),
                bgOffsetY = f("bgOffsetY", d.bgOffsetY),
                showClock = o.optBoolean("showClock", d.showClock),
                clockX = f("clockX", d.clockX),
                clockY = f("clockY", d.clockY),
                clockSize = f("clockSize", d.clockSize),
                clockStretch = f("clockStretch", d.clockStretch),
                clockFont = legacy?.first ?: enumOr(o.optString("clockFont"), d.clockFont),
                clockWeight = o.optInt("clockWeight", legacy?.second ?: d.clockWeight),
                clockLayout = enumOr(o.optString("clockLayout"), d.clockLayout),
                clockColor = o.optInt("clockColor", d.clockColor),
                clockAlpha = f("clockAlpha", d.clockAlpha),
                use24h = o.optBoolean("use24h", d.use24h),
                showSeconds = o.optBoolean("showSeconds", d.showSeconds),
                showAmPm = o.optBoolean("showAmPm", d.showAmPm),
                // v1.0 padded 24-hour and stacked clocks; keep that look for existing setups.
                clockLeadingZero = o.optBoolean(
                    "clockLeadingZero",
                    o.optBoolean("use24h", false) || o.optString("clockLayout") == ClockLayout.STACKED.name,
                ),
                showDate = o.optBoolean("showDate", d.showDate),
                dateX = f("dateX", d.dateX),
                dateY = f("dateY", d.dateY),
                dateSize = f("dateSize", d.dateSize),
                dateStretch = f("dateStretch", d.dateStretch),
                dateFont = enumOr(o.optString("dateFont"), d.dateFont),
                dateWeight = o.optInt("dateWeight", d.dateWeight),
                // Before the date had its own color it followed the clock's.
                dateColor = o.optInt("dateColor", o.optInt("clockColor", d.dateColor)),
                dateAlpha = f("dateAlpha", d.dateAlpha),
                dateFormat = enumOr(o.optString("dateFormat"), d.dateFormat),
                clockShadow = o.optBoolean("clockShadow", d.clockShadow),
                showBattery = o.optBoolean("showBattery", d.showBattery),
                showLockIcon = o.optBoolean("showLockIcon", d.showLockIcon),
                lockIconColor = o.optInt("lockIconColor", d.lockIconColor),
                lockIconAlpha = f("lockIconAlpha", d.lockIconAlpha),
                lockIconSize = f("lockIconSize", d.lockIconSize),
                lockIconX = f("lockIconX", d.lockIconX),
                lockIconY = f("lockIconY", d.lockIconY),
                showHint = o.optBoolean("showHint", d.showHint),
                showHintChevron = o.optBoolean("showHintChevron", d.showHintChevron),
                showHintBar = o.optBoolean("showHintBar", d.showHintBar),
                hintText = o.optString("hintText", d.hintText),
                hintStyle = TextSpec.fromJson(o.optJSONObject("hintStyle"), d.hintStyle),
                showPrompt = o.optBoolean("showPrompt", d.showPrompt),
                showPromptIcon = o.optBoolean("showPromptIcon", d.showPromptIcon),
                promptText = o.optString("promptText", d.promptText),
                promptStyle = TextSpec.fromJson(o.optJSONObject("promptStyle"), d.promptStyle),
                showKeyLetters = o.optBoolean("showKeyLetters", d.showKeyLetters),
                passcodeBlur = f("passcodeBlur", d.passcodeBlur),
                showMedia = o.optBoolean("showMedia", d.showMedia),
                mediaX = f("mediaX", d.mediaX),
                mediaY = f("mediaY", d.mediaY),
                mediaScale = f("mediaScale", d.mediaScale),
                mediaStyle = enumOr(o.optString("mediaStyle"), d.mediaStyle),
                mediaBgAlpha = f("mediaBgAlpha", d.mediaBgAlpha),
                mediaColor = o.optInt("mediaColor", d.mediaColor),
                mediaTextScale = f("mediaTextScale", d.mediaTextScale),
                mediaButtonScale = f("mediaButtonScale", d.mediaButtonScale),
                showNotifications = o.optBoolean("showNotifications", d.showNotifications),
                notifX = f("notifX", d.notifX),
                notifY = f("notifY", d.notifY),
                notifScale = f("notifScale", d.notifScale),
                notifStyle = enumOr(o.optString("notifStyle"), d.notifStyle),
                notifMax = o.optInt("notifMax", d.notifMax),
                notifHideContent = o.optBoolean("notifHideContent", d.notifHideContent),
                notifBgAlpha = f("notifBgAlpha", d.notifBgAlpha),
                notifTextScale = f("notifTextScale", d.notifTextScale),
                notifShowClear = o.optBoolean("notifShowClear", d.notifShowClear),
                keyStyle = enumOr(o.optString("keyStyle"), d.keyStyle),
                keyColor = o.optInt("keyColor", d.keyColor),
                keyAlpha = f("keyAlpha", d.keyAlpha),
                keyTextColor = o.optInt("keyTextColor", d.keyTextColor),
                keypadScale = f("keypadScale", d.keypadScale),
                pinHash = o.optStringOrNull("pinHash"),
                pinSalt = o.optStringOrNull("pinSalt"),
                pinLength = o.optInt("pinLength", d.pinLength),
                portraitLayout = o.optJSONObject("portraitLayout")?.let { p ->
                    p.keys().asSequence().associateWith { p.optDouble(it) }.filterValues { it.isFinite() }
                },
            )
        }

        private fun JSONObject.optStringOrNull(key: String): String? =
            if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

        private inline fun <reified T : Enum<T>> enumOr(name: String, default: T): T =
            enumValues<T>().firstOrNull { it.name == name } ?: default
    }
}
