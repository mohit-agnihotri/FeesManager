package com.example.feesmanager.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.feesmanager.R
import com.example.feesmanager.data.model.Student
import com.example.feesmanager.data.network.FmResult
import com.example.feesmanager.ui.advances.AdvancePaymentActivity
import com.example.feesmanager.ui.chat.ClassChatActivity
import com.example.feesmanager.ui.chat.MessageActivity
import com.example.feesmanager.ui.fees.FeeCalendarActivity
import com.example.feesmanager.ui.fees.HistoryActivity
import com.example.feesmanager.ui.fees.PayFeesActivity
import com.example.feesmanager.ui.settings.MultiAcademyActivity
import com.example.feesmanager.utils.AnimUtil
import com.example.feesmanager.utils.AnimUtil.withBounce
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StudentHomeFragment : Fragment(R.layout.fragment_student_home) {

    private val viewModel: StudentDashboardViewModel by lazy {
        ViewModelProvider(requireActivity())[StudentDashboardViewModel::class.java]
    }

    private lateinit var tvTotalFees: TextView
    private lateinit var tvPaid: TextView
    private lateinit var tvPending: TextView
    private lateinit var cardAdvanceBalance: View
    private lateinit var tvAdvanceBalance: TextView
    private lateinit var contentWrapper: View

    // Since we're in a fragment, the Activity will pass us student info via a shared ViewModel, 
    // but StudentDashboardViewModel doesn't store student info statically, it observes `viewModel.student`.
    // We can just observe the same LiveData!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        tvTotalFees = view.findViewById(R.id.tvTotalFees)
        tvPaid = view.findViewById(R.id.tvPaid)
        tvPending = view.findViewById(R.id.tvPending)
        cardAdvanceBalance = view.findViewById(R.id.cardAdvanceBalance)
        tvAdvanceBalance = view.findViewById(R.id.tvAdvanceBalance)
        contentWrapper = view.findViewById(R.id.contentWrapper)

        setupButtons(view)

        viewModel.student.observe(viewLifecycleOwner) { result ->
            if (result is FmResult.Success) {
                renderStudent(result.content)
            }
        }

        animateEntrance(view)
    }

    private fun setupButtons(view: View) {
        val parentActivity = requireActivity() as StudentDashboardActivity

        view.findViewById<View>(R.id.btnPay).withBounce {
            startActivity(Intent(requireContext(), PayFeesActivity::class.java))
        }
        view.findViewById<View>(R.id.btnHistory).withBounce {
            startActivity(Intent(requireContext(), HistoryActivity::class.java))
        }
        view.findViewById<View>(R.id.btnCalendar).withBounce {
            startActivity(Intent(requireContext(), FeeCalendarActivity::class.java))
        }
        view.findViewById<View>(R.id.cardAdvanceBalance).withBounce {
            startActivity(Intent(requireContext(), AdvancePaymentActivity::class.java))
        }
        view.findViewById<View>(R.id.btnMyAcademies).withBounce {
            startActivity(Intent(requireContext(), MultiAcademyActivity::class.java))
        }
        view.findViewById<View>(R.id.btnContactTeacher).withBounce {
            val teacherId = parentActivity.getCurrentTeacherId()
            if (teacherId.isEmpty()) {
                Toast.makeText(requireContext(), "Not linked to academy", Toast.LENGTH_SHORT).show()
                return@withBounce
            }
            startActivity(
                Intent(requireContext(), MessageActivity::class.java)
                    .putExtra("mode", "student")
                    .putExtra("teacherId", teacherId)
                    .putExtra("studentId", parentActivity.getCurrentStudentId())
            )
        }
        view.findViewById<View>(R.id.btnClassChat).withBounce {
            val teacherId = parentActivity.getCurrentTeacherId()
            val className = parentActivity.getCurrentClass()
            if (teacherId.isEmpty() || className.isEmpty()) {
                Toast.makeText(requireContext(), "Class not assigned yet", Toast.LENGTH_SHORT).show()
                return@withBounce
            }
            startActivity(
                Intent(requireContext(), ClassChatActivity::class.java)
                    .putExtra("teacherId", teacherId)
                    .putExtra("className", className.removePrefix("Class ").trim())
                    .putExtra("mode", "student")
                    .putExtra("studentId", parentActivity.getCurrentStudentId())
                    .putExtra("senderName", parentActivity.getCurrentName())
            )
        }
    }

    private fun renderStudent(student: Student) {
        val advRemaining = student.advanceBalance.remaining
        cardAdvanceBalance.visibility = View.VISIBLE
        tvAdvanceBalance.text = if (advRemaining > 0) "₹$advRemaining" else "₹0"

        val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        var currentTotal = 0
        var currentPaid = 0
        var totalPending = 0

        for ((key, feeMonth) in student.fees) {
            if (!key.matches(Regex("\\d{4}-\\d{2}"))) continue
            if (key == currentMonth) {
                currentTotal = feeMonth.total
                currentPaid = feeMonth.paid
            }
            val pend = feeMonth.total - feeMonth.paid
            if (pend > 0) totalPending += pend
        }

        tvTotalFees.text = "₹$currentTotal"
        tvPaid.text = "₹$currentPaid"
        tvPending.text = "₹${maxOf(0, totalPending)}"
    }

    private fun animateEntrance(view: View) {
        AnimUtil.slideUp(contentWrapper, 60)
        
        val statCards = listOf(
            tvTotalFees.parent as? View,
            tvPaid.parent as? View,
            tvPending.parent as? View
        ).filterNotNull()
        statCards.forEachIndexed { i, v -> AnimUtil.scaleIn(v, (180 + i * 70).toLong()) }
        
        runCatching { AnimUtil.slideUp(view.findViewById(R.id.btnPay), 420) }
        
        val tiles = listOf(
            R.id.btnHistory, R.id.btnCalendar,
            R.id.btnContactTeacher, R.id.btnClassChat, R.id.btnMyAcademies
        ).mapNotNull { runCatching { view.findViewById<View>(it) }.getOrNull() }
        AnimUtil.staggerIn(tiles, 60)
    }
}
