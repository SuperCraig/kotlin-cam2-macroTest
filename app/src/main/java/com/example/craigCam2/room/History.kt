package com.example.craigCam2.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = History.TABLE_NAME)
data class History(var date:String, var contrast: String, var refreshRate: String, var colorTemperature: String){
    companion object{
        const val TABLE_NAME = "HistoryTable"
    }

    @PrimaryKey(autoGenerate = true)
    var id: Int = 0
}