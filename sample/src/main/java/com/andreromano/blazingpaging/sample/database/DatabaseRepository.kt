package com.andreromano.blazingpaging.sample.database

import com.andreromano.blazingpaging.DataSource
import com.andreromano.blazingpaging.PagedList
import com.andreromano.blazingpaging.Thingamabob
import com.andreromano.blazingpaging.sample.common.core.ErrorKt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow

class DatabaseRepository(
    private val dataDao: DataDao,
) {

    suspend fun getDataPagedList(): PagedList<DataEntity, ErrorKt> = coroutineScope {
        val thingamabob = Thingamabob<DatabaseKey, DataEntity, ErrorKt>(
            this,
            Thingamabob.Config(
                DatabaseKey(0),
                20,
            ),
            DatabaseDataSource(repository)
        )
        pagedListFlow.value = thingamabob.buildPagedList()
        TODO()


    }

    suspend fun check(id: Int) = dataDao.check(CheckedDataEntity(id))

    suspend fun uncheck(id: Int) = dataDao.uncheck(CheckedDataEntity(id))


    data class DatabaseKey(
        val offset: Int,
    )

    class MyDataSource(
        private val dataDao: DataDao,
    ) : DatabaseDataSource<DatabaseKey, DataEntity, ErrorKt>() {
        override suspend fun fetchPage(key: DatabaseKey, pageSize: Int): FetchResult<DatabaseKey, DataEntity, ErrorKt> {
            val result = dataDao.getPaged(pageSize, key.offset)
            val nextOffset = if (result.size < pageSize) null else key.offset + pageSize
            val nextPageKey = nextOffset?.let { DatabaseKey(it) }
            return FetchResult.Success(nextPageKey, result)
        }

    }

}