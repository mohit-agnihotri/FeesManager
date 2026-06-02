package com.example.feesmanager

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

/**
 * SplashActivity — Entry point.
 * Flow:
 *   1. Show splash for 1.5s
 *   2. If first launch → LanguageSelectActivity
 *   3. Else → RoleSelectActivity (which handles auto-login)
 */
class SplashActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyFromPref(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            val next = if (LocaleHelper.isFirstLaunch(this)) {
                Intent(this, LanguageSelectActivity::class.java)
            } else {
                Intent(this, RoleSelectActivity::class.java)
            }
            startActivity(next)
            finish()
        }, 1500)
    }
}
