package com.example.feesmanager.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.feesmanager.R
import com.example.feesmanager.ui.chat.ClassChatActivity
import com.example.feesmanager.ui.chat.MessageActivity
import com.example.feesmanager.utils.AnimUtil.withBounce
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class StudentChatFragment : Fragment(R.layout.fragment_student_chat) {

    private var teacherId: String = ""
    private var studentId: String = ""
    private var className: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val parentActivity = requireActivity() as StudentDashboardActivity
        teacherId = parentActivity.getCurrentTeacherId()
        studentId = parentActivity.getCurrentStudentId()
        className = parentActivity.getCurrentClass().removePrefix("Class ").trim()

        view.findViewById<View>(R.id.cardContactTeacher).withBounce {
            if (teacherId.isEmpty()) {
                Toast.makeText(requireContext(), "Not linked to academy", Toast.LENGTH_SHORT).show()
                return@withBounce
            }
            startActivity(
                Intent(requireContext(), MessageActivity::class.java)
                    .putExtra("mode", "student")
                    .putExtra("teacherId", teacherId)
                    .putExtra("studentId", studentId)
            )
        }

        view.findViewById<View>(R.id.cardClassChat).withBounce {
            if (teacherId.isEmpty() || className.isEmpty()) {
                Toast.makeText(requireContext(), "Class not assigned yet", Toast.LENGTH_SHORT).show()
                return@withBounce
            }
            startActivity(
                Intent(requireContext(), ClassChatActivity::class.java)
                    .putExtra("teacherId", teacherId)
                    .putExtra("className", className)
                    .putExtra("mode", "student")
                    .putExtra("studentId", studentId)
                    .putExtra("senderName", parentActivity.getCurrentName())
            )
        }

        if (teacherId.isNotEmpty()) {
            loadBadges(view)
        }
    }

    override fun onResume() {
        super.onResume()
        if (teacherId.isNotEmpty()) {
            view?.let { loadBadges(it) }
        }
    }

    private fun loadBadges(view: View) {
        lifecycleScope.launch {
            val personalUnread = com.example.feesmanager.utils.UnreadBadgeHelper.fetchTotalUnreadForStudent(requireContext(), teacherId, studentId)
            val classUnread = com.example.feesmanager.utils.UnreadBadgeHelper.fetchClassUnreadForStudent(requireContext(), teacherId, className, studentId)

            val tvPersonalBadge = view.findViewById<android.widget.TextView>(R.id.tvPersonalBadge)
            tvPersonalBadge?.text = if (personalUnread > 0) personalUnread.toString() else ""
            tvPersonalBadge?.visibility = if (personalUnread > 0) View.VISIBLE else View.GONE

            val tvClassBadge = view.findViewById<android.widget.TextView>(R.id.tvClassBadge)
            tvClassBadge?.text = if (classUnread > 0) classUnread.toString() else ""
            tvClassBadge?.visibility = if (classUnread > 0) View.VISIBLE else View.GONE
        }
    }
}
