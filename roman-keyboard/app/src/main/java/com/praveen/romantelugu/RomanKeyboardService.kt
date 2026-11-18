package com.praveen.romantelugu

import android.inputmethodservice.InputMethodService
import android.Manifest
import android.content.Intent
import android.app.AlertDialog
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Vibrator
import android.os.VibrationEffect
import android.graphics.drawable.GradientDrawable

class RomanKeyboardService : InputMethodService() {

    private var recognizer: SpeechRecognizer? = null
    private var keyboardRoot: LinearLayout? = null
    private var symbolsMode = false
    private var symbolsPageTwo = false
    private var shifted = false
    private var capsLock = false
    private var lastShiftTap = 0L
    private var lastSpaceTapAt = 0L
    private lateinit var clipboardHistory: ClipboardHistoryStore
    private var clipboardManager: ClipboardManager? = null
    private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var showingClipboardHistory = false
    private var showingToolsPanel = false
    private var suggestionRow: LinearLayout? = null
    private var toneGenerator: ToneGenerator? = null
    private val swipePath = StringBuilder()
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var swipeMoved = false
    private var pendingSwipeWord: String? = null
    private var englishAlMode = false

    private val romanTeluguWords = listOf(
        "nenu", "naaku", "naatho", "nuvvu", "neeku", "neetho",
        "meeru", "meeku", "manam", "mana", "memu", "maaku",
        "vallu", "vaallu", "ela", "elaga", "emiti", "enti",
        "enduku", "eppudu", "ekkada", "evaru", "ippudu",
        "tarvata", "mundhu", "akkada", "ikkada", "avunu",
        "kaadu", "sare", "ledu", "undi", "unnanu", "unnaru",
        "bagunnanu", "bagunnara", "bagundi", "chala", "manchi",
        "kavali", "vaddu", "chesanu", "chestunna", "vastanu",
        "vellanu", "tinnava", "tinanu", "cheppu", "cheppandi",
        "ledu", "amma", "nanna", "anna", "akka", "thammudu",
        "friend", "intiki", "office", "pani", "ippude",
        "repu", "ninna", "roju", "randi", "please", "thanks",
        "meeru", "nenu", "nuvvu", "manam", "meeru", "vaallu",
        "chustunna", "chusanu", "chuddam", "chudali", "vachindi",
        "vastunnanu", "vastunnaru", "vellali", "vellandi", "vachanu",
        "matladali", "matladudam", "matladutunna", "cheppanu",
        "cheppali", "cheppandi", "telusu", "teliyadu", "ardham",
        "ardhamayindi", "ardhamkaledu", "anukuntunna", "anukunnanu",
        "nachindi", "nachaledu", "ishtam", "ishtamledu", "parledu",
        "problem", "help", "message", "call", "phone", "number",
        "ekkadunnavu", "ekkadunnaru", "eppudostavu", "eppudostaru",
        "vastava", "vastara", "veltava", "veltara", "chesava",
        "chesara", "tinnara", "tintunnava", "paduko", "lechu",
        "nidra", "aakali", "neellu", "tindi", "illu", "intlo",
        "bayata", "lopala", "mundhu", "venaka", "pakkana",
        "ivala", "eroju", "ninna", "monna", "repatiki",
        "udayam", "madhyanam", "sayamtram", "ratri", "podhuna",
        "ippatiki", "ventane", "konchem", "chala", "inka",
        "malli", "maatram", "kuda", "aithe", "kani", "endukante",
        "avakasham", "santosham", "kastam", "bagaledu", "super",
        "awesome", "okay", "sarey", "dhanyavadalu", "shubharatri",
        "subhodayam", "namaskaram", "welcome", "sorry", "congrats"
    )

    // Offline dictionaries are bundled with the APK and never require network access.
    private val offlineWords: List<String> by lazy {
        loadDictionaryAsset("roman_telugu_words.txt")
    }

    private val offlinePhrases: List<String> by lazy {
        loadDictionaryAsset("roman_telugu_phrases.txt")
    }

