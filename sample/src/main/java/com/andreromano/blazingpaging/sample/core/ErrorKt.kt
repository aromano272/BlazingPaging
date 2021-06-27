package com.andreromano.blazingpaging.sample.core

sealed class ErrorKt : Throwable() {
    data class Unknown(override val message: String) : ErrorKt() {
        constructor(ex: Throwable) : this(ex.message.orEmpty())
    }
    object Unauthorized : ErrorKt()
    object Generic : ErrorKt()
    object NotFound : ErrorKt()

    sealed class Database : ErrorKt() {
        object ParsingError : Database()
        object ConnectionClosed : Database()
    }

    sealed class Network : ErrorKt() {
        object ParsingError: Network()
        sealed class ApiError : Network() {

            sealed class Login : ApiError() {
                object UserNotFound : Login()
                object WrongPassword : Login()
            }

            object Generic : ApiError()
        }

        object Generic : ApiError()
    }

    sealed class ChangeInitialPassword : ErrorKt() {
        object PasswordsDontMatch : ChangeInitialPassword()
    }
}