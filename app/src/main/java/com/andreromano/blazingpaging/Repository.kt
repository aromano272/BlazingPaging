package com.andreromano.blazingpaging

object Repository {

    suspend fun getData(page: Int, pageSize: Int) = Api.getData(page, pageSize)

    suspend fun getDiffUtilData(page: Int, pageSize: Int) = Api.getDiffUtilData(page, pageSize)

}