    private fun loadDictionaryAsset(fileName: String): List<String> {
        return try {
            assets.open(fileName).bufferedReader().useLines { lines ->
                lines.map { it.trim().lowercase() }
                    .filter { it.matches(Regex("[a-z]+(?: [a-z]+)*")) }
                    .distinct()
                    .toList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun onCreate() {
        super.onCreate()
        clipboardHistory = ClipboardHistoryStore(this)

        clipboardManager =
            getSystemService(CLIPBOARD_SERVICE) as ClipboardManager

        clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
            captureClipboardText()
        }
    }

    // Reset transient UI state when switching to a new editor or app.
    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        symbolsMode = false
        symbolsPageTwo = false
        englishAlMode = getSharedPreferences("keyboard_prefs", MODE_PRIVATE)
            .getBoolean("english_al_mode", false)
        shifted = false
        capsLock = false
        lastShiftTap = 0L
        lastSpaceTapAt = 0L
        showingClipboardHistory = false
        showingToolsPanel = false
        pendingSwipeWord = null
        swipePath.clear()
        swipeMoved = false
        // Clear candidates from the previous editor before the new input view refreshes.
        suggestionRow?.removeAllViews()
    }

    override fun onStartInputView(
        info: android.view.inputmethod.EditorInfo,
        restarting: Boolean
    ) {
        super.onStartInputView(info, restarting)
        clipboardListener?.let {
            clipboardManager?.removePrimaryClipChangedListener(it)
            clipboardManager?.addPrimaryClipChangedListener(it)
        }
        updateAutoCapitalization()
        if (keyboardRoot != null) renderKeyboard()
    }

    // Keep password content out of suggestions, correction, clipboard history and dictation.
    private fun isSensitiveInput(): Boolean {
        val inputType = currentInputEditorInfo?.inputType ?: return false
        val inputClass = inputType and EditorInfo.TYPE_MASK_CLASS
        val variation = inputType and EditorInfo.TYPE_MASK_VARIATION
        return when (inputClass) {
            EditorInfo.TYPE_CLASS_TEXT -> variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD
            EditorInfo.TYPE_CLASS_NUMBER -> variation == EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    private fun updateAutoCapitalization() {
        val prefs = getSharedPreferences("keyboard_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("auto_capitalization_enabled", true) || symbolsMode || capsLock) return

        val inputType = currentInputEditorInfo?.inputType ?: return
        if ((inputType and EditorInfo.TYPE_MASK_CLASS) != EditorInfo.TYPE_CLASS_TEXT) return

        val requestsSentenceCaps =
            (inputType and EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0
        val requestsWordCaps =
            (inputType and EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS) != 0
        val requestsAllCaps =
            (inputType and EditorInfo.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0
        if (!requestsSentenceCaps && !requestsWordCaps && !requestsAllCaps) return

        val before = currentInputConnection?.getTextBeforeCursor(2, 0)?.toString().orEmpty()
        val shouldShift = when {
            requestsAllCaps -> true
            requestsWordCaps -> before.isEmpty() || before.lastOrNull()?.isWhitespace() == true
            else -> before.isEmpty() || before.lastOrNull()?.let { it in setOf('.', '!', '?', '\n') } == true
        }
        if (shifted != shouldShift) {
            shifted = shouldShift
            keyboardRoot?.let { renderKeyboard() }
        }
    }

    // Keep suggestions in sync when the cursor or selection moves in the editor.
    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd,
            candidatesStart, candidatesEnd
        )
        if (newSelStart == newSelEnd) {
            updateSuggestionStrip()
        } else {
            // Do not show stale word predictions while a range is selected.
            suggestionRow?.removeAllViews()
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        clipboardListener?.let {
            clipboardManager?.removePrimaryClipChangedListener(it)
        }
        super.onFinishInputView(finishingInput)
    }

    private fun captureClipboardText() {
        if (isSensitiveInput()) return
        if (!getSharedPreferences("keyboard_prefs", MODE_PRIVATE)
                .getBoolean("clipboard_history_enabled", false)) {
            return
        }

        try {
            val text = clipboardManager?.primaryClip
                ?.getItemAt(0)
                ?.coerceToText(this)
                ?.toString()

            if (!text.isNullOrBlank()) {
                clipboardHistory.add(text)
            }
        } catch (_: Exception) {
            // Clipboard may be unavailable or restricted by Android.
        }
    }

    override fun onCreateInputView(): View {
        keyboardRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(5))
            setBackgroundColor(
                when (getSharedPreferences("keyboard_prefs", MODE_PRIVATE)
                    .getString("theme", "light")) {
                    "dark" -> Color.rgb(35, 38, 44)
                    "blue" -> Color.rgb(205, 224, 250)
                    else -> Color.rgb(211, 216, 224)
                }
            )
        }
        renderKeyboard()
        return keyboardRoot!!
    }

    private fun renderKeyboard() {
        val root = keyboardRoot ?: return
        root.removeAllViews()

        val preferences = getSharedPreferences("keyboard_prefs", MODE_PRIVATE)
        val selectedTheme = preferences.getString("theme", "light")
        root.setPadding(dp(4), dp(4), dp(4), dp(5))
        // Honour the keyboard-height preference by reserving enough vertical
        // space for every weighted key row. Without this minimum, Android can
        // compress the rows and make the bottom row overlap or become too small.
        val configuredKeyHeight = preferences.getInt("key_height_dp", 42).coerceIn(38, 50)
        val hasNumberRow = preferences.getBoolean("number_row_enabled", true)
        val hasSuggestions = preferences.getBoolean("suggestions_enabled", true) && !isSensitiveInput()
        val visibleKeyRows = 4 + if (!symbolsMode && hasNumberRow) 1 else 0
        val requiredKeyboardHeight = 44 +
            (if (showingToolsPanel) 42 else 0) +
            (if (hasSuggestions) 38 else 0) +
            configuredKeyHeight * visibleKeyRows + 12
        root.minimumHeight = dp(requiredKeyboardHeight)
        root.setBackgroundColor(
            when (selectedTheme) {
                "dark" -> Color.rgb(30, 33, 39)
                "blue" -> Color.rgb(207, 224, 247)
                else -> Color.rgb(211, 216, 224)
            }
        )

        // Compact, full-width toolbar. Language switching and keyboard hiding
        // live on the bottom row so they cannot collide with toolbar controls.
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        if (!isSensitiveInput()) {
            toolbar.addView(toolButton("☷", 0.75f) {
            showingToolsPanel = !showingToolsPanel
            renderKeyboard()
        })
        toolbar.addView(toolButton("▤", 0.9f) {
                showingClipboardHistory = !showingClipboardHistory
                renderKeyboard()
            })
        } else {
            showingClipboardHistory = false
        }
        if (preferences.getBoolean("emoji_switch_key_enabled", true)) {
            toolbar.addView(toolButton("☺", 0.8f) { showEmojiPicker() })
        }

        val mic = toolButton("♩", 1.1f) {}
        mic.contentDescription = if (isSensitiveInput()) "Voice typing unavailable in password fields" else "Hold to dictate in Telugu"
        mic.isEnabled = !isSensitiveInput()
        mic.alpha = if (isSensitiveInput()) 0.45f else 1f
        mic.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    mic.text = "Listening"
                    startVoice()
                    true
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    mic.text = "♩"
                    stopVoice()
                    true
                }
                else -> true
            }
        }
        if (preferences.getBoolean("voice_input_key_enabled", true)) {
            toolbar.addView(mic)
        }
        toolbar.addView(toolButton("⚙", 0.8f) {
            val settingsIntent = Intent(this, KeyboardSettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(settingsIntent)
        })
        toolbar.addView(toolButton("✎", 0.8f) { showEditMenu() })
        root.addView(
            toolbar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(46)
            )
        )

        if (showingToolsPanel) {
            val toolsScroll = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
            }
            val toolsRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            toolsRow.addView(toolButton("Clipboard", 1f) {
                showingToolsPanel = false
                showingClipboardHistory = true
                renderKeyboard()
            }, LinearLayout.LayoutParams(dp(104), dp(38)))
            toolsRow.addView(toolButton("Emoji", 1f) { showEmojiPicker() }, LinearLayout.LayoutParams(dp(90), dp(38)))
            toolsRow.addView(toolButton("Text Edit", 1f) { showEditMenu() }, LinearLayout.LayoutParams(dp(104), dp(38)))
            toolsRow.addView(toolButton("Theme", 1f) {
                openSettingsPage("themes")
            }, LinearLayout.LayoutParams(dp(88), dp(38)))
            toolsRow.addView(toolButton("Resize", 1f) {
                openSettingsPage("layout")
            }, LinearLayout.LayoutParams(dp(88), dp(38)))
            toolsRow.addView(toolButton("Settings", 1f) {
                openSettingsPage("home")
            }, LinearLayout.LayoutParams(dp(100), dp(38)))
            toolsRow.addView(toolButton("Close", 1f) {
                showingToolsPanel = false
                renderKeyboard()
            }, LinearLayout.LayoutParams(dp(76), dp(38)))
            toolsScroll.addView(toolsRow)
            root.addView(toolsScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)))
        }

        if (showingClipboardHistory) {
            addClipboardHistoryPanel(root)
            return
        }

        val editorInputType = currentInputEditorInfo?.inputType ?: 0
        val editorDisablesSuggestions =
            (editorInputType and EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
        if (preferences.getBoolean("suggestions_enabled", true) &&
            !isSensitiveInput() && !editorDisablesSuggestions) {
            addSuggestionStrip(root)
        } else {
            suggestionRow = null
        }
        // Gboard-style symbol pages already contain their own digit row;
        // never render the optional alphabet number row on top of them.
        if (!symbolsMode && preferences.getBoolean("number_row_enabled", true)) {
            addRow(root, "1234567890", 1f)
        }

        if (!symbolsMode) {
            addRow(root, "qwertyuiop", 1f)
            addRow(root, "asdfghjkl", 1f)

            val third = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            third.addView(key(if (capsLock) "⇪" else "⇧", 1.25f) {
                val now = System.currentTimeMillis()
                if (now - lastShiftTap < 500) {
                    capsLock = !capsLock
                    shifted = capsLock
                } else {
                    shifted = !shifted
                }
                lastShiftTap = now
                renderKeyboard()
            })

            "zxcvbnm".forEach { c ->
                val shown = if (shifted || capsLock) c.uppercaseChar() else c
                val alternatives = mapOf(
                    'z' to "1", 'x' to "2", 'c' to "3",
                    'v' to "4", 'b' to "5", 'n' to "6", 'm' to "7"
                )
                third.addView(key(shown.toString(), 1f, {
                    currentInputConnection?.commitText(shown.toString(), 1)
                    updateSuggestionStrip()
                    if (shifted && !capsLock) {
                        shifted = false
                        renderKeyboard()
                    } else {
                        updateAutoCapitalization()
                    }
                }, {
                    currentInputConnection?.commitText(alternatives[c].orEmpty(), 1)
                }))
            }

            val backspace = key("⌫", 1.25f) {}
            var repeatingDelete = false
            var deleteWordOnSwipe = false
            var backspaceDownX = 0f
            val repeatDelete = object : Runnable {
                override fun run() {
                    if (repeatingDelete && !deleteWordOnSwipe) {
                        deleteBackward()
                        backspace.postDelayed(this, 65)
                    }
                }
            }
            backspace.contentDescription = "Backspace. Hold to delete continuously; swipe left to delete the previous word"
            backspace.setOnTouchListener { _, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        performKeyFeedback()
                        backspaceDownX = event.rawX
                        deleteWordOnSwipe = false
                        repeatingDelete = true
                        deleteBackward()
                        backspace.postDelayed(repeatDelete, 400)
                        true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        if (!deleteWordOnSwipe && backspaceDownX - event.rawX > dp(32)) {
                            deleteWordOnSwipe = true
                            repeatingDelete = false
                            backspace.removeCallbacks(repeatDelete)
                            val before = currentInputConnection?.getTextBeforeCursor(256, 0)?.toString().orEmpty()
                            val previousWord = before.takeLastWhile { !it.isWhitespace() }
                            val count = previousWord.codePointCount(0, previousWord.length)
                            if (count > 0) {
                                currentInputConnection?.deleteSurroundingTextInCodePoints(count, 0)
                                updateSuggestionStrip()
                                updateAutoCapitalization()
                            }
                        }
                        true
                    }
                    android.view.MotionEvent.ACTION_UP,
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        repeatingDelete = false
                        backspace.removeCallbacks(repeatDelete)
                        true
                    }
                    else -> true
                }
            }
            third.addView(backspace)
            root.addView(
                third,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
        } else if (!symbolsPageTwo) {
            // Gboard-like first symbols page: one digit row followed by two
            // punctuation rows. The alphabet number row is intentionally hidden.
            addRow(root, "1234567890", 1f)
            addRow(root, "@#$%&-*+()", 1f)
            addRow(root, "=/:;!?'\"", 1f)
        } else {
            addRow(root, "~`|•√π÷×¶∆", 1f)
            addRow(root, "£¢€¥^°={}\\", 1f)
            addRow(root, "%©®™✓[]<>", 1f)
        }

        // Standard bottom row: language switch at left, hide at right,
        // with a wide unlabeled spacebar in the centre.
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        bottom.addView(key(if (symbolsMode) "ABC" else "?123", 0.9f) {
            symbolsMode = !symbolsMode
            symbolsPageTwo = false
            shifted = false
            renderKeyboard()
        })
        if (symbolsMode) {
            bottom.addView(key(if (symbolsPageTwo) "123" else "!?#", 0.8f) {
                symbolsPageTwo = !symbolsPageTwo
                renderKeyboard()
            })
        } else {
            bottom.addView(key(",", 0.5f) {
                currentInputConnection?.commitText(",", 1)
                updateSuggestionStrip()
            })
        }
        val spaceLabel = if (englishAlMode) "English (AL)" else "Roman Telugu"
        val space = key(spaceLabel, if (symbolsMode) 2.8f else 3.4f) {
            commitSpaceWithOptionalCorrection()
            updateSuggestionStrip()
        }
        space.contentDescription = "Space. Swipe left or right to move the text cursor"
        var cursorGestureMoved = false
        var cursorGestureLastX = 0f
        var cursorGestureRemainder = 0f
        space.setOnTouchListener { view, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    cursorGestureMoved = false
                    cursorGestureLastX = event.rawX
                    cursorGestureRemainder = 0f
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val delta = event.rawX - cursorGestureLastX
                    cursorGestureLastX = event.rawX
                    cursorGestureRemainder += delta
                    val step = dp(12).coerceAtLeast(1)
                    val moves = (cursorGestureRemainder / step).toInt()
                    if (moves != 0) {
                        val extracted = currentInputConnection?.getExtractedText(
                            android.view.inputmethod.ExtractedTextRequest(), 0
                        )
                        if (extracted != null && extracted.selectionStart >= 0) {
                            val target = (extracted.selectionStart + moves)
                                .coerceIn(0, extracted.text?.length ?: extracted.selectionStart)
                            currentInputConnection?.setSelection(target, target)
                            cursorGestureMoved = true
                            cursorGestureRemainder -= moves * step
                        }
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    if (!cursorGestureMoved) view.performClick()
                    true
                }
                android.view.MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }
        bottom.addView(space)
        if (symbolsMode) {
            bottom.addView(key("=", 0.65f) { currentInputConnection?.commitText("=", 1) })
        } else {
            bottom.addView(key(".", 0.5f) {
                currentInputConnection?.commitText(".", 1)
                updateSuggestionStrip()
            })
        }
        bottom.addView(key(enterKeyLabel(), 0.95f) { performEnterAction() })
        if (preferences.getBoolean("language_switch_key_enabled", true)) {
            bottom.addView(key("🌐", 0.7f, {
                // Switch the keyboard's own input profile without opening
                // Android's system IME picker on every tap.
                englishAlMode = !englishAlMode
                preferences.edit().putBoolean("english_al_mode", englishAlMode).apply()
                renderKeyboard()
            }, {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showInputMethodPicker()
            }))
        }
        val hide = key("⌄", 0.6f) { requestHideSelf(0) }
        hide.contentDescription = "Hide keyboard"
        bottom.addView(hide)
        root.addView(
            bottom,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
    }

    private fun openSettingsPage(page: String) {
        val intent = Intent(this, KeyboardSettingsActivity::class.java)
            .putExtra("open_page", page)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun toolButton(
        label: String,
        weight: Float,
        action: () -> Unit
    ): TextView {
        val theme = getSharedPreferences("keyboard_prefs", MODE_PRIVATE)
            .getString("theme", "light")
        val descriptions = mapOf(
            "☷" to "Keyboard tools",
            "▤" to "Clipboard",
            "☺" to "Emoji",
            "♩" to "Voice typing",
            "⚙" to "Settings",
            "✎" to "Text editing",
            "Listening" to "Listening"
        )
        return TextView(this).apply {
            text = label
            textSize = if (label == "Listening") 12f else 23f
            gravity = Gravity.CENTER
            contentDescription = descriptions[label] ?: label
            setTextColor(if (theme == "dark") Color.rgb(220, 224, 232) else Color.rgb(83, 88, 98))
            background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            val params = LinearLayout.LayoutParams(0, dp(44), weight)
            params.setMargins(dp(1), 0, dp(1), 0)
            layoutParams = params
            isFocusable = true
            setOnClickListener {
                performKeyFeedback()
                action()
            }
        }
    }

    private fun commitSpaceWithOptionalCorrection() {
        val connection = currentInputConnection ?: return
        if (isSensitiveInput()) {
            connection.commitText(" ", 1)
            lastSpaceTapAt = 0L
            updateSuggestionStrip()
            return
        }

        // Match the familiar double-space full stop shortcut.
        val now = android.os.SystemClock.uptimeMillis()
        val beforeForPunctuation = connection.getTextBeforeCursor(2, 0)?.toString().orEmpty()
        if (now - lastSpaceTapAt in 1..350 && beforeForPunctuation.endsWith(" ") &&
            beforeForPunctuation.dropLast(1).lastOrNull()?.isWhitespace() == false) {
            connection.deleteSurroundingTextInCodePoints(1, 0)
            connection.commitText(". ", 1)
            lastSpaceTapAt = 0L
            updateSuggestionStrip()
            updateAutoCapitalization()
            return
        }
        lastSpaceTapAt = now
        val prefs = getSharedPreferences("keyboard_prefs", MODE_PRIVATE)
        val before = connection.getTextBeforeCursor(48, 0)?.toString().orEmpty()
        val word = before.takeLastWhile { it.isLetter() }
        val corrections = if (englishAlMode) {
            mapOf("teh" to "the", "adn" to "and", "dont" to "don't", "cant" to "can't", "im" to "I'm")
        } else {
            mapOf("nuvuu" to "nuvvu", "bagunanu" to "bagunnanu", "bagunara" to "bagunnara")
        }
        val personalWordSet = prefs.getStringSet("personal_dictionary", emptySet())
            ?.map { it.lowercase() }?.toSet().orEmpty()
        val shortcuts = prefs.getStringSet("text_shortcuts", emptySet()).orEmpty()
            .mapNotNull { entry ->
                val separator = entry.indexOf('=')
                if (separator <= 0 || separator == entry.lastIndex) null
                else entry.substring(0, separator).trim().lowercase() to
                    entry.substring(separator + 1).trim()
            }.toMap()
        val expansion = shortcuts[word.lowercase()]
        val corrected = if (
            expansion == null &&
            prefs.getBoolean("basic_autocorrect_enabled", false) &&
            word.lowercase() !in personalWordSet
        ) {
            corrections[word.lowercase()]
        } else null

        if (expansion != null && word.isNotEmpty()) {
            connection.deleteSurroundingTextInCodePoints(
                word.codePointCount(0, word.length), 0
            )
            connection.commitText("$expansion ", 1)
        } else if (corrected != null && word.isNotEmpty()) {
            connection.deleteSurroundingTextInCodePoints(
                word.codePointCount(0, word.length), 0
            )
            val replacement = if (word.first().isUpperCase()) {
                corrected.replaceFirstChar { it.uppercase() }
            } else corrected
            connection.commitText("$replacement ", 1)
        } else {
            connection.commitText(" ", 1)
        }
        updateSuggestionStrip()
    }

    private fun deleteBackward() {
        val connection = currentInputConnection ?: return
        val selected = connection.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            connection.commitText("", 1)
        } else {
            connection.deleteSurroundingTextInCodePoints(1, 0)
        }
        updateSuggestionStrip()
        // Re-evaluate sentence/word capitalisation after deleting text.
        updateAutoCapitalization()
    }

    private fun enterKeyLabel(): String {
        val action = currentInputEditorInfo?.imeOptions
            ?.and(EditorInfo.IME_MASK_ACTION)
            ?: EditorInfo.IME_ACTION_NONE

        return when (action) {
            EditorInfo.IME_ACTION_GO -> "Go"
            EditorInfo.IME_ACTION_SEARCH -> "Search"
            EditorInfo.IME_ACTION_SEND -> "Send"
            EditorInfo.IME_ACTION_NEXT -> "Next"
            EditorInfo.IME_ACTION_DONE -> "Done"
            else -> "↵"
        }
    }

    private fun performEnterAction() {
        val connection = currentInputConnection ?: return
        val action = currentInputEditorInfo?.imeOptions
            ?.and(EditorInfo.IME_MASK_ACTION)
            ?: EditorInfo.IME_ACTION_NONE

        val hasEditorAction =
            action != EditorInfo.IME_ACTION_NONE &&
            action != EditorInfo.IME_ACTION_UNSPECIFIED

        if (hasEditorAction) {
            if (!connection.performEditorAction(action)) {
                connection.commitText("\n", 1)
            }
        } else {
            connection.commitText("\n", 1)
        }
    }

    private fun showEmojiPicker() {
        val emojiGroups = listOf(
            "😀", "😃", "😄", "😁", "😆", "😅",
            "😂", "🤣", "😊", "😇", "🙂", "🙃",
            "😉", "😍", "🥰", "😘", "😋", "😎",
            "🤔", "🤗", "🤩", "😴", "😭", "🥺",
            "😡", "🙏", "👏", "👍", "👎", "👌",
            "❤️", "💕", "💖", "🔥", "🎉", "✨",
            "💯", "🙌", "🤝", "💐", "🌹", "🎂",
            "🍕", "🍔", "🍟", "🍫", "☕", "🥤",
            "⚽", "🏏", "🎮", "🚗", "✈️", "🏠"
        )
        val preferences = getSharedPreferences("keyboard_prefs", MODE_PRIVATE)
        val recentEmojis = preferences.getString("recent_emojis", "")
            .orEmpty().split("|").filter { it.isNotBlank() }
        val orderedEmojis = (recentEmojis + emojiGroups).distinct()

        val dialog = AlertDialog.Builder(this)
            .setTitle("Emoji")
            .setNegativeButton("Close", null)
            .create()

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        val search = EditText(this).apply {
            hint = "Search emoji"
            setSingleLine(true)
            textSize = 15f
            contentDescription = "Search emojis by keyword or emoji"
        }
        panel.addView(search, LinearLayout.LayoutParams(-1, dp(48)))
        val scroll = ScrollView(this)
        val grid = android.widget.GridLayout(this).apply {
            columnCount = 6
            setPadding(dp(2), dp(6), dp(2), dp(6))
        }
        val emojiCells = mutableListOf<Pair<String, TextView>>()
        orderedEmojis.forEach { emoji ->
            val cell = TextView(this).apply {
                text = emoji
                textSize = 27f
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                layoutParams = android.widget.GridLayout.LayoutParams().apply {
                    width = dp(52)
                    height = dp(52)
                    setMargins(dp(2), dp(2), dp(2), dp(2))
                }
                setOnClickListener {
                    currentInputConnection?.commitText(emoji, 1)
                    val prefs = getSharedPreferences("keyboard_prefs", MODE_PRIVATE)
                    val recent = prefs.getString("recent_emojis", "")
                        .orEmpty().split("|").filter { it.isNotBlank() }
                        .toMutableList()
                    recent.remove(emoji)
                    recent.add(0, emoji)
                    prefs.edit().putString("recent_emojis", recent.take(12).joinToString("|")).apply()
                    updateSuggestionStrip()
                    dialog.dismiss()
                }
            }
            grid.addView(cell)
            emojiCells.add(emoji to cell)
        }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                val query = text?.toString()?.trim().orEmpty()
                emojiCells.forEach { (emoji, cell) ->
                    cell.visibility = if (query.isBlank() || emoji.contains(query, true)) View.VISIBLE else View.GONE
                }
            }
            override fun afterTextChanged(text: android.text.Editable?) {}
        })
        scroll.addView(grid)
        panel.addView(scroll, LinearLayout.LayoutParams(-1, dp(360)))
        dialog.setView(panel)
        dialog.show()
    }

    private fun showEditMenu() {
        val actions = arrayOf("Cut", "Copy", "Paste", "Select all")
        val actionIds = intArrayOf(
            android.R.id.cut,
            android.R.id.copy,
            android.R.id.paste,
            android.R.id.selectAll
        )

        AlertDialog.Builder(this)
            .setTitle("Text editing")
            .setItems(actions) { _, which ->
                currentInputConnection?.performContextMenuAction(
                    actionIds[which]
                )
            }
            .show()
    }

    private fun addSuggestionStrip(root: LinearLayout) {
        suggestionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(5), 0, dp(5), 0)
        }
        root.addView(
            suggestionRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(34)
            )
        )
        updateSuggestionStrip()
    }

    private fun updateSuggestionStrip() {
        val row = suggestionRow ?: return
        row.removeAllViews()
        val inputType = currentInputEditorInfo?.inputType ?: 0
        if (isSensitiveInput() ||
            (inputType and EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) return

        // English (AL) is a separate profile: do not show Roman Telugu
        // dictionary, phrase, or next-word candidates in this mode.
        if (englishAlMode) {
            pendingSwipeWord = null
            val darkTheme = getSharedPreferences("keyboard_prefs", MODE_PRIVATE)
                .getString("theme", "light") == "dark"
            row.addView(TextView(this).apply {
                text = "English (AL)"
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(if (darkTheme) Color.LTGRAY else Color.GRAY)
                layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f)
            })
            return
        }

        val beforeCursor = currentInputConnection
            ?.getTextBeforeCursor(80, 0)?.toString().orEmpty()
        val prefix = beforeCursor.takeLastWhile { it.isLetter() }.lowercase()
        val previousWord = beforeCursor.dropLast(prefix.length)
            .trimEnd().takeLastWhile { it.isLetter() }.lowercase()

        val prefs = getSharedPreferences("keyboard_prefs", MODE_PRIVATE)
        // Copy and normalize saved words before ranking suggestions.
        val personalWords = if (prefs.getBoolean("personal_dictionary_suggestions", true)) {
            prefs.getStringSet("personal_dictionary", emptySet())
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.distinctBy { it.lowercase() }
                ?.sortedBy { it.lowercase() }
                .orEmpty()
        } else emptyList()
        val nextWords = mapOf(
            "nenu" to listOf("bagunnanu", "vastanu", "cheptanu"),
            "meeru" to listOf("ela", "ekkada", "vastunnara"),
            "ela" to listOf("unnaru", "undi", "undhi"),
            "naaku" to listOf("kavali", "telusu", "ardham"),
            "em" to listOf("chesavu", "jarigindi", "kavali"),
            "repu" to listOf("vastanu", "kaluddam", "matladuddam"),
            "ippudu" to listOf("cheppu", "randi", "chuddam"),
            "nuvvu" to listOf("ekkada", "em", "vastava"),
            "naaku" to listOf("telusu", "ardham", "ishtam"),
            "meeru" to listOf("ekkadunnaru", "vastara", "cheppandi"),
            "bagunnanu" to listOf("meeru", "kuda", "chala"),
            "bagunnara" to listOf("meeru", "ani", "cheppandi"),
            "emiti" to listOf("jarigindi", "sangathi", "matter"),
            "enti" to listOf("sangathi", "problem", "matter"),
            "ekkada" to listOf("unnavu", "unnaru", "ki vellavu"),
            "eppudu" to listOf("vastavu", "vastaru", "kaluddam"),
            "enduku" to listOf("ala", "cheppavu", "jarigindi"),
            "repu" to listOf("kaluddam", "vastanu", "matladuddam"),
            "ninna" to listOf("em", "ekkadiki", "vachanu"),
            "sare" to listOf("aithe", "vastanu", "kaluddam"),
            "avunu" to listOf("kada", "nijame", "correct"),
            "ledu" to listOf("andi", "inka", "parledu"),
            "cheppu" to listOf("em", "sangathi", "jarigindi"),
            "thanks" to listOf("and", "a lot", "bro"),
            "office" to listOf("lo", "ki", "nundi"),
            "intiki" to listOf("vastanu", "vellali", "vachanu"),
            "call" to listOf("cheyyi", "chesanu", "matladudam"),
            "message" to listOf("pettanu", "chusava", "cheyyi"),
            "phone" to listOf("cheyyi", "lift", "cheyyandi"),
            "pani" to listOf("undi", "ayipoyindi", "chestunna"),
            "time" to listOf("undi", "ledu", "cheppu"),
            "food" to listOf("tinnava", "kavali", "bagundi"),
            "amma" to listOf("intlo", "ekkada", "cheppu"),
            "nanna" to listOf("intlo", "ekkada", "vastunnaru"),
            "friend" to listOf("vastunnadu", "call", "tho"),
            "chala" to listOf("bagundi", "manchidi", "kastam")
        )
        // Offer the next part of a saved phrase when the preceding word
        // matches, including while the user is typing that next word.
        val phraseContinuations = if (previousWord.isNotBlank()) {
            offlinePhrases.mapNotNull { phrase ->
                val continuation = phrase.removePrefix("$previousWord ").takeIf {
                    phrase.startsWith("$previousWord ", ignoreCase = true)
                } ?: return@mapNotNull null
                if (prefix.isBlank() || continuation.substringBefore(' ')
                        .startsWith(prefix, ignoreCase = true)) continuation else null
            }
        } else emptyList()

        val candidates = if (prefix.isBlank()) {
            val contextual = if (prefs.getBoolean("next_word_enabled", true)) {
                nextWords[previousWord].orEmpty()
            } else emptyList()
            (phraseContinuations + contextual + listOf("nenu", "meeru", "ela"))
                .distinctBy { it.lowercase() }
        } else {
            (phraseContinuations +
                offlinePhrases.filter { phrase ->
                    phrase.substringBefore(' ').startsWith(prefix, ignoreCase = true)
                } +
                personalWords.filter { it.startsWith(prefix, ignoreCase = true) } +
                romanTeluguWords.filter { it.startsWith(prefix, ignoreCase = true) } +
                offlineWords.filter { it.startsWith(prefix, ignoreCase = true) })
                .distinctBy { it.lowercase() }
        }
        val swipeMatches = pendingSwipeWord?.let { listOf(it) }.orEmpty()
        val matches = (swipeMatches + candidates).distinct().take(3)

        val theme = prefs.getString("theme", "light")
        val textColor = if (theme == "dark") Color.rgb(230, 233, 239) else Color.rgb(55, 61, 70)

        if (matches.isEmpty()) {
            row.addView(TextView(this).apply {
                text = "Roman Telugu"
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(if (theme == "dark") Color.LTGRAY else Color.GRAY)
                layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f)
            })
            return
        }

        matches.forEachIndexed { index, word ->
            val suggestion = TextView(this).apply {
                text = word
                textSize = 15f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
                setTextColor(textColor)
                layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f)
                setOnClickListener {
                    if (pendingSwipeWord == word) {
                        currentInputConnection?.commitText("$word ", 1)
                        pendingSwipeWord = null
                    } else {
                        val currentBeforeCursor = currentInputConnection
                            ?.getTextBeforeCursor(80, 0)?.toString().orEmpty()
                        val currentWord = currentBeforeCursor.takeLastWhile { it.isLetter() }
                        if (currentWord.isNotEmpty()) {
                            currentInputConnection?.deleteSurroundingTextInCodePoints(currentWord.codePointCount(0, currentWord.length), 0)
                        }
                        currentInputConnection?.commitText("$word ", 1)
                    }
                    updateSuggestionStrip()
                }
            }
            row.addView(suggestion)
            if (index < matches.size - 1) {
                row.addView(View(this).apply {
                    setBackgroundColor(if (theme == "dark") Color.rgb(75, 80, 90) else Color.rgb(190, 195, 203))
                    layoutParams = LinearLayout.LayoutParams(dp(1), dp(22))
                })
            }
        }
    }

    private fun addClipboardHistoryPanel(root: LinearLayout) {
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER
        }

        header.addView(key("Clipboard", 2f) {})
        header.addView(key("Clear", 1f) {
            clipboardHistory.clearUnpinned()
            renderKeyboard()
        })
        header.addView(key("Close ✕", 1f) {
            showingClipboardHistory = false
            renderKeyboard()
        })
        root.addView(header)

        val entries = clipboardHistory.getAll()

        if (entries.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "No clipboard history yet. Copy some text while the keyboard is active."
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(Color.DKGRAY)
                setPadding(dp(12), dp(24), dp(12), dp(24))
            })
            return
        }

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        entries.forEach { entry ->
            val itemRow = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
            }

            val preview = TextView(this).apply {
                text = entry.text
                textSize = 14f
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(
                    if (getSharedPreferences("keyboard_prefs", MODE_PRIVATE)
                            .getString("theme", "light") == "dark")
                        Color.WHITE else Color.BLACK
                )
                setPadding(dp(8), dp(6), dp(8), dp(6))
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    dp(52),
                    1f
                )
                setOnClickListener {
                    currentInputConnection?.commitText(entry.text, 1)
                    showingClipboardHistory = false
                    renderKeyboard()
                }
            }

            itemRow.addView(preview)

            itemRow.addView(key(if (entry.pinned) "📌" else "Pin", 0.8f) {
                clipboardHistory.togglePin(entry.id)
                renderKeyboard()
            })

            itemRow.addView(key("✕", 0.7f) {
                clipboardHistory.delete(entry.id)
                renderKeyboard()
            })

            list.addView(itemRow)
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(list)
        }

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(230)
            )
        )
    }

    private fun findSwipeLetter(rawX: Float, rawY: Float): Char? {
        val root = keyboardRoot ?: return null
        fun visit(view: View): Char? {
            if (view.visibility != View.VISIBLE || view.width <= 0 || view.height <= 0) return null
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            val inside = rawX >= location[0] && rawX <= location[0] + view.width &&
                rawY >= location[1] && rawY <= location[1] + view.height
            if (view is android.view.ViewGroup) {
                for (index in 0 until view.childCount) {
                    visit(view.getChildAt(index))?.let { return it }
                }
            }
            if (inside) return (view.tag as? String)?.singleOrNull()
            return null
        }
        return visit(root)
    }

    private fun bestSwipeWords(path: String): List<String> {
        if (path.length < 3) return emptyList()
        fun distance(a: String, b: String): Int {
            var previous = IntArray(b.length + 1) { it }
            for (i in a.indices) {
                val current = IntArray(b.length + 1)
                current[0] = i + 1
                for (j in b.indices) {
                    current[j + 1] = minOf(
                        current[j] + 1,
                        previous[j + 1] + 1,
                        previous[j] + if (a[i] == b[j]) 0 else 1
                    )
                }
                previous = current
            }
            return previous[b.length]
        }
        return romanTeluguWords.distinct()
            .filter { kotlin.math.abs(it.length - path.length) <= maxOf(3, path.length / 2) }
            .sortedBy { distance(path, it) + kotlin.math.abs(path.length - it.length) }
            .take(3)
    }

    private fun addRow(root: LinearLayout, letters: String, weight: Float) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            if (letters.length == 9) {
                setPadding(dp(12), 0, dp(12), 0)
            }
        }
        letters.forEach { c ->
            val label = if (!symbolsMode && (shifted || capsLock)) {
                c.uppercaseChar().toString()
            } else c.toString()

            val alternatives = mapOf(
                'q' to "1", 'w' to "2", 'e' to "3",
                'r' to "4", 't' to "5", 'y' to "6",
                'u' to "7", 'i' to "8", 'o' to "9",
                'p' to "0", 'a' to "@", 's' to "#",
                'd' to "$", 'f' to "%", 'g' to "&",
                'h' to "-", 'j' to "+", 'k' to "(",
                'l' to ")"
            )
            val letterKey = key(label, weight, {
                currentInputConnection?.commitText(label, 1)
                updateSuggestionStrip()
                if (shifted && !capsLock && !symbolsMode) {
                    shifted = false
                    renderKeyboard()
                } else {
                    updateAutoCapitalization()
                }
            }, {
                currentInputConnection?.commitText(alternatives[c].orEmpty(), 1)
                updateSuggestionStrip()
            })
            if (!symbolsMode && !englishAlMode && c.isLetter()) {
                letterKey.tag = c.lowercaseChar().toString()
                letterKey.contentDescription = "$label key. Swipe across letters to type a word"
                letterKey.setOnTouchListener { view, event ->
                    when (event.action) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            swipePath.clear()
                            swipePath.append(c.lowercaseChar())
                            swipeStartX = event.rawX
                            swipeStartY = event.rawY
                            swipeMoved = false
                            pendingSwipeWord = null
                            true
                        }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            val traced = findSwipeLetter(event.rawX, event.rawY)
                            if (traced != null && swipePath.lastOrNull() != traced) swipePath.append(traced)
                            if (kotlin.math.hypot((event.rawX - swipeStartX).toDouble(), (event.rawY - swipeStartY).toDouble()) > dp(24)) swipeMoved = true
                            true
                        }
                        android.view.MotionEvent.ACTION_UP -> {
                            val traced = findSwipeLetter(event.rawX, event.rawY)
                            if (traced != null && swipePath.lastOrNull() != traced) swipePath.append(traced)
                            val distance = kotlin.math.hypot((event.rawX - swipeStartX).toDouble(), (event.rawY - swipeStartY).toDouble())
                            val isSwipe = swipeMoved && distance > dp(24) && swipePath.length >= 3
                            if (isSwipe && !isSensitiveInput()) {
                                pendingSwipeWord = bestSwipeWords(swipePath.toString()).firstOrNull()
                                if (pendingSwipeWord != null) updateSuggestionStrip() else view.performClick()
                            } else {
                                view.performClick()
                            }
                            swipePath.clear()
                            true
                        }
                        android.view.MotionEvent.ACTION_CANCEL -> {
                            swipePath.clear()
                            true
                        }
                        else -> true
                    }
                }
            }
            row.addView(letterKey)
        }
        root.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
    }

    private fun key(
        label: String,
        weight: Float,
        action: () -> Unit
    ): TextView = createKey(label, weight, action, null)

    private fun key(
        label: String,
        weight: Float,
        action: () -> Unit,
        longAction: () -> Unit
    ): TextView = createKey(label, weight, action, longAction)

    private fun createKey(
        label: String,
        weight: Float,
        action: () -> Unit,
        longAction: (() -> Unit)?
    ): TextView {
        return TextView(this).apply {
            text = label
            textSize = when {
                label.length > 4 -> 12f
                label.length > 1 -> 15f
                label == " " -> 16f
                else -> 21f
            }
            gravity = Gravity.CENTER
            val keyPreferences = getSharedPreferences("keyboard_prefs", MODE_PRIVATE)
            val theme = keyPreferences.getString("theme", "light")
            val roundedKeys = keyPreferences.getBoolean("rounded_keys_enabled", true)
            val keyBorders = keyPreferences.getBoolean("key_borders_enabled", false)
            val isActionKey = label in setOf("⇧", "⇪", "⌫", "?123", "ABC", ",", ".", "🌐", "⌄") ||
                label in setOf("Go", "Search", "Send", "Next", "Done", "↵")
            val isEnter = label in setOf("Go", "Search", "Send", "Next", "Done", "↵")
            val keyColor = when (theme) {
                "dark" -> when {
                    isEnter -> Color.rgb(75, 105, 151)
                    isActionKey -> Color.rgb(70, 75, 85)
                    else -> Color.rgb(55, 60, 69)
                }
                "blue" -> when {
                    isEnter -> Color.rgb(139, 180, 238)
                    isActionKey -> Color.rgb(190, 211, 239)
                    else -> Color.rgb(250, 252, 255)
                }
                else -> when {
                    isEnter -> Color.rgb(168, 198, 245)
                    isActionKey -> Color.rgb(205, 211, 220)
                    else -> Color.WHITE
                }
            }
            setTextColor(
                if (theme == "dark" && !isEnter) Color.rgb(242, 244, 248)
                else Color.rgb(32, 36, 43)
            )
            background = GradientDrawable().apply {
                setColor(keyColor)
                cornerRadius = dp(
                    if (!roundedKeys) 4 else if (label == " ") 12 else 6
                ).toFloat()
                if (keyBorders) {
                    val borderColor = if (theme == "dark") Color.rgb(103, 111, 124)
                        else Color.rgb(151, 160, 174)
                    setStroke(dp(1), borderColor)
                }
            }
            val selectedFont = keyPreferences.getString("keyboard_font", "sans-serif")
                ?: "sans-serif"
            typeface = Typeface.create(
                selectedFont,
                if (isActionKey) Typeface.BOLD else Typeface.NORMAL
            )
            val configuredKeyHeight = keyPreferences.getInt("key_height_dp", 42).coerceIn(38, 50)
            minimumHeight = dp(configuredKeyHeight)
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
            params.setMargins(dp(2), dp(3), dp(2), dp(3))
            layoutParams = params
            elevation = dp(if (isActionKey) 0 else 1).toFloat()
            setPadding(dp(1), 0, dp(1), 0)
            isSingleLine = true
            setOnClickListener { performKeyFeedback(); action() }
            if (longAction != null) {
                setOnLongClickListener {
                    longAction()
                    true
                }
            }
        }
    }

    private fun performKeyFeedback() {
        val preferences = getSharedPreferences("keyboard_prefs", MODE_PRIVATE)
        if (preferences.getBoolean("key_sound_enabled", false)) {
            try {
                if (toneGenerator == null) {
                    toneGenerator = ToneGenerator(AudioManager.STREAM_SYSTEM, 35)
                }
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 35)
            } catch (_: Exception) {}
        }
        if (preferences.getBoolean("key_vibration_enabled", false)) {
            try {
                val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
                if (vibrator.hasVibrator()) {
                    vibrator.vibrate(VibrationEffect.createOneShot(
                        18, VibrationEffect.DEFAULT_AMPLITUDE
                    ))
                }
            } catch (_: Exception) {}
        }
    }

    private fun dp(value: Int) =
        (value * resources.displayMetrics.density).toInt()

    private fun pasteFromClipboard() {
        try {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            if (!clipboard.hasPrimaryClip()) {
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                return
            }

            val text = clipboard.primaryClip
                ?.getItemAt(0)
                ?.coerceToText(this)
                ?.toString()

            if (text.isNullOrEmpty()) {
                Toast.makeText(this, "No text available to paste", Toast.LENGTH_SHORT).show()
                return
            }

            currentInputConnection?.commitText(text, 1)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to access clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startVoice() {
        if (isSensitiveInput()) {
            Toast.makeText(this, "Voice typing is disabled in password fields", Toast.LENGTH_SHORT).show()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Allow microphone permission in the app first", Toast.LENGTH_LONG).show()
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition unavailable", Toast.LENGTH_SHORT).show()
            return
        }

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)

        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onResults(results: Bundle?) {
                val spoken = results?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )?.firstOrNull().orEmpty()
                if (spoken.isNotBlank()) {
                    currentInputConnection?.commitText(TeluguRomanizer.convert(spoken), 1)
                }
            }
            override fun onError(error: Int) {
                if (error != SpeechRecognizer.ERROR_CLIENT) {
                    Toast.makeText(this@RomanKeyboardService,
                        "Voice recognition error: $error", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "te-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "te-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 20000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 20000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 20000L)
        }
        recognizer?.startListening(intent)
    }

    private fun stopVoice() {
        try {
            recognizer?.stopListening()
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        clipboardListener?.let { listener ->
            clipboardManager?.removePrimaryClipChangedListener(listener)
        }
        clipboardListener = null
        clipboardManager = null

        recognizer?.destroy()
        recognizer = null
        toneGenerator?.release()
        toneGenerator = null
        super.onDestroy()
    }
}

object TeluguRomanizer {
    private val vowels = mapOf(
        'అ' to "a", 'ఆ' to "aa", 'ఇ' to "i", 'ఈ' to "ee",
        'ఉ' to "u", 'ఊ' to "oo", 'ఋ' to "ru", 'ఎ' to "e",
        'ఏ' to "ee", 'ఐ' to "ai", 'ఒ' to "o", 'ఓ' to "oo",
        'ఔ' to "au"
    )

    private val consonants = mapOf(
        'క' to "k", 'ఖ' to "kh", 'గ' to "g", 'ఘ' to "gh", 'ఙ' to "ng",
        'చ' to "ch", 'ఛ' to "chh", 'జ' to "j", 'ఝ' to "jh", 'ఞ' to "ny",
        'ట' to "t", 'ఠ' to "th", 'డ' to "d", 'ఢ' to "dh", 'ణ' to "n",
        'త' to "th", 'థ' to "th", 'ద' to "d", 'ధ' to "dh", 'న' to "n",
        'ప' to "p", 'ఫ' to "ph", 'బ' to "b", 'భ' to "bh", 'మ' to "m",
        'య' to "y", 'ర' to "r", 'ల' to "l", 'వ' to "v",
        'శ' to "sh", 'ష' to "sh", 'స' to "s", 'హ' to "h",
        'ళ' to "l", 'ఱ' to "r"
    )

    private val signs = mapOf(
        'ా' to "aa", 'ి' to "i", 'ీ' to "ee", 'ు' to "u",
        'ూ' to "oo", 'ృ' to "ru", 'ె' to "e", 'ే' to "ee",
        'ై' to "ai", 'ొ' to "o", 'ో' to "oo", 'ౌ' to "au"
    )

    fun convert(input: String): String {
        val out = StringBuilder()
        input.forEach { c ->
            when {
                vowels.containsKey(c) -> out.append(vowels[c])
                consonants.containsKey(c) -> out.append(consonants[c]).append("a")
                signs.containsKey(c) -> {
                    if (out.endsWith("a")) out.deleteCharAt(out.length - 1)
                    out.append(signs[c])
                }
                c == '్' -> {
                    if (out.endsWith("a")) out.deleteCharAt(out.length - 1)
                }
                c == 'ం' -> out.append("m")
                c == 'ః' -> out.append("h")
                c.isWhitespace() || c.isDigit() || c in ".,!?;:-" -> out.append(c)
                else -> out.append(c)
            }
        }
        return out.toString()
    }
}
