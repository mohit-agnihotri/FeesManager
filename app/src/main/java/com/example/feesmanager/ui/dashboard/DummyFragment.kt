package com.example.feesmanager.ui.dashboard

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.feesmanager.R

class DummyFragment : Fragment(R.layout.fragment_dummy) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val title = arguments?.getString("title") ?: "Tab"
        view.findViewById<TextView>(R.id.tvTitle)?.text = title
    }
    
    companion object {
        fun newInstance(title: String) = DummyFragment().apply {
            arguments = Bundle().apply { putString("title", title) }
        }
    }
}
