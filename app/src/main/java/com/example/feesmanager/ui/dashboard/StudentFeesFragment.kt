package com.example.feesmanager.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.feesmanager.R
import com.example.feesmanager.ui.fees.FeeCalendarActivity
import com.example.feesmanager.ui.fees.HistoryActivity
import com.example.feesmanager.ui.fees.PayFeesActivity
import com.example.feesmanager.utils.AnimUtil.withBounce

class StudentFeesFragment : Fragment(R.layout.fragment_student_fees) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.cardPayFees).withBounce {
            startActivity(Intent(requireContext(), PayFeesActivity::class.java))
        }

        view.findViewById<View>(R.id.cardHistory).withBounce {
            startActivity(Intent(requireContext(), HistoryActivity::class.java))
        }

        view.findViewById<View>(R.id.cardCalendar).withBounce {
            startActivity(Intent(requireContext(), FeeCalendarActivity::class.java))
        }
    }
}
