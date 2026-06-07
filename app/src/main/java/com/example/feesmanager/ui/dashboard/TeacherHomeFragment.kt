package com.example.feesmanager.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.feesmanager.R
import com.example.feesmanager.data.network.FmResult
import com.example.feesmanager.ui.advances.AdvanceStudentsActivity
import com.example.feesmanager.ui.analytics.AnalyticsActivity
import com.example.feesmanager.ui.announcements.AnnouncementsActivity
import com.example.feesmanager.ui.attendance.AttendanceActivity
import com.example.feesmanager.ui.fees.FeesEntryActivity
import com.example.feesmanager.ui.fees.SetClassFeesActivity
import com.example.feesmanager.ui.settings.BackupActivity
import com.example.feesmanager.ui.student.AddStudentActivity
import com.example.feesmanager.ui.student.ViewStudentsActivity
import com.example.feesmanager.utils.AnimUtil
import com.example.feesmanager.utils.AnimUtil.withBounce

class TeacherHomeFragment : Fragment(R.layout.fragment_teacher_home) {

    private val viewModel: DashboardViewModel by lazy {
        ViewModelProvider(requireActivity())[DashboardViewModel::class.java]
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupButtons(view)

        val totalStudents = view.findViewById<TextView>(R.id.tvTotalStudents)
        val totalFees = view.findViewById<TextView>(R.id.tvTotalFees)
        val collected = view.findViewById<TextView>(R.id.tvCollected)
        val pending = view.findViewById<TextView>(R.id.tvPending)

        viewModel.stats.observe(viewLifecycleOwner) { result ->
            if (result is FmResult.Success) {
                val s = result.content
                totalStudents.text = s.totalStudents.toString()
                totalFees.text = "₹${s.totalFees}"
                collected.text = "₹${s.totalCollectedFees}"
                pending.text = "₹${s.totalPendingFees}"
            }
        }
    }

    private fun setupButtons(view: View) {
        fun go(id: Int, cls: Class<*>) = runCatching {
            view.findViewById<View>(id)?.withBounce { startActivity(Intent(requireContext(), cls)) }
        }

        go(R.id.addStudentBtn, AddStudentActivity::class.java)
        go(R.id.viewStudentBtn, ViewStudentsActivity::class.java)
        go(R.id.feesBtn, FeesEntryActivity::class.java)
        go(R.id.btnSetFees, SetClassFeesActivity::class.java)
        go(R.id.btnAnalytics, AnalyticsActivity::class.java)
        go(R.id.btnAttendance, AttendanceActivity::class.java)
        go(R.id.btnBackup, BackupActivity::class.java)
        go(R.id.btnAdvanceStudents, AdvanceStudentsActivity::class.java)
        go(R.id.btnAnnouncements, AnnouncementsActivity::class.java)
        
        view.findViewById<View>(R.id.pendingCardBtn)?.setOnClickListener {
            AnimUtil.bounce(it)
            startActivity(Intent(requireContext(), com.example.feesmanager.ui.student.PendingStudentsActivity::class.java).putExtra("mode", "defaulters"))
        }

        view.findViewById<View>(R.id.btnAnnouncements)?.setOnClickListener {
            AnimUtil.bounce(it)
            startActivity(Intent(requireContext(), AnnouncementsActivity::class.java).putExtra("mode", "teacher"))
        }
    }
}
