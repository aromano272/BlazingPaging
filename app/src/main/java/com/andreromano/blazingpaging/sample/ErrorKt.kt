package com.andreromano.blazingpaging.sample

import com.andreromano.blazingpaging.sample.core.ErrorKt

val ErrorKt.errorMessage: String
    get() = when (this) {
        is ErrorKt.Unknown -> "Unknown"
        ErrorKt.Unauthorized -> "Unauthorized"
        ErrorKt.Generic -> "Generic"
        ErrorKt.Network -> "Network"
        ErrorKt.NotFound -> "Not Found"
        is ErrorKt.Login -> when (this) {
            ErrorKt.Login.UserNotFound -> "Este email não está registado"
            ErrorKt.Login.WrongPassword -> "A password está errada"
        }
        is ErrorKt.ChangeInitialPassword -> when (this) {
            ErrorKt.ChangeInitialPassword.PasswordsDontMatch -> "As passwords não são iguais"
        }
        is ErrorKt.Database -> when (this) {
            ErrorKt.Database.ConnectionClosed -> "Não foi possivel estabelecer a ligação com o servidor"
            ErrorKt.Database.ParsingError -> "Ocorreu um erro"
        }

    }