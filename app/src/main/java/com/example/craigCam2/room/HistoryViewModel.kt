package com.example.craigCam2.room

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData

class HistoryViewModel(application: Application): AndroidViewModel(application){
    private var repository: HistoryRepository = HistoryRepository(application)

    private var allHistories: LiveData<List<History>> = repository.getAllHistories()

    fun insert(history: History){
        repository.insert(history)
    }

    fun update(history: History){
        repository.update(history)
    }

    fun delete(history: History){
        repository.delete(history)
    }

    fun deleteAllHistories(){
        repository.deleteAllHistories()
    }

    fun getAllNotes(): LiveData<List<History>>{
        return allHistories
    }
}