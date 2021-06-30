package com.andreromano.blazingpaging.sample.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow


@Dao
interface DataDao {

    @Query("""
        SELECT 
            DataEntity.*,
            CASE 
                WHEN CheckedDataEntity.data_id IS NOT NULL THEN 1
                ELSE 0
            END AS isChecked 
        FROM DataEntity
        LEFT JOIN CheckedDataEntity ON CheckedDataEntity.data_id = DataEntity.id
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getPaged(limit: Int, offset: Int): List<DataItem>

    // TODO: Check if DataEntity, CheckedDataEntity does trigger whenever any changes
    @Query("SELECT * FROM DataEntity, CheckedDataEntity LIMIT 0")
    fun getInvalidationTrigger(): Flow<Empty?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun check(id: CheckedDataEntity)

    @Delete
    suspend fun uncheck(id: CheckedDataEntity)
}