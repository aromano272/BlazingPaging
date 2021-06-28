package com.andreromano.blazingpaging.sample.database

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity
data class DataEntity(
    @PrimaryKey
    val id: Int,
    val title: String,
)