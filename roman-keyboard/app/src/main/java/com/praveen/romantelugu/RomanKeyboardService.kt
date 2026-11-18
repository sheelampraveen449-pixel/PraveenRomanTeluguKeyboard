package com.praveen.romantelugu

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.*
import android.text.InputFilter
import android.text.InputType

class KeyboardSettingsActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var content: LinearLayout
    private lateinit var pageTitle: TextView
    private var currentPage = "home"

    private val dark: Boolean
        get() = prefs.getString("theme", "light") == "dark"

    private val bg: Int
        get() = if (dark) Color.rgb(19, 21, 27) else Color.rgb(247, 248, 252)

    private val surface: Int
        get() = if (dark) Color.rgb(36, 39, 47) else Color.WHITE

    private val primary: Int
        get() = if (dark) Color.WHITE else Color.rgb(28, 32, 43)

    private val secondary: Int
        get() = if (dark) Color.rgb(180, 185, 197) else Color.rgb(105, 111, 124)

    private val accent = Color.rgb(75, 105, 190)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("keyboard_prefs", MODE_PRIVATE)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.setBackgroundDrawable(ColorDrawable(bg))
        window.decorView.setBackgroundColor(bg)
        val requestedPage = intent.getStringExtra("open_page")
            ?.takeIf { it in setOf(
                "home", "languages", "preferences", "themes", "fonts", "search",
                "correction", "voice", "clipboard", "dictionary", "layout",
                "shortcuts", "glide", "media", "advanced", "privacy", "about"
            ) } ?: "home"
        renderPage(requestedPage, false)
    }

    private fun dp(value: Int) =
        (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int = 18): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun renderPage(page: String, animate: Boolean = true) {
        currentPage = page
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.setBackgroundDrawable(ColorDrawable(bg))
        window.decorView.setBackgroundColor(bg)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        val toolbar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(18), dp(10))
            minimumHeight = dp(64)
        }

        val back = TextView(this).apply {
            text = if (page == "home") "‹" else "‹"
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(primary)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            setOnClickListener {
                if (currentPage != "home") renderPage("home")
                else finish()
            }
        }
        toolbar.addView(back)

        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        pageTitle = TextView(this).apply {
            text = if (page == "home") "Settings" else pageName(page)
            textSize = 20f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(primary)
        }
        heading.addView(pageTitle)
        toolbar.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))

        root.addView(toolbar)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(dp(18), dp(4), dp(18), dp(28))
        }

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        when (page) {
            "home" -> buildHome()
            "languages" -> buildLanguages()
            "preferences" -> buildPreferences()
            "themes" -> buildThemes()
            "fonts" -> buildFonts()
            "search" -> buildSearch()
            "correction" -> buildCorrection()
            "voice" -> buildVoice()
            "clipboard" -> buildClipboard()
            "dictionary" -> buildDictionary()
            "layout" -> buildLayout()
            "shortcuts" -> buildTextShortcuts()
            "glide" -> buildGlide()
            "media" -> buildMedia()
            "advanced" -> buildAdvanced()
            "privacy" -> buildPrivacy()
            "about" -> buildAbout()
        }

        // Avoid a white frame during page changes in dark mode.
        // Replacing the content view directly keeps the selected theme visible.
        root.clearAnimation()
    }

    private fun pageName(page: String) = when (page) {
        "languages" -> "Languages"
        "preferences" -> "Preferences"
        "themes" -> "Theme"
        "fonts" -> "Font"
        "search" -> "Search"
        "correction" -> "Text correction"
        "voice" -> "Voice typing"
        "clipboard" -> "Clipboard"
        "dictionary" -> "Dictionary"
        "layout" -> "Keyboard layout"
        "shortcuts" -> "Text shortcuts"
        "glide" -> "Glide typing"
        "media" -> "Emojis, stickers & GIFs"
        "advanced" -> "Advanced settings"
        "privacy" -> "Privacy"
        "about" -> "About"
        else -> "Settings"
    }

    private fun section(title: String, description: String? = null) {
        val block = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(24), dp(4), dp(10))
        }
        block.addView(TextView(this).apply {
            text = title.uppercase()
            textSize = 13f
            letterSpacing = 0.035f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(secondary)
        })
        if (description != null) {
            block.addView(TextView(this).apply {
                text = description
                textSize = 13f
                setTextColor(secondary)
                setPadding(0, dp(5), 0, 0)
            })
        }
        content.addView(block)
    }

    private fun infoCard(title: String, description: String) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
        }
        card.addView(TextView(this).apply {
            text = title
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(primary)
        })
        card.addView(TextView(this).apply {
            text = description
            textSize = 13f
            setTextColor(secondary)
            setPadding(0, dp(5), 0, 0)
        })
        addCard(card)
    }

    private fun addCard(view: View) {
        val params = LinearLayout.LayoutParams(-1, -2)
        params.bottomMargin = dp(9)
        content.addView(view, params)
    }

    private fun navRow(
        icon: String,
        title: String,
        description: String,
        destination: String
    ) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(10), dp(8), dp(10))
            minimumHeight = dp(68)
            isClickable = true
            isFocusable = true
            background = android.util.TypedValue().let { value ->
                theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
                getDrawable(value.resourceId)
            }
            setOnClickListener { renderPage(destination) }
        }

        card.addView(TextView(this).apply {
            text = icon
            textSize = 22f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(48))
        })

        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(4), 0)
        }
        labels.addView(TextView(this).apply {
            text = title
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(primary)
        })
        labels.addView(TextView(this).apply {
            text = description
            textSize = 12f
            setTextColor(secondary)
            setPadding(0, dp(3), 0, 0)
        })
        card.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(TextView(this).apply {
            text = "›"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(secondary)
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(42))
        })
        val params = LinearLayout.LayoutParams(-1, -2)
        params.bottomMargin = dp(1)
        content.addView(card, params)
    }

    private fun switchRow(
        title: String,
        description: String,
        key: String,
        default: Boolean = false
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(13), dp(12), dp(13))
        }
        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        labels.addView(TextView(this).apply {
            text = title
            textSize = 15f
            setTextColor(primary)
            typeface = Typeface.DEFAULT_BOLD
        })
        labels.addView(TextView(this).apply {
            text = description
            textSize = 12f
            setTextColor(secondary)
            setPadding(0, dp(4), 0, 0)
        })
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Switch(this).apply {
            isChecked = prefs.getBoolean(key, default)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(key, checked).apply()
            }
        })
        addCard(row)
        content.addView(View(this).apply {
            setBackgroundColor(if (dark) Color.rgb(55, 59, 68) else Color.rgb(225, 228, 234))
        }, LinearLayout.LayoutParams(-1, dp(1)))
    }

    private fun buildHome() {
        section("PRAVEEN ROMAN TELUGU KEYBOARD", "Keyboard settings")
        section("PREFERENCES")
        navRow("☷", "Languages", "Roman Telugu and English input", "languages")
        navRow("⌨", "Preferences", "Number row, key behaviour and feedback", "preferences")
        navRow("▦", "Theme", "Keyboard colours and key shapes", "themes")
        navRow("▤", "Keyboard layout", "Key height and layout options", "layout")
        section("TEXT CORRECTION")
        navRow("✎", "Text correction", "Suggestions, next words and autocorrection", "correction")
        navRow("〰", "Glide typing", "Swipe typing and gestures", "glide")
        navRow("♩", "Voice typing", "Telugu speech input", "voice")
        navRow("Aa", "Personal dictionary", "Your saved words and shortcuts", "dictionary")
        section("TOOLS")
        navRow("▣", "Clipboard", "Copied text and pinned items", "clipboard")
        navRow("☺", "Emojis, stickers & GIFs", "Emoji support and media options", "media")
        navRow("⌕", "Search", "Search availability and options", "search")
        section("ADVANCED")
        navRow("▧", "Font", "Choose keyboard lettering", "fonts")
        navRow("⌘", "Text shortcuts", "Expand abbreviations into phrases", "shortcuts")
        navRow("♙", "Privacy", "Personal data and permissions", "privacy")
        navRow("⚙", "Advanced settings", "Additional keyboard controls", "advanced")
        navRow("ⓘ", "About", "App information and version", "about")
    }

    private fun buildLanguages() {
        section("ACTIVE LANGUAGE")
        infoCard("Roman Telugu", "Type Telugu using English letters. For example, nenu and meeru ela unnaru. This is the current keyboard mode.")
        infoCard("English QWERTY", "The keyboard uses the English letter layout for Roman Telugu typing.")
        infoCard("Voice input", "Telugu speech is recognized using the Telugu (India) speech-recognition locale and converted to Roman letters.")
        infoCard("Language availability", "Additional language layouts and downloadable language packs are not implemented yet.")
    }

    private fun buildPreferences() {
        section("KEY FEEDBACK")
        switchRow("Key sound", "Play a subtle sound when a key is tapped", "key_sound_enabled", false)
        switchRow("Vibration", "Short haptic feedback on key taps", "key_vibration_enabled", false)
        section("KEYBOARD")
        switchRow("Number row", "Show numbers above the letter keys", "number_row_enabled", true)
        switchRow("Emoji switch key", "Show an emoji shortcut where supported", "emoji_switch_key_enabled", true)
        switchRow("Language switch key", "Show the language switch key", "language_switch_key_enabled", true)
        switchRow("Voice input key", "Show the microphone shortcut", "voice_input_key_enabled", true)
        switchRow("Key borders", "Display borders around keys", "key_borders_enabled", false)
        infoCard("Long-press delay", "Long-press timing uses Android's standard touch behaviour in this build.")
        infoCard("Tip", "Keyboard height can be adjusted under Keyboard layout.")
    }

    private fun buildThemes() {
        section("KEYBOARD PREVIEW", "Changes are saved on this device.")
        themePreview()
        section("DEFAULT COLOURS")
        themeRow("Default", "light", "Classic light keyboard colours")
        themeRow("Dark", "dark", "Dark keys and dark background")
        themeRow("Blue", "blue", "Soft blue keys and background")
        section("KEY STYLE")
        switchRow("Key borders", "Show clear edges around individual keys", "key_borders_enabled", false)
        switchRow("Rounded keys", "Use rounded corners on keyboard keys", "rounded_keys_enabled", true)
        infoCard("More themes", "Custom photos, gradients and downloadable themes are not available yet. These options will be added without requiring internet access for the current offline themes.")
    }

    private fun buildFonts() {
        section("KEYBOARD FONT", "Choose how letters appear on the keyboard.")
        val selected = prefs.getString("keyboard_font", "sans-serif")
        listOf(
            Triple("System", "sans-serif", "Aa Bb Cc 123"),
            Triple("Rounded", "sans-serif-rounded", "Aa Bb Cc 123"),
            Triple("Serif", "serif", "Aa Bb Cc 123"),
            Triple("Monospace", "monospace", "Aa Bb Cc 123")
        ).forEach { (title, value, sample) ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(surface)
                setPadding(dp(16), dp(16), dp(16), dp(16))
            }
            val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(TextView(this).apply {
                text = title
                textSize = 16f
                setTextColor(primary)
                typeface = Typeface.create(value, Typeface.NORMAL)
            })
            labels.addView(TextView(this).apply {
                text = sample
                textSize = 18f
                setTextColor(secondary)
                typeface = Typeface.create(value, Typeface.NORMAL)
                setPadding(0, dp(4), 0, 0)
            })
            card.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(TextView(this).apply {
                text = if (selected == value) "✓" else "○"
                textSize = 22f
                setTextColor(accent)
            })
            card.setOnClickListener {
                prefs.edit().putString("keyboard_font", value).apply()
                renderPage("fonts", false)
            }
            addCard(card)
        }
        infoCard("Apply font", "Reopen the keyboard after selecting a font to see the change.")
    }

    private fun buildSearch() {
        section("SEARCH")
        infoCard("Keyboard search", "Integrated web and GIF search are not enabled in this offline-first version.")
        infoCard("Privacy", "The keyboard does not request internet access or send your typed text to a search service.")
    }

    private fun themePreview() {
        val selectedTheme = prefs.getString("theme", "light")
        val previewBg = when (selectedTheme) {
            "dark" -> Color.rgb(30, 33, 40)
            "blue" -> Color.rgb(190, 211, 239)
            else -> Color.rgb(211, 216, 224)
        }
        val previewKey = when (selectedTheme) {
            "dark" -> Color.rgb(55, 60, 69)
            "blue" -> Color.rgb(250, 252, 255)
            else -> Color.WHITE
        }
        val previewAction = when (selectedTheme) {
            "dark" -> Color.rgb(70, 75, 85)
            "blue" -> Color.rgb(190, 211, 239)
            else -> Color.rgb(205, 211, 220)
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(previewBg, 14)
            setPadding(dp(8), dp(10), dp(8), dp(10))
        }
        val suggestions = TextView(this).apply {
            text = "nenu                 meeru                 ela"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(if (selectedTheme == "dark") Color.WHITE else Color.rgb(55, 61, 70))
            setPadding(0, 0, 0, dp(7))
        }
        card.addView(suggestions)
        listOf("qwertyuiop", "asdfghjkl", "⇧ zxcvbnm ⌫", "?123       English       ↵").forEachIndexed { index, line ->
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER
                orientation = LinearLayout.HORIZONTAL
            }
            line.split(" ").filter { it.isNotEmpty() }.forEach { key ->
                val action = key in setOf("⇧", "⌫", "?123", "↵")
                row.addView(TextView(this).apply {
                    text = key
                    textSize = if (key.length > 1) 11f else 13f
                    gravity = Gravity.CENTER
                    setTextColor(if (selectedTheme == "dark") Color.WHITE else Color.rgb(32, 36, 43))
                    background = rounded(if (action) previewAction else previewKey, 5)
                    layoutParams = LinearLayout.LayoutParams(0, dp(31), 1f).apply {
                        setMargins(dp(2), dp(2), dp(2), dp(2))
                    }
                })
            }
            card.addView(row)
        }
        addCard(card)
    }

    private fun themeRow(title: String, value: String, description: String) {
        val selected = prefs.getString("theme", "light") == value
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(surface)
            setPadding(dp(16), dp(17), dp(16), dp(17))
        }
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(primary)
            typeface = Typeface.DEFAULT_BOLD
        })
        labels.addView(TextView(this).apply {
            text = description
            textSize = 12f
            setTextColor(secondary)
            setPadding(0, dp(4), 0, 0)
        })
        card.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(TextView(this).apply {
            text = if (selected) "✓" else "○"
            textSize = 23f
            setTextColor(accent)
        })
        card.setOnClickListener {
            prefs.edit().putString("theme", value).apply()
            renderPage("themes", false)
        }
        addCard(card)
    }

    private fun buildCorrection() {
        section("SUGGESTIONS")
        switchRow("Roman Telugu suggestions", "Show matching Roman Telugu words above the keyboard", "suggestions_enabled", true)
        switchRow("Contextual next-word suggestions", "Suggest likely words based on the word before the cursor", "next_word_enabled", true)
        switchRow("Personal dictionary suggestions", "Include words you add to your personal dictionary", "personal_dictionary_suggestions", true)
        switchRow("Basic automatic correction", "Correct a small set of common typing mistakes when you press space", "basic_autocorrect_enabled", false)
        infoCard("How suggestions work", "Suggestions combine the built-in offline Roman Telugu word list, your personal words and a small contextual next-word model. Tapping a suggestion replaces the current word.")
        infoCard("Feature status", "Basic Roman Telugu matching and contextual next-word suggestions are available. Editor-requested sentence capitalisation is supported. Full language-model prediction and broader spelling correction are still in development.")
        navRow("📖", "Personal dictionary", "Add and manage your own words", "dictionary")
    }

    private fun buildVoice() {
        section("TELUGU SPEECH")
        infoCard("Voice typing language", "Telugu (India) · te-IN")
        infoCard("How it works", "Press and hold the microphone key while speaking Telugu. The recognized Telugu text is converted into Roman letters.")
        val granted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        infoCard("Microphone permission", if (granted) "Permission is currently granted." else "Permission is not granted. Open the app setup screen and allow microphone access to use voice typing.")
        infoCard("Speech recognition", "Recognition is provided by the Android speech-recognition service. Availability can depend on the device and installed speech services.")
    }

    private fun buildClipboard() {
        section("CLIPBOARD HISTORY")
        switchRow("Enable clipboard history", "Save copied text locally while the keyboard is active", "clipboard_history_enabled", false)
        infoCard("On-device storage", "Clipboard history is stored locally by this app. Android may restrict background clipboard access.")
        val clear = actionButton("Clear all clipboard data")
        clear.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear clipboard history?")
                .setMessage("This deletes all saved entries, including pinned items.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear") { _, _ ->
                    ClipboardHistoryStore(this).clearAll()
                    Toast.makeText(this, "Clipboard history cleared", Toast.LENGTH_SHORT).show()
                }.show()
        }
        addCard(clear)
    }

    private fun buildDictionary() {
        section("PERSONAL WORDS", "Your own words are stored on this device.")
        val words = prefs.getStringSet("personal_dictionary", emptySet())
            ?.toSortedSet().orEmpty()
        if (words.isEmpty()) {
            infoCard("No personal words yet", "Add words you want to keep in your personal Roman Telugu dictionary.")
        } else {
            words.forEach { word ->
                val row = LinearLayout(this).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    background = rounded(surface)
                    setPadding(dp(16), dp(12), dp(12), dp(12))
                }
                row.addView(TextView(this).apply {
                    text = word
                    textSize = 16f
                    setTextColor(primary)
                }, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(TextView(this).apply {
                    text = "Remove"
                    textSize = 13f
                    setTextColor(accent)
                    setPadding(dp(10), dp(8), dp(8), dp(8))
                    setOnClickListener {
                        val updated = prefs.getStringSet("personal_dictionary", emptySet())
                            ?.toMutableSet() ?: mutableSetOf()
                        updated.remove(word)
                        prefs.edit().putStringSet("personal_dictionary", updated).apply()
                        renderPage("dictionary", false)
                    }
                })
                addCard(row)
            }
        }
        val add = actionButton("+  Add a word")
        add.setOnClickListener {
            val input = EditText(this).apply {
                hint = "Enter a Roman Telugu word"
                inputType = InputType.TYPE_CLASS_TEXT
                setSingleLine(true)
                filters = arrayOf(InputFilter.LengthFilter(40))
            }
            AlertDialog.Builder(this)
                .setTitle("Add personal word")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save") { _, _ ->
                    val word = input.text.toString().trim().lowercase()
                    if (word.isNotEmpty() && word.all { it.isLetter() }) {
                        val updated = prefs.getStringSet("personal_dictionary", emptySet())
                            ?.toMutableSet() ?: mutableSetOf()
                        updated.removeAll { it.equals(word, ignoreCase = true) }
                        updated.add(word)
                        prefs.edit().putStringSet("personal_dictionary", updated).apply()
                        renderPage("dictionary", false)
                    } else if (word.isNotEmpty()) {
                        Toast.makeText(this, "Use letters only", Toast.LENGTH_SHORT).show()
                    }
                }.show()
        }
        addCard(add)
        infoCard("How it works", "Your personal words are included in matching suggestions when Personal dictionary suggestions is enabled. They stay on this device and are not used for automatic correction.")
    }

    private fun buildTextShortcuts() {
        section("CUSTOM SHORTCUTS", "Type a short code followed by Space to insert its full phrase.")
        val shortcuts = prefs.getStringSet("text_shortcuts", emptySet())
            ?.toSortedSet().orEmpty()
        if (shortcuts.isEmpty()) {
            infoCard("No shortcuts yet", "Example: brb = be right back")
        } else {
            shortcuts.forEach { entry ->
                val row = LinearLayout(this).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    background = rounded(surface)
                    setPadding(dp(14), dp(12), dp(12), dp(12))
                }
                row.addView(TextView(this).apply {
                    text = entry.replaceFirst("=", "  →  ")
                    textSize = 14f
                    setTextColor(primary)
                }, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(TextView(this).apply {
                    text = "Remove"
                    setTextColor(accent)
                    setPadding(dp(8), dp(8), dp(4), dp(8))
                    setOnClickListener {
                        val updated = prefs.getStringSet("text_shortcuts", emptySet())
                            ?.toMutableSet() ?: mutableSetOf()
                        updated.remove(entry)
                        prefs.edit().putStringSet("text_shortcuts", updated).apply()
                        renderPage("shortcuts", false)
                    }
                })
                addCard(row)
            }
        }
        val add = actionButton("+  Add shortcut")
        add.setOnClickListener {
            val fields = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(4), dp(8), dp(4))
            }
            val trigger = EditText(this).apply {
                hint = "Shortcut (e.g. brb)"
                setSingleLine(true)
                filters = arrayOf(InputFilter.LengthFilter(20))
            }
            val expansion = EditText(this).apply {
                hint = "Full phrase (e.g. be right back)"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                minLines = 2
                filters = arrayOf(InputFilter.LengthFilter(160))
            }
            fields.addView(trigger)
            fields.addView(expansion)
            AlertDialog.Builder(this)
                .setTitle("Add text shortcut")
                .setView(fields)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save") { _, _ ->
                    val key = trigger.text.toString().trim().lowercase()
                    val phrase = expansion.text.toString().trim()
                    if (key.isNotBlank() && key.all { it.isLetterOrDigit() } &&
                        phrase.isNotBlank() && !phrase.contains("=")) {
                        val updated = prefs.getStringSet("text_shortcuts", emptySet())
                            ?.toMutableSet() ?: mutableSetOf()
                        updated.removeAll { it.substringBefore("=").equals(key, true) }
                        updated.add("$key=$phrase")
                        prefs.edit().putStringSet("text_shortcuts", updated).apply()
                        renderPage("shortcuts", false)
                    } else {
                        Toast.makeText(this, "Enter a valid shortcut and phrase", Toast.LENGTH_SHORT).show()
                    }
                }.show()
        }
        addCard(add)
        infoCard("Privacy", "Shortcuts are stored locally and are not uploaded. They are disabled in sensitive password fields.")
    }

    private fun buildLayout() {
        section("KEY SIZE")
        val current = prefs.getInt("key_height_dp", 42).coerceIn(38, 50)
        val valueLabel = TextView(this).apply {
            text = "Key height: ${current} dp"
            textSize = 15f
            setTextColor(primary)
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        addCard(valueLabel)
        val seek = SeekBar(this).apply {
            max = 12
            progress = current - 38
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val height = progress + 38
                    valueLabel.text = "Key height: $height dp"
                    prefs.edit().putInt("key_height_dp", height).apply()
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        }
        addCard(seek)
        section("LAYOUT")
        switchRow("Number row", "Display the number keys above letters", "number_row_enabled", true)
        infoCard("Apply changes", "Close and reopen the keyboard after changing its height or layout.")
    }

    private fun buildPrivacy() {
        section("YOUR DATA")
        infoCard("Roman Telugu typing", "Typed text is sent to the active app through Android's keyboard input connection.")
        infoCard("Clipboard", "Clipboard history is optional and stored locally. You can disable it or clear saved entries at any time.")
        infoCard("Voice typing", "Voice recognition uses the Android speech-recognition service. Speech processing may be handled by that service according to its own privacy policy.")
        infoCard("Password-field protection", "Suggestions, automatic correction, clipboard-history capture and voice dictation are disabled in standard password fields. The keyboard does not add typed password text to its personal dictionary.")
        infoCard("Permissions", "This app requests microphone permission for voice typing. It does not require overlay or accessibility permissions.")
    }

    private fun buildGlide() {
        section("GLIDE TYPING")
        infoCard("Offline swipe typing is available", "Slide your finger across the QWERTY letter keys without lifting it. The keyboard compares the traced letters with its built-in Roman Telugu word list and shows a candidate in the suggestion strip. Tap the candidate to insert it.")
        infoCard("Current limitations", "Swipe matching uses the built-in word list and is an initial implementation. It may not recognise every word or match Gboard accuracy. Password fields and fields that disable suggestions do not offer swipe predictions.")
    }

    private fun buildMedia() {
        section("EMOJIS")
        infoCard("Emoji picker", "The keyboard currently includes a basic emoji picker and remembers recently used emoji on this device.")
        section("STICKERS & GIFS")
        infoCard("Not available yet", "Sticker and GIF search requires a media catalogue and network access. It is not enabled, and this keyboard does not request internet permission.")
    }

    private fun buildAdvanced() {
        section("KEYBOARD CONTROLS")
        switchRow("Automatic sentence capitalisation", "Start sentences with a capital letter where the editor requests it", "auto_capitalization_enabled", true)
        switchRow("Number row", "Keep a dedicated number row above letters", "number_row_enabled", true)
        switchRow("Show word suggestions", "Display Roman Telugu suggestions above the keys", "suggestions_enabled", true)
        section("PRIVACY")
        infoCard("Local-first design", "Personal dictionary and clipboard history are stored locally. Voice recognition is handled by the Android speech service when you use the microphone.")
        section("FEATURE STATUS")
        infoCard("Advanced features in development", "Downloadable language packs, cloud sync and sticker/GIF search are not currently active. Offline swipe typing has an initial implementation and needs device testing.")
    }

    private fun buildAbout() {
        section("ROMAN TELUGU KEYBOARD")
        infoCard("Version 1.0", "A Roman Telugu keyboard with offline word suggestions and Telugu voice-to-Roman typing.")
        infoCard("Built for", "Fast Roman Telugu typing using a familiar English QWERTY layout.")
        infoCard("Project status", "This is an actively developed build. Offline swipe typing has an initial implementation; additional language packs, cloud sync and sticker/GIF search are not available yet.")
    }

    private fun actionButton(label: String): Button =
        Button(this).apply {
            text = label
            textSize = 14f
            isAllCaps = false
        }

    override fun onBackPressed() {
        if (currentPage != "home") renderPage("home")
        else super.onBackPressed()
    }
}
