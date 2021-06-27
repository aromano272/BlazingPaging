package com.andreromano.blazingpaging.sample.network.mapper

import com.andreromano.blazingpaging.sample.core.ErrorKt

internal fun String?.asApiError(): ErrorKt.Network.ApiError = when (this) {
    "LOGIN_UserNotFound" -> ErrorKt.Network.ApiError.Login.UserNotFound
    "LOGIN_WrongPassword" -> ErrorKt.Network.ApiError.Login.WrongPassword
    else -> ErrorKt.Network.ApiError.Generic
}