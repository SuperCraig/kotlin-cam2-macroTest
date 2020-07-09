package com.example.craigCam2.room

import android.app.Application
import android.os.AsyncTask
import androidx.lifecycle.LiveData

class HistoryRepository(application: Application) {
    private var historyDao: HistoryDao
    private var allHistories: LiveData<List<History>>

    init {
        val database: HistoryDatabase =
            HistoryDatabase.getInstance(application.applicationContext)!!
        historyDao = database.historyDao()
        allHistories = historyDao.getAllHistories()
    }

    fun insert(history: History){
        val insertHistoryAsyncTask = InsertHistoryAsyncTask(historyDao).execute(history)
    }

    fun update(history: History){
        val updateHistoryAsyncTask = UpdateHistoryAsyncTask(historyDao).execute(history)
    }

    fun delete(history: History){
        val deleteHistoryAsyncTask = DeleteHistoryAsyncTask(historyDao).execute(history)
    }

    fun deleteAllHistories(){
        val deleteAllHistoriesAsyncTask = DeleteAllHistoriesAsyncTask(historyDao).execute()
    }

    fun getAllHistories(): LiveData<List<History>>{
        return allHistories
    }

    companion object{
        private class InsertHistoryAsyncTask(historyDao: HistoryDao) : AsyncTask<History, Unit, Unit>(){
            val historyDao: HistoryDao = historyDao

            override fun doInBackground(vararg p0: History?) {
                historyDao.insert(p0[0]!!)
            }
        }

        private class UpdateHistoryAsyncTask(historyDao: HistoryDao) : AsyncTask<History, Unit, Unit>(){
            val historyDao: HistoryDao = historyDao

            override fun doInBackground(vararg p0: History?) {
                historyDao.update(p0[9]!!)
            }
        }

        private class DeleteHistoryAsyncTask(historyDao: HistoryDao) : AsyncTask<History, Unit, Unit>(){
            val historyDao: HistoryDao = historyDao

            override fun doInBackground(vararg p0: History?) {
                historyDao.delete(p0[0]!!)
            }
        }

        private class DeleteAllHistoriesAsyncTask(historyDao: HistoryDao) : AsyncTask<Unit, Unit, Unit>(){
            val historyDao: HistoryDao = historyDao

            override fun doInBackground(vararg params: Unit?) {
                historyDao.deleteAllHistories()
            }
        }
    }
}