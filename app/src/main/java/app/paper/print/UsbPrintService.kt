// SPDX-License-Identifier: GPL-3.0-or-later
package app.paper.print

import android.app.*
import android.content.*
import android.os.*
import androidx.compose.runtime.*
import io.github.noble6.canonivec.UsbPrinter
import io.github.noble6.canonivec.core.*
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class UsbJobState(val active:Boolean=false,val title:String="",val message:String="",val progress:Float=0f,val attention:Boolean=false,val error:Boolean=false,val sent:Boolean=false)
object UsbJobs {
    var state by mutableStateOf(UsbJobState());internal set
    internal val cancelled=AtomicBoolean(false)
    @Volatile var report="Paper 0.3.0 · Experimental Canon binary-raster USB test\n";private set
    @Synchronized fun log(text:String){report=(report+text+"\n").takeLast(12000)}
    fun resetReport(){report="Paper 0.3.0 · Experimental G2010 / G2100 raster backend\nGutenprint 5.3.5; USB/status base 55c8322936569f2b2f57db357d95ad934453d73b\nAndroid API ${Build.VERSION.SDK_INT}; ABI=${Build.SUPPORTED_ABIS.joinToString()}\n"}
    fun dismiss(){if(!state.active)state=UsbJobState()}
    fun cancel(){cancelled.set(true)}
}

