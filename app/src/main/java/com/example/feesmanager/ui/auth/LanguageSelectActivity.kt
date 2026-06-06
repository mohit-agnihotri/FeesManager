package com.example.feesmanager.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.feesmanager.R
import com.example.feesmanager.utils.AnimUtil
import com.example.feesmanager.utils.LocaleHelper
import com.example.feesmanager.utils.ThemeManager

/**
 * LanguageSelectActivity — Shown only on first launch.
 * Lets user pick English or Hindi, saves to prefs, then restarts in chosen language.
 */
class LanguageSelectActivity : AppCompatActivity() {

    private var selectedLang = "en"

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyFromPref(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_language_select)

        val btnEnglish   = findViewById<View>(R.id.btnEnglish)
        val btnHindi     = findViewById<View>(R.id.btnHindi)
        val checkEnglish = findViewById<TextView>(R.id.checkEnglish)
        val checkHindi   = findViewById<TextView>(R.id.checkHindi)
        val btnContinue  = findViewById<Button>(R.id.btnContinue)

        // Default: English selected
        checkEnglish.visibility = View.VISIBLE
        AnimUtil.scaleIn(btnEnglish, 200)
        AnimUtil.scaleIn(btnHindi, 320)
        AnimUtil.slideUp(btnContinue, 440)

        btnEnglish.setOnClickListener {
            selectedLang = "en"
            checkEnglish.visibility = View.VISIBLE
            checkHindi.visibility   = View.GONE
            AnimUtil.bounce(btnEnglish)
        }

        btnHindi.setOnClickListener {
            selectedLang = "hi"
            checkHindi.visibility   = View.VISIBLE
            checkEnglish.visibility = View.GONE
            AnimUtil.bounce(btnHindi)
        }

        btnContinue.setOnClickListener {
            AnimUtil.bounce(btnContinue)
            // Save and apply locale — recreate to apply
            LocaleHelper.setAndSave(this, selectedLang)
            btnContinue.postDelayed({
                startActivity(Intent(this, RoleSelectActivity::class.java))
                finish()
            }, 100)
        }
    }
}