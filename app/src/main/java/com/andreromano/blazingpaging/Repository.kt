package com.andreromano.blazingpaging

object Repository {

    suspend fun getData(page: Int, pageSize: Int) = Api.getData(page, pageSize)

}