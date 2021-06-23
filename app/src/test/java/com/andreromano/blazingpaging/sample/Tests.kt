package com.andreromano.blazingpaging.sample

import com.andreromano.blazingpaging.sample._utils.MainCoroutineScopeRule
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class Tests {

    @get:Rule
    val coroutineScope = MainCoroutineScopeRule()
}