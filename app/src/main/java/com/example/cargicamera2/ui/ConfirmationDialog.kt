package com.example.cargicamera2.ui

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.example.cargicamera2.R

private const val PERMISSIONS_REQUEST_CODE = 10
private val PERMISSIONS_REQUIRED = arrayOf(Manifest.permission.CAMERA,
    Manifest.permission.WRITE_EXTERNAL_STORAGE,
    Manifest.permission.READ_EXTERNAL_STORAGE)

class ConfirmationDialog:DialogFragment(){
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = AlertDialog.Builder(activity)
        .setMessage(R.string.request_permission)
        .setPositiveButton(android.R.string.ok) { _, _ ->
            parentFragment?.requestPermissions(PERMISSIONS_REQUIRED, PERMISSIONS_REQUEST_CODE)
        }
        .setNegativeButton(android.R.string.cancel) { _, _ ->
            parentFragment?.activity?.finish()
        }
        .create()
}