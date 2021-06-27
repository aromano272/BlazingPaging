package com.andreromano.blazingpaging.sample.common.extensions

import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope


val Fragment.viewLifecycleScope
    get() = viewLifecycleOwner.lifecycleScope
