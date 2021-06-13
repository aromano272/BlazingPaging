package com.andreromano.blazingpaging.core

sealed class ErrorKt : Throwable() {
    data class Unknown(override val message: String) : ErrorKt() {
        constructor(ex: Throwable) : this(ex.message.orEmpty())
    }
    object Unauthorized : ErrorKt()
    object Generic : ErrorKt()
    object NotFound : ErrorKt()
    object Network : ErrorKt()

    sealed class Database : ErrorKt() {
        object ParsingError : Database()
        object ConnectionClosed : Database()
    }

    sealed class Login : ErrorKt() {
        object UserNotFound : Login()
        object WrongPassword : Login()
    }
    sealed class ChangeInitialPassword : ErrorKt() {
        object PasswordsDontMatch : ChangeInitialPassword()
    }
}