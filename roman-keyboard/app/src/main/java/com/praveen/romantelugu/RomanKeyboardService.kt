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
            setPadding(3, 5, 3, 6)
            setBackgroundColor(
                when (getSharedPreferences("keyboard_prefs", MODE_PRIVATE)
                    .getString("theme", "light")) {
                    "dark" -> Color.rgb(35, 38, 44)
                    "blue" -> Color.rgb(205, 224, 250)
                    else -> Color.rgb(225, 229, 235)
                }
            )
        }
        renderKeyboard()
        return keyboardRoot!!
    }

    private fun renderKeyboard() {
        val root = keyboardRoot ?: return
        root.removeAllViews()

        val toolbar = LinearLayout(this).apply {
            gravity = Gravity.CENTER
        }

        toolbar.addView(key("📋 History", 1.2f) {
            showingClipboardHistory = !showingClipboardHistory
            renderKeyboard()
        })
        toolbar.addView(key("😀", 0.8f) {
            currentInputConnection?.commitText("😀", 1)
        })

        val mic = key("🎙 Hold", 1.5f) {}
        mic.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    mic.text = "Listening..."
                    startVoice()
                    true
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    mic.text = "🎙 Hold"
                    stopVoice()
                    true
                }
                else -> true
            }
        }
        toolbar.addView(mic)

        toolbar.addView(key("⚙", 0.8f) {
            val settingsIntent = Intent(this, KeyboardSettingsActivity::class.java)
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(settingsIntent)
        })
        toolbar.addView(key("✂", 0.8f) {
            showEditMenu()
        })
        toolbar.addView(key("⇄", 0.8f) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .showInputMethodPicker()
        })
        toolbar.addView(key("⌄", 0.8f) {
            requestHideSelf(0)
        })
        root.addView(toolbar)

        if (showingClipboardHistory) {
            addClipboardHistoryPanel(root)
            return
        }

        val preferences = getSharedPreferences("keyboard_prefs", MODE_PRIVATE)
        if (preferences.getBoolean("suggestions_enabled", true)) {
            addSuggestionStrip(root)
        }
        if (preferences.getBoolean("number_row_enabled", true)) {
            addRow(root, "1234567890", 1f)
        }

        if (!symbolsMode) {
            addRow(root, "qwertyuiop", 1f)
            addRow(root, "asdfghjkl", 1f)

            val third = LinearLayout(this).apply { gravity = Gravity.CENTER }
            third.addView(key(if (capsLock) "⇪" else "⬆", 1.35f) {
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
                    }
                }, {
                    currentInputConnection?.commitText(alternatives[c].orEmpty(), 1)
                }))
            }
            val backspace = key("⌫", 1.35f) {}
            var repeatingDelete = false
            val repeatDelete = object : Runnable {
                override fun run() {
                    if (repeatingDelete) {
                        deleteBackward()
                        backspace.postDelayed(this, 65)
                    }
                }
            }
            backspace.setOnTouchListener { _, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
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
            root.addView(third)
        } else {
            addRow(root, "@#$%&-*+()", 1f)
            addRow(root, "!?/;:'\"_=", 1f)
            addRow(root, "[]{}<>\\|~`", 1f)
        }

        val bottom = LinearLayout(this).apply { gravity = Gravity.CENTER }
        bottom.addView(key(if (symbolsMode) "ABC" else "?123", 1.25f) {
            symbolsMode = !symbolsMode
            shifted = false
            renderKeyboard()
        })
        bottom.addView(key(",", 0.75f) {
            currentInputConnection?.commitText(",", 1)
            updateSuggestionStrip()
        })
        bottom.addView(key("Roman Telugu", 3.8f) {
            currentInputConnection?.commitText(" ", 1)
            updateSuggestionStrip()
        })
        bottom.addView(key(".", 0.75f) {
            currentInputConnection?.commitText(".", 1)
            updateSuggestionStrip()
        })
        bottom.addView(key(enterKeyLabel(), 1.15f) {
            performEnterAction()
        })
        root.addView(bottom)
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
            gravity = Gravity.CENTER
        }
        root.addView(
            suggestionRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42)
            )
        )
        updateSuggestionStrip()
    }

    private fun updateSuggestionStrip() {
        val row = suggestionRow ?: return
        row.removeAllViews()

        val beforeCursor = currentInputConnection
            ?.getTextBeforeCursor(60, 0)?.toString().orEmpty()
        val prefix = beforeCursor.takeLastWhile { it.isLetter() }
            .lowercase()

        val matches = if (prefix.isBlank()) {
            listOf("nenu", "meeru", "ela")
        } else {
            romanTeluguWords
                .filter { it.startsWith(prefix) }
                .distinct()
                .take(3)
        }

        if (matches.isEmpty()) {
            row.addView(TextView(this).apply {
                text = "Roman Telugu"
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(Color.GRAY)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            })
            return
        }

        matches.forEach { word ->
            row.addView(key(word, 1f) {
                val currentBeforeCursor = currentInputConnection
                    ?.getTextBeforeCursor(60, 0)?.toString().orEmpty()
                val currentWord = currentBeforeCursor
                    .takeLastWhile { it.isLetter() }

                if (currentWord.isNotEmpty()) {
                    currentInputConnection?.deleteSurroundingText(
                        currentWord.length, 0
                    )
                }
                currentInputConnection?.commitText("$word ", 1)
                updateSuggestionStrip()
            })
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
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
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
                }
            }, {
                currentInputConnection?.commitText(alternatives[c].orEmpty(), 1)
            }))
        }
        root.addView(row)
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
            textSize = if (label.length > 3) 13f else 18f
            gravity = Gravity.CENTER
            val selectedTheme = getSharedPreferences("keyboard_prefs", MODE_PRIVATE)
                .getString("theme", "light")
            setTextColor(if (selectedTheme == "dark") Color.WHITE else Color.BLACK)
            background = GradientDrawable().apply {
                setColor(
                    when (selectedTheme) {
                        "dark" -> Color.rgb(65, 69, 78)
                        "blue" -> Color.rgb(250, 252, 255)
                        else -> Color.WHITE
                    }
                )
                cornerRadius = dp(7).toFloat()
            }
            typeface = Typeface.DEFAULT_BOLD
            val keyHeight = getSharedPreferences("keyboard_prefs", MODE_PRIVATE)
                .getInt("key_height_dp", 47).coerceIn(40, 64)
            val params = LinearLayout.LayoutParams(0, dp(keyHeight), weight)
            params.setMargins(2, 3, 2, 3)
            layoutParams = params
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
