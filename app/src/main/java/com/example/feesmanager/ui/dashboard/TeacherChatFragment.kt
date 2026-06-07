package com.example.feesmanager.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.feesmanager.R
import com.example.feesmanager.ui.chat.ClassSelectChatActivity
import com.example.feesmanager.ui.chat.StudentQueriesActivity
import com.example.feesmanager.utils.AnimUtil.withBounce
import com.example.feesmanager.utils.UnreadBadgeHelper
import kotlinx.coroutines.launch

class TeacherChatFragment : Fragment(R.layout.fragment_teacher_chat) {

    private var teacherId: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val parentActivity = requireActivity() as DashboardActivity
        teacherId = parentActivity.getCurrentTeacherId()

        if (teacherId.isNotEmpty()) {
            loadBadgesAndSetup(view)
        }
    }

    override fun onResume() {
        super.onResume()
        if (teacherId.isNotEmpty()) {
            view?.let { loadBadgesAndSetup(it) }
        }
    }

    private fun loadBadgesAndSetup(view: View) {
        lifecycleScope.launch {
            val personalUnread = UnreadBadgeHelper.fetchTotalPersonalUnreadForTeacher(requireContext(), teacherId)
            val classUnread = UnreadBadgeHelper.fetchTotalClassUnreadForTeacher(requireContext(), teacherId)

            val tvPersonalBadge = view.findViewById<TextView>(R.id.tvPersonalBadge)
            tvPersonalBadge.text = if (personalUnread > 0) personalUnread.toString() else ""
            tvPersonalBadge.visibility = if (personalUnread > 0) View.VISIBLE else View.GONE

            val tvClassBadge = view.findViewById<TextView>(R.id.tvClassBadge)
            tvClassBadge.text = if (classUnread > 0) classUnread.toString() else ""
            tvClassBadge.visibility = if (classUnread > 0) View.VISIBLE else View.GONE

            view.findViewById<View>(R.id.cardPersonalChat).withBounce {
                startActivity(Intent(requireContext(), StudentQueriesActivity::class.java))
            }
            
            view.findViewById<View>(R.id.cardClassChat).withBounce {
                startActivity(Intent(requireContext(), ClassSelectChatActivity::class.java))
            }
        }
    }
}
