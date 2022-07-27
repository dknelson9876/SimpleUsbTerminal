package de.kai_morich.simple_usb_terminal.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import de.kai_morich.simple_usb_terminal.databinding.FragmentTerminalBinding
import de.kai_morich.simple_usb_terminal.Constants

class TerminalFragment2 : Fragment() {

    private val viewModel: TerminalViewModel by viewModels()

    private lateinit var binding: FragmentTerminalBinding

    private val broadcastReceiver = object : BroadcastReceiver () {
        override fun onReceive(context: Context, intent: Intent) {
            if(Constants.INTENT_ACTION_GRANT_USB == intent.action) {
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                connect(granted)
            }
        }

    }

    //region Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        setRetainInstance(true)
    }

    override fun onDestroy() {
        if(viewModel.connected != TerminalViewModel.Connected.False)
            disconnect()
        requireActivity().unbindService(viewModel)

        super.onDestroy()
    }

    //endregion
}

