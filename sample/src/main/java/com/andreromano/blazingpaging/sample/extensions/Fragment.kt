package com.andreromano.blazingpaging.sample.extensions

import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope


val Fragment.viewLifecycleScope
    get() = viewLifecycleOwner.lifecycleScope
