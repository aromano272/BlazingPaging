package com.andreromano.blazingpaging.sample.other

object Repository {

    suspend fun getSequentialData(page: Int, pageSize: Int) = OtherApi.getSequentialData(page, pageSize)

    suspend fun getDiffUtilData(page: Int, pageSize: Int) = OtherApi.getDiffUtilData(page, pageSize)

}