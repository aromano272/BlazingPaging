package com.andreromano.blazingpaging.sample.database

import com.andreromano.blazingpaging.DatabaseDataSource
import com.andreromano.blazingpaging.PagedList
import com.andreromano.blazingpaging.Thingamabob
import com.andreromano.blazingpaging.sample.common.core.ErrorKt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlin.coroutines.coroutineContext

class DatabaseRepository(
    private val dataDao: DataDao,
) {

    fun getDataPagedList(coroutineScope: CoroutineScope): PagedList<DataItem, ErrorKt> {
        val thingamabob = Thingamabob<DatabaseKey, DataItem, ErrorKt>(
            coroutineScope,
            Thingamabob.Config(
                DatabaseKey(0),
                20,
            ),
            MyDataSource(dataDao),
        )

        return thingamabob.buildPagedList()
    }

    suspend fun check(id: Int) = dataDao.check(CheckedDataEntity(id))

    suspend fun uncheck(id: Int) = dataDao.uncheck(CheckedDataEntity(id))


    data class DatabaseKey(
        val offset: Int,
    )

    class MyDataSource(
        private val dataDao: DataDao,
    ) : DatabaseDataSource<DatabaseKey, DataItem, ErrorKt>(dataDao.getInvalidationTrigger()) {
        override suspend fun fetchPage(key: DatabaseKey, pageSize: Int): FetchResult<DatabaseKey, DataItem, ErrorKt> {
            val result = dataDao.getPaged(pageSize, key.offset)
            val nextOffset = if (result.size < pageSize) null else key.offset + pageSize
            val nextPageKey = nextOffset?.let { DatabaseKey(it) }
            return FetchResult.Success(nextPageKey, result)
        }
    }
}