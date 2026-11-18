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
    private var shifted = false
    private var capsLock = false
    private var lastShiftTap = 0L
    private lateinit var clipboardHistory: ClipboardHistoryStore
    private var clipboardManager: ClipboardManager? = null
    private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var showingClipboardHistory = false
    private var suggestionRow: LinearLayout? = null
    private var toneGenerator: ToneGenerator? = null

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
        "repu", "ninna", "roju", "randi", "please", "thanks"
    )

    override fun onCreate() {
        super.onCreate()
        clipboardHistory = ClipboardHistoryStore(this)

        clipboardManager =
            getSystemService(CLIPBOARD_SERVICE) as ClipboardManager

        clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
            captureClipboardText()
        }
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

    override fun onFinishInputView(finishingInput: Boolean) {
        clipboardListener?.let {
            clipboardManager?.removePrimaryClipChangedListener(it)
        }
        super.onFinishInputView(finishingInput)
    }

    private fun captureClipboardText() {
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
        toolbar.addView(toolButton("▤", 0.9f) {
            showingClipboardHistory = !showingClipboardHistory
            renderKeyboard()
        })
        toolbar.addView(toolButton("☺", 0.8f) {
            showEmojiPicker()
        })

        val mic = toolButton("🎙", 1.1f) {}
        mic.contentDescription = "Hold to dictate in Telugu"
        mic.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    mic.text = "Listening"
                    startVoice()
                    true
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    mic.text = "🎙"
                    stopVoice()
                    true
                }
                else -> true
            }
        }
        toolbar.addView(mic)
        toolbar.addView(toolButton("⚙", 0.8f) {
            val settingsIntent = Intent(this, KeyboardSettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(settingsIntent)
        })
        toolbar.addView(toolButton("✂", 0.8f) { showEditMenu() })
        root.addView(
            toolbar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(40)
            )
        )

        if (showingClipboardHistory) {
            addClipboardHistoryPanel(root)
            return
        }

        if (preferences.getBoolean("suggestions_enabled", true)) {
            addSuggestionStrip(root)
        }
        if (preferences.getBoolean("number_row_enabled", true)) {
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
            val repeatDelete = object : Runnable {
                override fun run() {
                    if (repeatingDelete) {
                        deleteBackward()
                        backspace.postDelayed(this, 65)
                    }
                }
            }
            backspace.contentDescription = "Backspace. Hold to delete continuously"
            backspace.setOnTouchListener { _, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        performKeyFeedback()
                        repeatingDelete = true
                        deleteBackward()
                        backspace.postDelayed(repeatDelete, 400)
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
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        } else {
            addRow(root, "@#$%&-*+()", 1f)
            addRow(root, "!?/;:'\"_=", 1f)
            addRow(root, "[]{}<>\\|~`", 1f)
        }

        // Standard bottom row: language switch at left, hide at right,
        // with a wide unlabeled spacebar in the centre.
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        bottom.addView(key("🌐", 0.75f) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .showInputMethodPicker()
        })
        bottom.addView(key(if (symbolsMode) "ABC" else "?123", 1.05f) {
            symbolsMode = !symbolsMode
            shifted = false
            renderKeyboard()
        })
        bottom.addView(key(",", 0.55f) {
            currentInputConnection?.commitText(",", 1)
            updateSuggestionStrip()
        })
        val space = key(" ", 3.4f) {
            commitSpaceWithOptionalCorrection()
            updateSuggestionStrip()
        }
        space.contentDescription = "Space"
        bottom.addView(space)
        bottom.addView(key(".", 0.55f) {
            currentInputConnection?.commitText(".", 1)
            updateSuggestionStrip()
        })
        bottom.addView(key(enterKeyLabel(), 1.05f) { performEnterAction() })
        val hide = key("⌄", 0.65f) { requestHideSelf(0) }
        hide.contentDescription = "Hide keyboard"
        bottom.addView(hide)
        root.addView(
            bottom,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun toolButton(
        label: String,
        weight: Float,
        action: () -> Unit
    ): TextView {
        val theme = getSharedPreferences("keyboard_prefs", MODE_PRIVATE)
            .getString("theme", "light")
        return TextView(this).apply {
            text = label
            textSize = 19f
            gravity = Gravity.CENTER
            contentDescription = label
            setTextColor(if (theme == "dark") Color.rgb(226, 230, 238) else Color.rgb(75, 82, 94))
            background = GradientDrawable().apply {
                setColor(when (theme) {
                    "dark" -> Color.rgb(39, 43, 51)
                    "blue" -> Color.rgb(207, 224, 247)
                    else -> Color.rgb(218, 222, 229)
                })
                cornerRadius = dp(20).toFloat()
            }
            val params = LinearLayout.LayoutParams(0, dp(40), weight)
            params.setMargins(dp(2), dp(1), dp(2), dp(1))
            layoutParams = params
            setOnClickListener {
                performKeyFeedback()
                action()
            }
        }
    }

    private fun commitSpaceWithOptionalCorrection() {
        val connection = currentInputConnection ?: return
        val prefs = getSharedPreferences("keyboard_prefs", MODE_PRIVATE)
        val before = connection.getTextBeforeCursor(48, 0)?.toString().orEmpty()
        val word = before.takeLastWhile { it.isLetter() }
        val corrections = mapOf(
            "nuvuu" to "nuvvu",
            "bagunanu" to "bagunnanu",
            "bagunara" to "bagunnara",
            "teh" to "the",
            "adn" to "and",
            "dont" to "don't",
            "cant" to "can't",
            "im" to "I'm"
        )
        val corrected = if (prefs.getBoolean("basic_autocorrect_enabled", false)) {
            corrections[word.lowercase()]
        } else null

        if (corrected != null && word.isNotEmpty()) {
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

        val scroll = ScrollView(this)
        val grid = android.widget.GridLayout(this).apply {
            columnCount = 6
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
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
        }
        scroll.addView(grid)
        dialog.setView(scroll)
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
                dp(39)
            )
        )
        updateSuggestionStrip()
    }

    private fun updateSuggestionStrip() {
        val row = suggestionRow ?: return
        row.removeAllViews()

        val beforeCursor = currentInputConnection
            ?.getTextBeforeCursor(80, 0)?.toString().orEmpty()
        val prefix = beforeCursor.takeLastWhile { it.isLetter() }.lowercase()
        val previousWord = beforeCursor.dropLast(prefix.length)
            .trimEnd().takeLastWhile { it.isLetter() }.lowercase()

        val prefs = getSharedPreferences("keyboard_prefs", MODE_PRIVATE)
        val personalWords = if (prefs.getBoolean("personal_dictionary_suggestions", true)) {
            prefs.getStringSet("personal_dictionary", emptySet())?.toList().orEmpty()
        } else emptyList()
        val nextWords = mapOf(
            "nenu" to listOf("bagunnanu", "vastanu", "cheptanu"),
            "meeru" to listOf("ela", "ekkada", "vastunnara"),
            "ela" to listOf("unnaru", "undi", "undhi"),
            "naaku" to listOf("kavali", "telusu", "ardham"),
            "em" to listOf("chesavu", "jarigindi", "kavali"),
            "repu" to listOf("vastanu", "kaluddam", "matladuddam"),
            "ippudu" to listOf("cheppu", "randi", "chuddam")
        )
        val candidates = if (prefix.isBlank()) {
            val contextual = if (prefs.getBoolean("next_word_enabled", true)) {
                nextWords[previousWord].orEmpty()
            } else emptyList()
            (contextual + listOf("nenu", "meeru", "ela"))
        } else {
            (personalWords.filter { it.startsWith(prefix, ignoreCase = true) } +
                romanTeluguWords.filter { it.startsWith(prefix, ignoreCase = true) })
        }
        val matches = candidates.distinct().take(3)

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
                    val currentBeforeCursor = currentInputConnection
                        ?.getTextBeforeCursor(80, 0)?.toString().orEmpty()
                    val currentWord = currentBeforeCursor.takeLastWhile { it.isLetter() }
                    if (currentWord.isNotEmpty()) {
                        currentInputConnection?.deleteSurroundingTextInCodePoints(currentWord.codePointCount(0, currentWord.length), 0)
                    }
                    currentInputConnection?.commitText("$word ", 1)
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

    private fun addRow(root: LinearLayout, letters: String, weight: Float) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            if (letters.length == 9) {
                setPadding(dp(15), 0, dp(15), 0)
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
            row.addView(key(label, weight, {
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
            }))
        }
        root.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
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
            val theme = getSharedPreferences("keyboard_prefs", MODE_PRIVATE)
                .getString("theme", "light")
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
                cornerRadius = dp(if (label == " ") 8 else 6).toFloat()
            }
            typeface = Typeface.create(
                if (isActionKey) "sans-serif-medium" else "sans-serif",
                if (isActionKey) Typeface.NORMAL else Typeface.NORMAL
            )
            val keyHeight = getSharedPreferences("keyboard_prefs", MODE_PRIVATE)
                .getInt("key_height_dp", 47).coerceIn(40, 58)
            val params = LinearLayout.LayoutParams(0, dp(keyHeight), weight)
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
