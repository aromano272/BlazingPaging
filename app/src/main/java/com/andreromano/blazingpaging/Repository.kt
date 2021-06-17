package com.andreromano.blazingpaging

object Repository {

    suspend fun getSequentialData(page: Int, pageSize: Int) = Api.getSequentialData(page, pageSize)

    suspend fun getDiffUtilData(page: Int, pageSize: Int) = Api.getDiffUtilData(page, pageSize)

}