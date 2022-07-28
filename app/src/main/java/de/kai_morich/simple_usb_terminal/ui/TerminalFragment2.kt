package de.kai_morich.simple_usb_terminal.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.SystemClock
import android.text.method.ScrollingMovementMethod
import android.view.*
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.gms.location.Priority
import de.kai_morich.simple_usb_terminal.*
import de.kai_morich.simple_usb_terminal.databinding.FragmentTerminalBinding
import de.kai_morich.simple_usb_terminal.services.FirebaseService
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class TerminalFragment2 : Fragment() {

    private var truncate: Boolean = true
    private val viewModel: TerminalViewModel by viewModels()

    private lateinit var binding: FragmentTerminalBinding
    private var initialStart = true

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
            viewModel.disconnect()
        requireActivity().unbindService(viewModel)

        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        //if attach() returns false, the service doesn't exist yet,
        // so start it and try again
        if(!viewModel.attach()){
            requireActivity().startService(Intent(activity, SerialService::class.java))
            viewModel.attach()
        }
    }

    override fun onStop() {
        viewModel.detach()
        super.onStop()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        requireActivity().bindService(Intent(activity, SerialService::class.java), viewModel, Context.BIND_AUTO_CREATE)
    }

    override fun onDetach() {
        requireActivity().unbindService(viewModel)
        super.onDetach()
    }

    override fun onResume() {
        super.onResume()
        requireActivity().registerReceiver(broadcastReceiver, IntentFilter(Constants.INTENT_ACTION_GRANT_USB))
        if(initialStart) {
            initialStart = false
            requireActivity().runOnUiThread(viewModel.connect)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_terminal, container, false)
        binding.receiveText.setTextColor(resources.getColor(R.color.colorRecieveText))
        binding.receiveText.movementMethod = ScrollingMovementMethod.getInstance()

        binding.setupBtn.setOnClickListener { onSetupClicked() }

        binding.stopUploadBtn.setOnClickListener {
            val stopIntent = Intent(context, FirebaseService.ActionListener::class.java)
            stopIntent.action = FirebaseService.KEY_NOTIFICATION_STOP_ACTION
            stopIntent.putExtra(FirebaseService.KEY_NOTIFICATION_ID, ServiceNotification.notificationId)
            FirebaseService.getServiceInstance().sendBroadcast(stopIntent)
        }

        binding.stopMotorBtn.setOnCheckedChangeListener { _, isChecked ->
            val stopMotorIntent = Intent(context, SerialService.ActionListener::class.java)
            stopMotorIntent.action = SerialService.KEY_STOP_MOTOR_ACTION
            stopMotorIntent.putExtra(SerialService.KEY_MOTOR_SWITCH_STATE, isChecked)
            SerialService.getInstance().sendBroadcast(stopMotorIntent)
        }

        binding.startBtn.setOnClickListener { send(BGapi.SCANNER_START) }
        binding.stopBtn.setOnClickListener { send(BGapi.SCANNER_STOP) }

        val gps_options = listOf("Power Saving", "Balanced", "High Accuracy")
        val adapter: ArrayAdapter<CharSequence> = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, gps_options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.gpsPrioritySpinner.adapter = adapter
        binding.gpsPrioritySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val activity = requireActivity()
                if(activity is MainActivity) {
                    when (position) {
                        0 -> activity.updateLocationPriority(Priority.PRIORITY_LOW_POWER)
                        1 -> activity.updateLocationPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                        2 -> activity.updateLocationPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    }
                }
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {
                // do nothing
            }

        }

        binding.receiveText.append("Writing to ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH_mm_ss"))}_log.txt\n")

        return view
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_terminal, menu)
        menu.findItem(R.id.truncate).isChecked = truncate
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.clear -> {
                binding.receiveText.text = ""
                return true
            }
            R.id.manualUpload -> {
                FirebaseService.getServiceInstance().uploadLog()
                return true
            }
            R.id.truncate -> {
                truncate = !truncate
                item.isChecked = truncate
                return true
            }
            R.id.manualCW -> {
                send(BGapi.ROTATE_CW)
                SystemClock.sleep(500)
                send(BGapi.ROTATE_STOP)
                return true
            }
            R.id.manualCCW -> {
                send(BGapi.ROTATE_CCW)
                SystemClock.sleep(500)
                send(BGapi.ROTATE_STOP)
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    //endregion Lifecycle



    private fun onSetupClicked(){
        send(BGapi.SCANNER_SET_MODE)
        send(BGapi.SCANNER_SET_TIMING)
        send(BGapi.CONNECTION_SET_PARAMETERS)
    }
}


