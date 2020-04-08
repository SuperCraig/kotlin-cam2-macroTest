package com.example.cargicamera2.room

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface HistoryDao{
    @Insert
    fun insert(history: History)

    @Update
    fun update(history: History)

    @Delete
    fun delete(history: History)

    @Query("DELETE FROM HistoryTable")
    fun deleteAllHistories()

    @Query("SELECT * FROM HistoryTable ORDER BY id DESC")
    fun getAllHistories(): LiveData<List<History>>
}