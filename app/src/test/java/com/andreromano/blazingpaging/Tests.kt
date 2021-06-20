package com.andreromano.blazingpaging

import com.andreromano.blazingpaging._utils.MainCoroutineScopeRule
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class Tests {

    @get:Rule
    val coroutineScope = MainCoroutineScopeRule()
}