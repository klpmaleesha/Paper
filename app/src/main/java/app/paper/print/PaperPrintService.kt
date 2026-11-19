// SPDX-License-Identifier: GPL-3.0-or-later
package app.paper.print

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.print.*
import android.printservice.*
import android.printservice.PrintJob
import app.paper.print.ipp.*
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** System print-menu integration for capability-checked PDF-over-IPP printers. */
class PaperPrintService:PrintService() {
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private val probes=Executors.newSingleThreadExecutor()
    private val cancel=ConcurrentHashMap<PrintJobId,AtomicBoolean>()
    private val store by lazy{NetworkPrinterStore(this)}
    private var discovery:PrinterDiscoverySession?=null
    private fun postJob(action:()->Unit){main.post{runCatching(action)}}

    override fun onCreatePrinterDiscoverySession():PrinterDiscoverySession=object:PrinterDiscoverySession(){
        private var scanner:NetworkDiscovery?=null
        private val checking=mutableSetOf<String>()
        override fun onStartPrinterDiscovery(priorityList:MutableList<PrinterId>){
            scanner?.stop();checking.clear();discovery=this;publishSaved()
            scanner=NetworkDiscovery(this@PaperPrintService,{uri->probe(uri)},{}).also{it.start()}
            val currentScanner=scanner;main.postDelayed({currentScanner?.stop()},12000)
            store.all().forEach{probe(it.uri)}
        }
        private fun probe(uri:String){
            if(!checking.add(uri))return
            probes.execute{runCatching{NetworkPrinter(uri,IppClient().capabilities(uri))}.onSuccess{printer->
                store.save(printer);main.post{if(!isDestroyed)addPrinters(listOf(info(printer)))}
            }.onFailure{main.post{if(!isDestroyed)store.all().firstOrNull{it.uri==uri}?.let{addPrinters(listOf(info(it,true)))}}}}
        }
        private fun publishSaved(){addPrinters(store.all().map{info(it,true)})}
        override fun onStopPrinterDiscovery(){scanner?.stop();scanner=null}
        override fun onValidatePrinters(ids:MutableList<PrinterId>){ids.forEach{id->store.all().firstOrNull{it.id==id.localId}?.let{checking.remove(it.uri);probe(it.uri)}}}
        override fun onStartPrinterStateTracking(id:PrinterId){onValidatePrinters(mutableListOf(id))}
        override fun onStopPrinterStateTracking(id:PrinterId){}
        override fun onDestroy(){scanner?.stop();if(discovery===this)discovery=null}
    }

