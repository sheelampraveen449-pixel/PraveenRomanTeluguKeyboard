package com.praveen.romantelugu

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 28, 28, 28)
        }

        root.addView(TextView(this).apply {
            text = "Roman Telugu Keyboard"
            textSize = 25f
            gravity = Gravity.CENTER
        })

        root.addView(TextView(this).apply {
            text = "\nType Telugu using English letters or speak Telugu using the microphone.\n"
            textSize = 16f
            gravity = Gravity.CENTER
        })

        root.addView(Button(this).apply {
            text = "1. Allow microphone"
            setOnClickListener {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 10)
                } else {
                    Toast.makeText(this@MainActivity, "Microphone permission granted", Toast.LENGTH_SHORT).show()
                }
            }
        })

        root.addView(Button(this).apply {
            text = "2. Enable keyboard"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        })

        root.addView(Button(this).apply {
            text = "3. Select keyboard"
            setOnClickListener {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showInputMethodPicker()
            }
        })

        setContentView(root)
    }
}
