package de.kai_morich.simple_usb_terminal.ui

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import de.kai_morich.simple_usb_terminal.SerialListener
import de.kai_morich.simple_usb_terminal.SerialService
import java.lang.Exception

class TerminalViewModel(
    private val state: SavedStateHandle,
    private val serviceConnection: SerialServiceConnection
    ) : ViewModel(), ServiceConnection by serviceConnection {

    private val _terminalText = MutableLiveData<String>()
    val terminalText: LiveData<String>
        get() = _terminalText

    //TODO: A DeviceState class might make sense as a wrapper to these values
    private val deviceId: Int? = state["device"]
    private val portNum: Int? = state["port"]
    private val baudRate: Int? = state["baud"]

    enum class Connected {False, Pending, True}


}

//TODO: this part definitely needs to be rewritten - should be using a repository as a single source of truth
class TerminalSerialListener : SerialListener {
    override fun onSerialConnect() {
        TODO("Not yet implemented")
    }

    override fun onSerialConnectError(e: Exception?) {
        TODO("Not yet implemented")
    }

    override fun onSerialRead(data: ByteArray?) {
        TODO("Not yet implemented")
    }

    override fun onSerialIoError(e: Exception?) {
        TODO("Not yet implemented")
    }
}

@RequiresApi(Build.VERSION_CODES.O)
//TODO: this really should go in SerialService, this is temporary
class SerialServiceConnection : ServiceConnection {

    var service: SerialService? = null
        private set

    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
        service = (binder as SerialService.SerialBinder).service
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        service = null
    }

}