    private fun info(p:NetworkPrinter,offline:Boolean=false):PrinterInfo {
        val c=p.capabilities;val id=generatePrinterId(p.id)
        val builder=PrinterInfo.Builder(id,c.name,if(offline||!c.supported||!c.accepting||c.printerState==5)PrinterInfo.STATUS_UNAVAILABLE else if(c.printerState==4)PrinterInfo.STATUS_BUSY else PrinterInfo.STATUS_IDLE)
            .setDescription(if(offline)"Offline · check the printer connection"else c.unsupportedReason?:"Paper · PDF over IPP · experimental")
        val open=Intent(this,MainActivity::class.java).putExtra("printers",true)
        builder.setInfoIntent(PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        if(c.supported){
            val capabilities=PrinterCapabilitiesInfo.Builder(id)
            val media=c.paperSizes.sortedBy{it.ordinal};media.forEachIndexed{i,paper->capabilities.addMediaSize(androidMedia(paper),i==0)}
            c.resolutions.sortedBy{it.xDpi.toLong()*it.yDpi}.forEachIndexed{i,r->capabilities.addResolution(PrintAttributes.Resolution(r.id,"${r.xDpi} × ${r.yDpi} dpi",r.xDpi,r.yDpi),i==0)}
            capabilities.setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            val colour=c.profile
            var modes=0;if(colour.colour)modes=modes or PrintAttributes.COLOR_MODE_COLOR;if(colour.grayscale)modes=modes or PrintAttributes.COLOR_MODE_MONOCHROME
            capabilities.setColorModes(modes,if(colour.colour)PrintAttributes.COLOR_MODE_COLOR else PrintAttributes.COLOR_MODE_MONOCHROME)
            var duplex=PrintAttributes.DUPLEX_MODE_NONE
            if(Sides.LONG in colour.automaticDuplex)duplex=duplex or PrintAttributes.DUPLEX_MODE_LONG_EDGE
            if(Sides.SHORT in colour.automaticDuplex)duplex=duplex or PrintAttributes.DUPLEX_MODE_SHORT_EDGE
            capabilities.setDuplexModes(duplex,PrintAttributes.DUPLEX_MODE_NONE)
            builder.setCapabilities(capabilities.build())
        }
        return builder.build()
    }

    override fun onPrintJobQueued(job:PrintJob){
        if(job.tag?.startsWith("paper-attempted")==true){job.fail("This job may already be at the printer. Check its queue before creating a new job; Paper will not resend it automatically.");return}
        val snapshot=job.info
        val localJobId=job.id
        val printer=store.all().firstOrNull{it.id==snapshot.printerId?.localId}
        if(printer==null){job.fail("Add this network printer in Paper first.");return}
        val stopped=AtomicBoolean(false);cancel[localJobId]=stopped
        worker.execute{
            val file=File(cacheDir,"system-print-${System.nanoTime()}.pdf")
            var attempted=false;var remoteId:Int?=null
            var fd:ParcelFileDescriptor?=null
            try{
                val ready=java.util.concurrent.CountDownLatch(1)
                val descriptor=java.util.concurrent.atomic.AtomicReference<ParcelFileDescriptor?>()
                val dataError=java.util.concurrent.atomic.AtomicReference<Exception?>()
                main.post{try{if(!stopped.get()){check(job.start()){"The job is no longer queued."};job.setStatus("Preparing the document");descriptor.set(job.document.data)}}catch(e:Exception){dataError.set(e)}finally{ready.countDown()}}
                ready.await()
                dataError.get()?.let{throw it}
                if(stopped.get())throw InterruptedException()
                fd=descriptor.get()?:throw IOException("Android did not provide a printable document.")
                // The Android spooler supplies the final, selected-page PDF. Do not reapply the source page ranges.
                ParcelFileDescriptor.AutoCloseInputStream(fd).use{input->file.outputStream().use{out->
                    val b=ByteArray(16384);var total=0L;while(true){if(stopped.get())throw InterruptedException();val n=input.read(b);if(n<0)break;total+=n;require(total<=104_857_600){"Use a document no larger than 100 MB."};out.write(b,0,n)}
                }}
                require(file.inputStream().use{input->val h=ByteArray(5);input.read(h)==5&&h.toString(Charsets.US_ASCII)=="%PDF-"}){"Android did not provide a PDF document."}
                val client=IppClient();val caps=client.capabilities(printer.uri)
                require(caps.supported){caps.unsupportedReason?:"This printer cannot print this document."}
                require(caps.accepting&&caps.printerState!=5){"The printer is not accepting jobs. Check its display."}
                require(snapshot.copies in 1..caps.maxCopies){"This printer supports up to ${caps.maxCopies} copies per job. Choose fewer copies."}
                val media=requireNotNull(snapshot.attributes?.mediaSize){"Choose a paper size."}
                val paper=PaperSize.entries.firstOrNull{androidMedia(it).asPortrait().let{m->kotlin.math.abs(m.widthMils-media.asPortrait().widthMils)<10&&kotlin.math.abs(m.heightMils-media.asPortrait().heightMils)<10}}
                val mediaKeyword=caps.media.firstOrNull{IppCapabilities.mediaToPaper[it]==paper}?:throw IOException("The chosen paper size is not supported by this printer.")
                val sides=when(snapshot.attributes?.duplexMode){PrintAttributes.DUPLEX_MODE_LONG_EDGE->"two-sided-long-edge";PrintAttributes.DUPLEX_MODE_SHORT_EDGE->"two-sided-short-edge";else->"one-sided"}
                require(sides in caps.sides){"The chosen two-sided mode is not supported by this printer."}
                val colour=if(snapshot.attributes?.colorMode==PrintAttributes.COLOR_MODE_MONOCHROME)"monochrome"else"color"
                require(colour in caps.colourModes){"The chosen colour mode is not supported by this printer."}
                val chosen=snapshot.attributes?.resolution
                val resolution=caps.resolutions.firstOrNull{it.id==chosen?.id}?:throw IOException("The chosen printer resolution is no longer supported. Select the printer again.")
                if(stopped.get())throw InterruptedException()
                // Persist an attempt marker before any print bytes leave. Retrying an uncertain job can duplicate sheets.
                val marked=java.util.concurrent.CountDownLatch(1);val saved=AtomicBoolean(false)
                main.post{try{saved.set(job.setTag("paper-attempted"));job.setStatus("Sending to ${caps.name}")}catch(_:Exception){saved.set(false)}finally{marked.countDown()}};marked.await()
                check(saved.get()){"The print job is no longer active. Nothing was sent."}
                attempted=true;remoteId=client.submit(printer.uri,file,IppJobOptions(mediaKeyword,colour,sides,snapshot.copies,resolution)){stopped.get()}
                val id=remoteId
                postJob{job.setTag("paper-attempted:$id");job.setStatus("Accepted by printer · waiting for completion")}
                if(IppProtocol.GET_JOB !in caps.operations)throw IOException("The document was accepted, but this printer cannot report completion. Check its queue before printing again.")
                val deadline=System.nanoTime()+3_600_000_000_000L;var requestedCancel=false
                while(System.nanoTime()<deadline){
                    if(stopped.get()&&!requestedCancel){
                        if(IppProtocol.CANCEL_JOB !in caps.operations)throw IOException("Cancel the job on the printer; remote cancellation is unavailable.")
                        client.cancel(printer.uri,id);requestedCancel=true
                    }
                    val status=client.job(printer.uri,id)
                    if(status.completedWithErrors)throw IOException("The printer completed this job with errors. Check the printed sheets.")
                    when(status.state){
                        9->{postJob{job.start();job.complete()};return@execute}
                        7->{postJob{job.cancel()};return@execute}
                        8->throw IOException("The printer aborted this job. Check which sheets printed before retrying.")
                        4,6->postJob{job.block(if(requestedCancel)"Waiting for the printer to confirm cancellation"else"Check the printer: ${status.reasons.joinToString().take(180)}")}
                        else->postJob{job.start();job.setStatus(if(requestedCancel)"Waiting for cancellation confirmation"else"Printer is processing the document")}
                    }
                    Thread.sleep(1500)
                }
                throw IOException("No completion was confirmed within an hour. Check the printer before retrying.")
            }catch(e:Exception){
                val message=when{
                    stopped.get()&&!attempted->null
                    attempted->(e.message?:"The printer connection ended.")+" Check the printer before resending."
                    else->e.message?:"Could not prepare this job."
                }
                postJob{if(message==null)job.cancel()else job.fail(message)}
            }finally{runCatching{fd?.close()};file.delete();cancel.remove(localJobId)}
        }
    }
    override fun onRequestCancelPrintJob(job:PrintJob){cancel[job.id]?.set(true)?:run{job.cancel()}}
    override fun onDisconnected(){super.onDisconnected();cancel.values.forEach{it.set(true)}}
    override fun onDestroy(){cancel.values.forEach{it.set(true)};worker.shutdown();probes.shutdown();super.onDestroy()}
    companion object {
        fun androidMedia(p:PaperSize):PrintAttributes.MediaSize=when(p){PaperSize.A4->PrintAttributes.MediaSize.ISO_A4;PaperSize.A5->PrintAttributes.MediaSize.ISO_A5;PaperSize.LETTER->PrintAttributes.MediaSize.NA_LETTER;PaperSize.LEGAL->PrintAttributes.MediaSize.NA_LEGAL;PaperSize.PHOTO->PrintAttributes.MediaSize.NA_INDEX_4X6}
    }
}
