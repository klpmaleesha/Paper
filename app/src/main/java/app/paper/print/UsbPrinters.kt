package app.paper.print

import android.app.PendingIntent
import android.content.*
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build

data class UsbStatus(val name: String? = null,val authorized: Boolean = false,val deviceId: Int? = null,val vendorId:Int?=null,val productId:Int?=null) {
    val detected get() = deviceId!=null
    val compatibility get()=if(!detected||vendorId==0x04a9&&(productId==0x183a||name?.contains("G2010",true)==true))Compatibility.EXPERIMENTAL else Compatibility.UNSUPPORTED
    val canPrint get()=detected&&authorized&&compatibility!=Compatibility.UNSUPPORTED
    val label get() = if(detected) "USB printer detected" else "Connect your printer"
}
class UsbPrinters(private val context: Context,private val changed:(UsbStatus)->Unit) {
    private val manager=context.getSystemService(UsbManager::class.java)
    private val action=context.packageName+".USB_PERMISSION"
    private val receiver=object:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){refresh()}}
    fun start(){
        val filter=IntentFilter().apply{addAction(action);addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)}
        if(Build.VERSION.SDK_INT>=33)context.registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("UnspecifiedRegisterReceiverFlag") context.registerReceiver(receiver,filter)
        refresh()
    }
    private fun printer(): UsbDevice? = manager.deviceList.values.firstOrNull { d ->
        (0 until d.interfaceCount).any{d.getInterface(it).interfaceClass==UsbConstants.USB_CLASS_PRINTER}
    }
    fun refresh(){val d=printer();changed(if(d==null)UsbStatus() else UsbStatus(d.productName?:"USB printer",manager.hasPermission(d),d.deviceId,d.vendorId,d.productId))}
    fun request(){val d=printer()?:run{refresh();return}
        if(manager.hasPermission(d)){refresh();return}
        // Mutable is necessary for UsbManager's result extras; the Intent is restricted to this package.
        val flags=PendingIntent.FLAG_UPDATE_CURRENT or if(Build.VERSION.SDK_INT>=31)PendingIntent.FLAG_MUTABLE else 0
        manager.requestPermission(d,PendingIntent.getBroadcast(context,0,Intent(action).setPackage(context.packageName),flags))
    }
    fun stop(){context.unregisterReceiver(receiver)}
}
