package com.andreromano.blazingpaging.sample.common.network.mapper

import com.andreromano.blazingpaging.sample.common.core.ErrorKt
import com.andreromano.blazingpaging.sample.common.core.ResultKt
import retrofit2.*
import timber.log.Timber
import java.io.IOException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.net.SocketTimeoutException

class ResultKtCallAdapter<T>(
    private val retrofit: Retrofit,
    private val apiResultType: ParameterizedType
) : CallAdapter<ResultKt<T>, Call<ResultKt<T>>> {

    override fun adapt(call: Call<ResultKt<T>>): Call<ResultKt<T>> {
        return object : Call<ResultKt<T>> by call {
            override fun enqueue(callback: Callback<ResultKt<T>>) {
                call.enqueue(
                    object : Callback<ResultKt<T>> {
                        override fun onResponse(call: Call<ResultKt<T>>, response: Response<ResultKt<T>>) {
                            val apiResponse: ResultKt<T> = try {
                                fromSuccess(response, apiResultType, call, retrofit)
                            } catch (ex: Exception) {
                                fromFailure(ex, call)
                            }
                            callback.onResponse(call, Response.success(apiResponse))
                        }

                        override fun onFailure(call: Call<ResultKt<T>>, error: Throwable) {
                            val apiResponse: ResultKt<T> = fromFailure(error, call)

                            callback.onResponse(call, Response.success(apiResponse))
                        }
                    }
                )
            }
        }
    }

    override fun responseType(): Type = apiResultType

    companion object {
        private fun <T> fromSuccess(response: Response<ResultKt<T>>, responseType: Type, call: Call<ResultKt<T>>, retrofit: Retrofit): ResultKt<T> {
            val apiResponse = try {
                if (response.isSuccessful) {
                    val body = response.body()
                    processResponse(body, response)
                } else {
                    return try {
                        val body = response.errorBody()?.let {
                            retrofit.responseBodyConverter<ResultKt<T>>(responseType, emptyArray()).convert(it)
                        }
                        processResponse(body, response)
                    } catch (ex: Exception) {
                        processResponse(null, response)
                    }
                }
            } catch (error: Exception) {
                fromFailure(error, call)
            }

            return apiResponse
        }

        private fun <T> processResponse(body: ResultKt<T>?, response: Response<ResultKt<T>>): ResultKt<T> = when {
            response.code() == 401 -> ResultKt.Failure(ErrorKt.Unauthorized)
            response.code() == 500 -> {
                val errorResponse = response.errorBody()?.string()
                Timber.e("500 SERVER ERROR: $errorResponse")
                ResultKt.Failure(ErrorKt.Generic)
            }
            body == null -> ResultKt.Failure(ErrorKt.Generic)
            else -> body
        }

        private fun <T> fromFailure(error: Throwable, call: Call<ResultKt<T>>): ResultKt<T> {
            val apiResponse: ResultKt<T> = try {
                if (error is RuntimeException) {
                    Timber.e("MOSHI EXCEPTION: $error")
                } else if (error is SocketTimeoutException) {
                    val newError = Error("TIMEOUT url: ${call.request().url}", error)
                    Timber.e("TIMEOUT EXCEPTION: $newError")
                }
                when (error) {
                    is IOException -> ResultKt.Failure(ErrorKt.Network.Generic)
                    else -> ResultKt.Failure(ErrorKt.Generic)
                }
            } catch (ex: Exception) {
                ResultKt.Failure(ErrorKt.Unknown(ex.message.orEmpty()))
            }

            return apiResponse
        }

    }
}