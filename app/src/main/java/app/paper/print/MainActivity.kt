package app.paper.print

import android.content.Intent
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModelProvider
import java.io.File

class MainActivity:ComponentActivity(){
    private lateinit var model:PaperModel
    private lateinit var usb:UsbPrinters
    private var exportFile:File?=null
    private val open=registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)model.import(uri)}
    private val save=registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")){uri->
        val file=exportFile;exportFile=null;if(uri!=null&&file!=null)model.export(file,uri)
    }
    private val notifications=registerForActivityResult(ActivityResultContracts.RequestPermission()){}
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        model=ViewModelProvider(this)[PaperModel::class.java]
        usb=UsbPrinters(this){model.usb(it)};usb.start()
        setContent{
            val state=model.state
            val dark=if(state.systemTheme)isSystemInDarkTheme()else state.dark
            SideEffect{
                val lightFlags=android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                if(android.os.Build.VERSION.SDK_INT>=30){
                    val mask=android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    window.insetsController?.setSystemBarsAppearance(if(dark)0 else mask,mask)
                }else @Suppress("DEPRECATION") run { window.decorView.systemUiVisibility=if(dark)window.decorView.systemUiVisibility and lightFlags.inv() else window.decorView.systemUiVisibility or lightFlags }
                @Suppress("DEPRECATION") run {window.statusBarColor=if(dark)0xFF101114.toInt()else 0xFFF3F4F7.toInt();window.navigationBarColor=if(dark)0xFF101114.toInt()else 0xFFF3F4F7.toInt()}
            }
            var output by remember{mutableStateOf(false)}
            var appearance by remember{mutableStateOf(false)}
            PaperTheme(dark){
                PaperScreen(state,change={s->model.update{s}},open={open.launch(arrayOf("application/pdf","image/*","text/plain"))},sample=model::sample,turn=model::turn,
                    review={output=true},connect=usb::request,theme={appearance=true},clear=model::clearNotice,cancel=model::cancel,
                    initialSheet=if(intent.getBooleanExtra("printers",false))"printer"else null,
                    applyPreset=model::applyPreset,savePreset=model::savePreset,deletePreset=model::deletePreset,
                    scanNetwork=model::scanNetwork,addNetwork=model::addNetwork,removeNetwork=model::removeNetwork,
                    printSettings={startActivity(Intent(android.provider.Settings.ACTION_PRINT_SETTINGS))},calibrationSample=model::calibrationSample,saveDuplex=model::saveDuplex)
                if(output&&state.document!=null){
                    OutputFlow(state.document,state.settings,onClose={output=false},prepare={sides,settings,target,suffix->
                        model.prepare(state.document,settings,sides){file->
                            when(target){
                                OutputTarget.EXPORT->{exportFile=file;save.launch(state.document.name.substringBeforeLast('.')+suffix+".pdf")}
                                OutputTarget.ANDROID->print(file,state.document.name+suffix,settings)
                                OutputTarget.USB->try{
                                    if(android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    UsbPrintService.start(this,file,state.document.name+suffix,settings,state.usb.deviceId)
                                }catch(e:Exception){model.error(e.message?:"Could not start USB printing.")}
                            }
                        }
                    },busy=state.busy||UsbJobs.state.active,cancel=model::cancel,usb=state.usb,connect=usb::request,duplexSetup=state.duplexSetup,hasNetwork=state.networkPrinters.any{it.capabilities.supported})
                }
                if(appearance){AlertDialog(onDismissRequest={appearance=false},title={Text("Appearance")},text={
                    androidx.compose.foundation.layout.Column{
                        TextButton(onClick={model.theme(false,true);appearance=false}){Text("Use phone setting")}
                        TextButton(onClick={model.theme(false);appearance=false}){Text("Light")}
                        TextButton(onClick={model.theme(true);appearance=false}){Text("Dark")}
                    }
                },confirmButton={TextButton(onClick={appearance=false}){Text("Done")}})}
                UsbJobDialog(UsbJobs.state)
            }
        }
        handleShare(intent)
    }
    private fun print(file:File,name:String,s:PrintSettings){
        try{
            val manager=getSystemService(PrintManager::class.java)
            var media=when(s.paper){PaperSize.A4->PrintAttributes.MediaSize.ISO_A4;PaperSize.A5->PrintAttributes.MediaSize.ISO_A5;PaperSize.LETTER->PrintAttributes.MediaSize.NA_LETTER;PaperSize.LEGAL->PrintAttributes.MediaSize.NA_LEGAL;PaperSize.PHOTO->PrintAttributes.MediaSize.NA_INDEX_4X6}
            media=if(s.landscape)media.asLandscape()else media.asPortrait()
            val attributes=PrintAttributes.Builder().setMediaSize(media).setColorMode(if(s.color)PrintAttributes.COLOR_MODE_COLOR else PrintAttributes.COLOR_MODE_MONOCHROME)
                .setResolution(PrintAttributes.Resolution("paper","Paper",s.quality.dpi,s.quality.dpi))
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS).setDuplexMode(PrintAttributes.DUPLEX_MODE_NONE).build()
            manager.print(name,PdfPrintAdapter(this,file,name),attributes)
        }catch(e:Exception){model.error(e.message?:"Android printing could not be opened. Save the prepared PDF instead.")}
    }
    @Suppress("DEPRECATION") private fun handleShare(intent:Intent?){
        if(intent?.action!=Intent.ACTION_SEND)return
        val uri=if(android.os.Build.VERSION.SDK_INT>=33)intent.getParcelableExtra(Intent.EXTRA_STREAM,android.net.Uri::class.java)else intent.getParcelableExtra(Intent.EXTRA_STREAM)
        if(uri!=null)model.import(uri)
        else model.error("Share a PDF, an image, or a text file. Shared text links cannot be printed directly.")
        intent.action=null
    }
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);handleShare(intent)}
    override fun onResume(){super.onResume();if(::usb.isInitialized)usb.refresh();if(::model.isInitialized)model.refreshNetwork()}
    override fun onDestroy(){if(::usb.isInitialized)usb.stop();super.onDestroy()}
}
