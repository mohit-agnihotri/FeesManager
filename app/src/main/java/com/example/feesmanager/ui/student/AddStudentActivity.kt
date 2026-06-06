package com.example.feesmanager.ui.student

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.feesmanager.R
import com.example.feesmanager.base.BaseActivity
import com.example.feesmanager.data.network.SupabaseManager
import com.example.feesmanager.ui.auth.SessionManager
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * AddStudentActivity — Teacher manually adds a student.
 * Uses the add_student_manually() RPC (SECURITY DEFINER) which creates
 * an auth.users entry + profile + enrollment atomically.
 *
 * Also shows the join code with a Regenerate button.
 */
class AddStudentActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_student)

        val nameField     = findViewById<EditText>(R.id.etName)
        val classField    = findViewById<EditText>(R.id.etClass)
        val whatsappField = findViewById<EditText>(R.id.etWhatsapp)
        val feeField      = runCatching { findViewById<EditText>(R.id.etFee) }.getOrNull()
        val addBtn        = findViewById<Button>(R.id.btnAdd)
        val tvJoinCode    = runCatching { findViewById<TextView>(R.id.tvJoinCodeDisplay) }.getOrNull()
        val btnRegen      = runCatching { findViewById<Button>(R.id.btnRegenCode) }.getOrNull()
        val btnShare      = runCatching { findViewById<Button>(R.id.btnShareCode) }.getOrNull()

        val teacherId = SessionManager.getTeacherId(this) ?: run {
            toast("Not logged in"); finish(); return
        }

        var currentJoinCode = ""
        var academyDisplayName = ""

        // Load current join code
        lifecycleScope.launch {
            try {
                val teacher = SupabaseManager.client.postgrest.from("teachers")
                    .select(Columns.Companion.raw("join_code, academy_name")) {
                        filter { eq("id", teacherId) }
                    }.decodeSingleOrNull<TeacherInfo>()
                currentJoinCode = teacher?.join_code ?: ""
                academyDisplayName = teacher?.academy_name ?: ""
                tvJoinCode?.text = "Join Code: $currentJoinCode"
            } catch (_: Exception) {}
        }

        // Regenerate join code
        btnRegen?.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Regenerate Join Code?")
                .setMessage("Old code will stop working. Students must use the new code.")
                .setPositiveButton("Regenerate") { _, _ ->
                    lifecycleScope.launch {
                        try {
                            val res = SupabaseManager.client.postgrest.rpc("regenerate_join_code")
                                .decodeAs<JsonObject>()
                            val success = res["success"]?.jsonPrimitive?.booleanOrNull ?: false
                            if (success) {
                                currentJoinCode = res["new_code"]?.jsonPrimitive?.content ?: ""
                                tvJoinCode?.text = "Join Code: $currentJoinCode"
                                toast("New code: $currentJoinCode ✅")
                            } else {
                                toast("Regenerate failed")
                            }
                        } catch (e: Exception) {
                            toast("Error: ${e.message}")
                        }
                    }
                }
                .setNegativeButton("Cancel", null).show()
        }

        // Share join code
        btnShare?.setOnClickListener {
            if (currentJoinCode.isEmpty()) { toast("Join code not loaded"); return@setOnClickListener }
            val shareText = "🏫 Join $academyDisplayName on Fees Manager!\n\n" +
                "📋 Join Code: $currentJoinCode\n\n" +
                "Steps:\n1. Download Fees Manager app\n2. Sign up as Student\n3. Enter join code: $currentJoinCode\n4. Wait for teacher approval ✅"
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareText)
            }, "Share Join Code"))
        }

        // Add student button
        addBtn.setOnClickListener {
            val name     = nameField.text.toString().trim()
            val cls      = classField.text.toString().trim()
            val whatsapp = whatsappField.text.toString().trim()
            val fee      = feeField?.text?.toString()?.toDoubleOrNull()

            if (name.isEmpty()) { toast("Enter student name"); return@setOnClickListener }
            if (cls.isEmpty())  { toast("Enter class"); return@setOnClickListener }
            if (whatsapp.isEmpty()) { toast("Enter WhatsApp number"); return@setOnClickListener }

            addBtn.isEnabled = false
            addBtn.text = "Adding..."

            lifecycleScope.launch {
                try {
                    val params = buildJsonObject {
                        put("p_name", name)
                        put("p_class", cls)
                        put("p_phone", whatsapp)
                        if (fee != null) put("p_fee", fee)
                    }
                    val res = SupabaseManager.client.postgrest
                        .rpc("add_student_manually", params)
                        .decodeAs<JsonObject>()

                    val success = res["success"]?.jsonPrimitive?.booleanOrNull ?: false
                    if (success) {
                        toast("✅ $name added successfully!")
                        nameField.text.clear()
                        classField.text.clear()
                        whatsappField.text.clear()
                        feeField?.text?.clear()
                    } else {
                        val error = res["error"]?.jsonPrimitive?.content ?: "Unknown error"
                        toast("Failed: $error")
                    }
                } catch (e: Exception) {
                    toast("Error: ${e.message}")
                } finally {
                    addBtn.isEnabled = true
                    addBtn.text = "➕ Add Student"
                }
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    @Serializable
    private data class TeacherInfo(val join_code: String? = null, val academy_name: String = "")
}