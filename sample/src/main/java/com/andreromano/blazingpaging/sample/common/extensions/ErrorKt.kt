package com.andreromano.blazingpaging.sample.common.extensions

import com.andreromano.blazingpaging.sample.common.core.ErrorKt

val ErrorKt.errorMessage: String
    get() = when (this) {
        is ErrorKt.Unknown -> "Unknown"
        ErrorKt.Unauthorized -> "Unauthorized"
        ErrorKt.Generic -> "Generic"
        ErrorKt.NotFound -> "Not Found"
        is ErrorKt.Network -> when (this) {
            ErrorKt.Network.ParsingError -> "Parsing Error"
            ErrorKt.Network.Generic -> "Generic Network Error"
            ErrorKt.Network.ApiError.Generic -> "Generic Api Error"

            ErrorKt.Network.ApiError.Login.UserNotFound -> "Este email não está registado"
            ErrorKt.Network.ApiError.Login.WrongPassword -> "A password está errada"
        }
        is ErrorKt.ChangeInitialPassword -> when (this) {
            ErrorKt.ChangeInitialPassword.PasswordsDontMatch -> "As passwords não são iguais"
        }
        is ErrorKt.Database -> when (this) {
            ErrorKt.Database.ConnectionClosed -> "Não foi possivel estabelecer a ligação com o servidor"
            ErrorKt.Database.ParsingError -> "Ocorreu um erro"
        }

    }