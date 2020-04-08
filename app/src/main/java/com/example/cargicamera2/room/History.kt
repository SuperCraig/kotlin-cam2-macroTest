package com.example.cargicamera2.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = History.TABLE_NAME)
data class History(var date:String, var contrast: Int, var refreshRate: Int, var colorTemperature: String){
    companion object{
        const val TABLE_NAME = "HistoryTable"
    }

    @PrimaryKey(autoGenerate = true)
    var id: Int = 0
}