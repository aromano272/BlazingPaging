package com.andreromano.blazingpaging.sample.reddit

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.andreromano.blazingpaging.sample.R
import com.andreromano.blazingpaging.sample.extensions.viewLifecycleScope
import org.koin.android.ext.android.inject

class RedditFragment : Fragment(R.layout.fragment_reddit) {

    private val api: RedditApi by inject()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleScope.launchWhenCreated {
            val result = api.getAccessToken()
            val a = 1
        }

    }

}