/** A visible foreground job survives screen rotation/backgrounding; no job is retried automatically. */
class UsbPrintService:Service(){
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private val running=AtomicBoolean(false)
    private var wake:PowerManager.WakeLock?=null
    @Volatile private var lastMessage=""
    override fun onBind(intent:Intent?)=null
    override fun onCreate(){
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"USB printing",NotificationManager.IMPORTANCE_LOW))
    }
    private fun notification(text:String):Notification {
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val cancel=PendingIntent.getService(this,1,Intent(this,UsbPrintService::class.java).setAction(CANCEL),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_paper).setContentTitle("Paper · USB printing").setContentText(text).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).addAction(Notification.Action.Builder(null,"Cancel",cancel).build()).build()
    }
    private fun update(message:String,progress:Float=UsbJobs.state.progress,attention:Boolean=false){
        if(lastMessage!=message){UsbJobs.log(message);lastMessage=message}
        main.post{UsbJobs.state=UsbJobState(true,"Printing via USB",message,progress,attention);getSystemService(NotificationManager::class.java).notify(NOTIFICATION,notification(message))}
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action==CANCEL){UsbJobs.cancel();return START_NOT_STICKY}
        if(intent==null){stopSelf();return START_NOT_STICKY}
        if(!running.compareAndSet(false,true))return START_NOT_STICKY
        UsbJobs.cancelled.set(false)
        UsbJobs.resetReport()
        startForeground(NOTIFICATION,notification("Preparing the USB job"))
        wake=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Paper:USBPrint").apply{acquire(65*60*1000L)}
        val path=intent.getStringExtra("file").orEmpty();val name=intent.getStringExtra("name")?:"Document"
        val size=intent.getStringExtra("paper")?:"A4";val gray=intent.getBooleanExtra("gray",false)
        val quality=intent.getStringExtra("quality")?:"NORMAL";val photo=intent.getBooleanExtra("photo",false)
        UsbJobs.state=UsbJobState(true,name,"Preparing the USB job")
        worker.execute{
            var printer:UsbPrinter?=null;var started=false;val activeJobId="00000001"
            val spool=File(cacheDir,"usb-spool-${System.nanoTime()}").apply{mkdirs()}
            try{
                val file=File(path).canonicalFile
                require(file.parentFile==cacheDir.canonicalFile&&file.isFile){"The prepared document is no longer available. Open it again."}
                val selectedId=intent.getIntExtra("deviceId",-1)
                val device=UsbPrinter.list(this).firstOrNull{d->d.deviceId==selectedId&&UsbPrinter.usb(this).hasPermission(d)}?:throw IOException("The selected USB printer is unavailable. Reconnect it and allow access.")
                printer=UsbPrinter.open(this,device)
                val deviceId=printer.deviceId()
                val mdl=ModelDatabase.field(deviceId,"MDL").orEmpty()
                val cmd=ModelDatabase.field(deviceId,"CMD").orEmpty()
                UsbJobs.log("USB %04x:%04x; MDL=$mdl; CMD=$cmd".format(device.vendorId,device.productId))
                UsbJobs.log("Paper=$size; grayscale=$gray; quality=$quality; photo=$photo")
                G2010Profile.validateIdentity(deviceId)
                UsbJobs.log(printer.description())
                val settings=PrintSettings(paper=PaperSize.valueOf(size),color=!gray,quality=RenderQuality.valueOf(quality),photoPaper=photo)
                require(PrinterProfiles.g2010.problems(settings).isEmpty()){PrinterProfiles.g2010.problems(settings).joinToString("\n")}
                val nativeFile=File(spool,"job.prn")
                require(spool.usableSpace>200L*1024*1024){"Your phone needs 200 MB of free working storage."}
                CanonRaster.render(this,file,settings,nativeFile,{UsbJobs.cancelled.get()}){page,count->update("Encoding Canon side $page of $count",.5f*page/count)}
                UsbJobs.log("Native Canon stream=${nativeFile.length()} bytes; jobID=$activeJobId")
                checkCancelled()
                val p=requireNotNull(printer)
                p.onDebug=UsbJobs::log
                var lastStatus=""
                p.onStatus={status->val info="Printer status=${status.status}; detail=${status.detail}; code=${status.supportCode}";if(info!=lastStatus){lastStatus=info;UsbJobs.log(info)};if(status.blocking)update(status.message,.65f,true)}
                var lastBytes=0L
                p.onTransfer={bytes->if(bytes-lastBytes>=65536||bytes==nativeFile.length()){lastBytes=bytes;update("Sent $bytes of ${nativeFile.length()} bytes",.6f+.2f*bytes/nativeFile.length());UsbJobs.log("USB acknowledged=$bytes/${nativeFile.length()}")}}
                p.startStatusReader();p.ensureListening();checkCancelled()
                update("Sending the document to your printer",.6f);started=true
                nativeFile.inputStream().use{input->p.output{UsbJobs.cancelled.get()}.use{out->input.copyTo(out,16384)}}
                UsbJobs.log("Native stream fully acknowledged: ${p.transferredBytes} bytes")
                update("Waiting for the printer to finish",.85f)
                val confirmed=waitForIdle(p,device.deviceName)
                finish(UsbJobState(title=if(confirmed)"Printer reports finished"else"Document sent",message=if(confirmed)"Check your printed sheets before starting another pass."else"No fresh completion status was received. Check the printer before continuing; do not resend automatically.",progress=1f,sent=true))
            }catch(e:Exception){
                printer?.let{UsbJobs.log("Transfer ended: acknowledged=${it.transferredBytes} bytes; cause=${e.javaClass.simpleName}: ${e.message}")}
                if(started)runCatching{printer?.cancelOnPrinter("print",activeJobId)}
                finish(UsbJobState(title=if(UsbJobs.cancelled.get())"Printing cancelled"else"USB job stopped",message=if(UsbJobs.cancelled.get())"Check the printer for sheets already in progress before starting again."else(e.message?:"The printer connection was lost."),error=!UsbJobs.cancelled.get()))
            }finally{runCatching{printer?.close()};spool.listFiles()?.forEach{it.delete()};spool.delete();wake?.let{if(it.isHeld)it.release()};running.set(false);main.post{stopForeground(STOP_FOREGROUND_REMOVE);stopSelf(startId)}}
        }
        return START_NOT_STICKY
    }
    private fun checkCancelled(){if(UsbJobs.cancelled.get())throw UsbPrinter.CancelledException()}
    private fun waitForIdle(printer:UsbPrinter,deviceName:String):Boolean {
        val start=SystemClock.elapsedRealtime();var idle=0L;var sawActive=false
        while(SystemClock.elapsedRealtime()-start<60*60*1000L){
            checkCancelled()
            if(!UsbPrinter.usb(this).deviceList.containsKey(deviceName))throw IOException("USB cable disconnected. Check which pages printed before retrying.")
            val st=printer.lastStatus;val fresh=System.currentTimeMillis()-printer.lastStatusAt<5000
            if(st!=null&&fresh){
                if(st.blocking){idle=0;update(st.message,.85f,true)}
                else if(st.kind==PrinterStatus.Kind.READY){
                    if(idle==0L)idle=SystemClock.elapsedRealtime()
                    if(sawActive&&SystemClock.elapsedRealtime()-idle>2500)return true
                    // An idle-only reply after transmission does not prove the printer accepted the job.
                    if(!sawActive&&SystemClock.elapsedRealtime()-start>15000)return false
                }else{idle=0;sawActive=true;update(st.message,.9f)}
            }else if(SystemClock.elapsedRealtime()-start>20000)return false
            Thread.sleep(500)
        }
        throw IOException("The printer did not finish within an hour. Check its status before retrying.")
    }
    private fun finish(state:UsbJobState){UsbJobs.log(state.title+": "+state.message);main.post{UsbJobs.state=state}}
    override fun onDestroy(){UsbJobs.cancel();worker.shutdown();super.onDestroy()}
    companion object{
        private const val CHANNEL="paper-usb-print";private const val NOTIFICATION=101;private const val CANCEL="app.paper.print.CANCEL_USB_JOB"
        fun start(context:Context,file:File,name:String,s:PrintSettings,deviceId:Int?=null){
            check(!UsbJobs.state.active){"A USB job is already running."}
            // The USB grant meets Android's connectedDevice foreground-service eligibility.
            // https://developer.android.com/develop/background-work/services/fgs/service-types#connected-device
            val selected=UsbPrinter.list(context).firstOrNull{(deviceId==null||it.deviceId==deviceId)&&UsbPrinter.usb(context).hasPermission(it)}
            check(selected!=null){
                "Connect the Canon G2010 and allow USB access first."
            }
            val i=Intent(context,UsbPrintService::class.java).putExtra("file",file.absolutePath).putExtra("name",name).putExtra("paper",s.paper.name).putExtra("gray",!s.color).putExtra("quality",s.quality.name).putExtra("photo",s.photoPaper).putExtra("deviceId",selected.deviceId)
            context.startForegroundService(i)
        }
    }
}
