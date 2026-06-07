package com.example.feesmanager.ui.dashboard

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class StudentPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> StudentHomeFragment()
            1 -> StudentFeesFragment()
            2 -> StudentChatFragment()
            3 -> StudentSettingsFragment()
            else -> StudentHomeFragment()
        }
    }
}
