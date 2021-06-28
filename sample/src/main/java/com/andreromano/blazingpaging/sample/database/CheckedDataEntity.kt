package com.andreromano.blazingpaging.sample.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey


@Entity(
    foreignKeys = [
        ForeignKey(
            entity = DataEntity::class,
            parentColumns = ["id"],
            childColumns = ["data_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CheckedDataEntity(
    @PrimaryKey
    val data_id: Int,
)