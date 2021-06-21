package com.andreromano.blazingpaging.other

import com.andreromano.blazingpaging.DataSource
import com.andreromano.blazingpaging.core.ResultKt
import kotlinx.coroutines.delay

class CustomDataSource<Data : Any>(private val fetch: suspend (page: Int, pageSize: Int) -> ResultKt<List<Data>>) : DataSource<Int, Data>() {
    override suspend fun fetchPage(key: Int, pageSize: Int): FetchResult<Int, Data> =
        when (val result = fetch(key, pageSize)) {
            is ResultKt.Success -> FetchResult.Success(if (result.data.size < pageSize) null else key + 1, result.data)
            is ResultKt.Failure -> FetchResult.Failure(result.error)
        }
}

class StringDataSource(private val fetch: suspend (key: String, pageSize: Int) -> ResultKt<GetUsersResponse> = ::getUsers) : DataSource<String, Data>() {
    override suspend fun fetchPage(key: String, pageSize: Int): FetchResult<String, Data> = when (val result = fetch(key, pageSize)) {
        is ResultKt.Success -> FetchResult.Success(result.data.nextPageKey, result.data.users)
        is ResultKt.Failure -> FetchResult.Failure(result.error)
    }
}

suspend fun getUsers(key: String, pageSize: Int): ResultKt<GetUsersResponse> {
    delay(2000)
    val keyInt = key.englishToInt()
    val nextPageKey = if (keyInt >= 10) null else (keyInt + 1).toEnglish()
    return ResultKt.Success(GetUsersResponse(
        nextPageKey,
        users.subList(
            (keyInt - 1) * pageSize,
            (keyInt * pageSize).coerceAtMost(users.size)
        )
    ))
}



private fun Int.toEnglish(): String = when (this) {
    1 -> "one"
    2 -> "two"
    3 -> "three"
    4 -> "four"
    5 -> "five"
    6 -> "six"
    7 -> "seven"
    8 -> "eight"
    9 -> "nine"
    10 -> "ten"
    else -> throw IllegalStateException()
}

private fun String.englishToInt(): Int = when (this) {
    "one" -> 1
    "two" -> 2
    "three" -> 3
    "four" -> 4
    "five" -> 5
    "six" -> 6
    "seven" -> 7
    "eight" -> 8
    "nine" -> 9
    "ten" -> 10
    else -> throw IllegalStateException()
}

val users = (0..400).map { Data(it, "name $it") }

data class GetUsersResponse(
    val nextPageKey: String?,
    val users: List<Data>,
)

data class User(
    val id: Int,
    val name: String
)