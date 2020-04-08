package com.example.cargicamera2.room

import android.content.Context
import android.os.AsyncTask
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.text.SimpleDateFormat
import java.util.*

@Database(entities = [(History::class)], version = 1)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        private var instance: HistoryDatabase? = null

        fun getInstance(context: Context): HistoryDatabase? {
            if (instance == null) {
                synchronized(HistoryDatabase::class) {
                    instance = Room.databaseBuilder(
                        context.applicationContext,
                        HistoryDatabase::class.java,
                        "history_database"
                    ).fallbackToDestructiveMigration().addCallback(roomCallback).build()
                }
            }
            return instance
        }

        fun destroyInstance() {
            instance = null
        }

        private val roomCallback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                PopulateDbAsyncTask(instance).execute()
            }
        }
    }

    class PopulateDbAsyncTask(db: HistoryDatabase?) : AsyncTask<Unit, Unit, Unit>() {
        private val historyDao: HistoryDao? = db?.historyDao()
        override fun doInBackground(vararg params: Unit?) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            val currentDateTime: String = dateFormat.format(Date()) // Find todays date
            historyDao?.insert(History(currentDateTime,5000, 15000, "Day White"))
            historyDao?.insert(History(currentDateTime,6000, 20000, "Day White"))
        }
    }
}