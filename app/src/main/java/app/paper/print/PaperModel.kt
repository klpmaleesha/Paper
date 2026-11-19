package app.paper.print

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.CancellationSignal
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import app.paper.print.ipp.*

data class PaperState(
    val settings: PrintSettings = PrintSettings(), val document: LocalDocument? = null,
    val preview: Bitmap? = null, val previewIndex: Int = 0, val previewCount: Int = 0,
    val previewBusy: Boolean = false, val busy: Boolean = false, val progress: Int = 0,
    val usb: UsbStatus = UsbStatus(), val error: String? = null,
    val message: String? = null, val dark: Boolean = false, val systemTheme: Boolean = true,
    val selectionError: String? = null,
    val presets:List<PrintPreset> = PrintPresets.defaults,val activePreset:String?=null,
    val duplexSetup:DuplexSetup?=null,
    val networkPrinters:List<NetworkPrinter> = emptyList(),val networkBusy:Boolean=false,val networkError:String?=null
)
class PaperModel(app: Application): AndroidViewModel(app) {
    var state by mutableStateOf(PaperState());private set
    private val prefs=app.getSharedPreferences("paper",0)
    private var previewJob: Job?=null
    private var previewVersion=0
    private var cancellation: CancellationSignal?=null
    private val networkStore=NetworkPrinterStore(app)
    private var scanner:NetworkDiscovery?=null
    private var scanJob:Job?=null
    private val probing=mutableSetOf<String>()
    private val probeSlots=kotlinx.coroutines.sync.Semaphore(2)
    init {
        // Only settings are saved. Files and previews stay in the private cache.
        runCatching {
            val j=JSONObject(prefs.getString("settings","{}")!!)
            val s=SettingsCodec.decode(j)
            s.validate();state=state.copy(settings=s,dark=prefs.getBoolean("dark",false),systemTheme=prefs.getBoolean("systemTheme",true))
        }
        state=state.copy(presets=PrintPresets.defaults+PrintPresets.decode(prefs.getString("presets","[]")!!),
            duplexSetup=DuplexSetup.decode(prefs.getString("duplexSetup","{}").orEmpty())?.takeIf{it.printerId==PrinterProfiles.g2010.id},networkPrinters=networkStore.all())
    }
    fun update(change:(PrintSettings)->PrintSettings){
        if(state.busy)return
        state=state.copy(settings=change(state.settings),error=null,message=null,previewIndex=0,selectionError=null,activePreset=null)
        val s=state.settings
        val j=SettingsCodec.encode(s)
        prefs.edit().putString("settings",j.toString()).apply();preview()
    }
    fun applyPreset(id:String){state.presets.firstOrNull{it.id==id}?.let{p->update{PrintPresets.apply(p,it)};state=state.copy(activePreset=id)}}
    fun savePreset(name:String){
        runCatching{require(state.presets.count{!it.builtIn}<20){"You can save up to 20 custom presets."};PrintPresets.create(name,state.settings)}
            .onSuccess{p->state=state.copy(presets=state.presets+p,activePreset=p.id);prefs.edit().putString("presets",PrintPresets.encode(state.presets)).apply()}
            .onFailure{error(it.message?:"Could not save this preset.")}
    }
    fun deletePreset(id:String){state=state.copy(presets=state.presets.filterNot{it.id==id&&!it.builtIn},activePreset=state.activePreset?.takeUnless{it==id});prefs.edit().putString("presets",PrintPresets.encode(state.presets)).apply()}
    fun saveDuplex(setup:DuplexSetup){state=state.copy(duplexSetup=setup);prefs.edit().putString("duplexSetup",setup.encode()).apply();update{it.copy(reverseBacks=setup.reverseBacks)}}
    fun calibrationSample(){if(state.busy)return;update{PrintSettings(color=false,sides=Sides.LONG)};load{CalibrationDocument.create(getApplication())}}
    fun refreshNetwork(){state=state.copy(networkPrinters=networkStore.all())}
    fun scanNetwork(){
        scanJob?.cancel();scanner?.stop();state=state.copy(networkBusy=true,networkError=null)
        scanner=NetworkDiscovery(getApplication(),{uri->checkNetwork(uri)},{message->state=state.copy(networkError=message)}).also{it.start()}
        scanJob=viewModelScope.launch{delay(12000);scanner?.stop();state=state.copy(networkBusy=false)}
    }
    fun addNetwork(address:String){runCatching{PrinterAddress.parse(address).canonical}.onSuccess{checkNetwork(it)}.onFailure{state=state.copy(networkError=it.message)}}
    private fun checkNetwork(uri:String){
        if(!probing.add(uri))return
        viewModelScope.launch{
            probeSlots.acquire()
            try{val printer=withContext(Dispatchers.IO){NetworkPrinter(uri,IppClient().capabilities(uri))};networkStore.save(printer);state=state.copy(networkPrinters=networkStore.all(),networkError=null)}
            catch(e:Exception){if(e is CancellationException)throw e;state=state.copy(networkError=e.message?:"This printer did not respond.")}
            finally{probeSlots.release();probing.remove(uri)}
        }
    }
    fun removeNetwork(id:String){networkStore.remove(id);refreshNetwork()}
    fun theme(dark: Boolean,system: Boolean=false){state=state.copy(dark=dark,systemTheme=system);prefs.edit().putBoolean("dark",dark).putBoolean("systemTheme",system).apply()}
    fun usb(status:UsbStatus){state=state.copy(usb=status)}
    fun clearNotice(){state=state.copy(error=null,message=null)}
    fun error(message:String){state=state.copy(error=message,busy=false)}
    fun import(uri:Uri){if(state.busy)return; load{Documents.import(getApplication(),uri)}}
    fun sample(){if(state.busy)return;load{Documents.sample(getApplication())}}
    private fun load(loader:()->LocalDocument){
        state=state.copy(busy=true,error=null,message=null)
        viewModelScope.launch {
            try { val doc=withContext(Dispatchers.IO){loader()};state=state.copy(document=doc,busy=false,previewIndex=0,settings=state.settings.copy(range="",filter=PageFilter.ALL,copies=1));preview() }
            catch(e:Exception){state=state.copy(busy=false,error=readable(e))}
        }
    }
    fun turn(delta:Int){state=state.copy(previewIndex=(state.previewIndex+delta).coerceIn(0,(state.previewCount-1).coerceAtLeast(0)));preview()}
    private fun preview(){
        previewJob?.cancel();val version=++previewVersion
        val doc=state.document?:return; val settings=state.settings;val index=state.previewIndex
        state=state.copy(previewBusy=true,preview=null)
        previewJob=viewModelScope.launch{
            delay(130)
            try {
                // Preview one copy; avoid allocating up to 198,000 planned sides just to show a page.
                val plan=try{PrintPlan.build(settings.copy(copies=1),doc.pages)}catch(e:IllegalArgumentException){
                    state=state.copy(previewBusy=false,previewCount=0,selectionError=e.message);return@launch
                }
                val sides=if(settings.duplex)plan.front.zip(plan.back.let{if(settings.reverseBacks)it.reversed() else it}).flatMap{listOf(it.first,it.second.copy(rotate=false))}else plan.front
                val clamped=index.coerceIn(0,sides.lastIndex)
                val bitmap=withContext(Dispatchers.IO){PdfOutput.preview(doc,sides[clamped],settings)}
                if(version==previewVersion)state=state.copy(preview=bitmap,previewIndex=clamped,previewCount=sides.size,previewBusy=false,selectionError=null)
                else bitmap.recycle()
            }catch(e:CancellationException){throw e}catch(e:Exception){if(version==previewVersion)state=state.copy(previewBusy=false,previewCount=0,error=readable(e))}
        }
    }
    fun prepare(document:LocalDocument,settings:PrintSettings,sides:List<SheetSide>,done:(File)->Unit){
        if(state.busy)return
        val signal=CancellationSignal();cancellation=signal
        state=state.copy(busy=true,progress=0,error=null,message=null)
        viewModelScope.launch{
            val file=File(getApplication<Application>().cacheDir,"output-${System.nanoTime()}.pdf")
            try {
                withContext(Dispatchers.IO){PdfOutput.write(document,sides,settings,file,signal){p->viewModelScope.launch{state=state.copy(progress=p)}}}
                state=state.copy(busy=false,progress=100);done(file)
            }catch(e:Exception){state=state.copy(busy=false,error=if(signal.isCanceled) null else readable(e),message=if(signal.isCanceled)"Preparation cancelled. Nothing was sent to the printer." else null)}
            finally{cancellation=null}
        }
    }
    fun export(file:File,uri:Uri){
        state=state.copy(busy=true)
        viewModelScope.launch{
            try {withContext(Dispatchers.IO){getApplication<Application>().contentResolver.openOutputStream(uri,"wt").use{out->requireNotNull(out){"This location cannot be written to."};file.inputStream().use{it.copyTo(out)}}};state=state.copy(busy=false,message="PDF saved.")}
            catch(e:Exception){state=state.copy(busy=false,error=readable(e))}
        }
    }
    fun cancel(){cancellation?.cancel()}
    override fun onCleared(){cancellation?.cancel();scanner?.stop();super.onCleared()}
    private fun readable(e:Exception)=when(e){is SecurityException->"This file is locked or permission was lost. Choose an unlocked copy of the file.";else->e.message?:"The document could not be prepared. Please try another file."}
}
