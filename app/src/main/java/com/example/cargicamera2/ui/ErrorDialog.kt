package com.example.cargicamera2.ui

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle

/**
 * Shows an error message dialog.
 */
class ErrorDialog : androidx.fragment.app.DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        AlertDialog.Builder(activity)
            .setMessage(arguments?.getString(ARG_MESSAGE))
            .setPositiveButton(android.R.string.ok) { _, _ -> activity?.finish() }
            .create()

    companion object {

        @JvmStatic private val ARG_MESSAGE = "message"

        @JvmStatic fun newInstance(message: String): ErrorDialog = ErrorDialog().apply {
            arguments = Bundle().apply { putString(ARG_MESSAGE, message) }
        }
    }

}