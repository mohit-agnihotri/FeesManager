package com.example.feesmanager.ui.dashboard

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class DashboardPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> TeacherHomeFragment()
            1 -> TeacherStudentsFragment()
            2 -> TeacherChatFragment()
            3 -> TeacherSettingsFragment()
            else -> TeacherHomeFragment()
        }
    }
}
