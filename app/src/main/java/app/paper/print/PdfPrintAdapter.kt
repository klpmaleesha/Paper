package app.paper.print

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.print.*
import android.print.pdf.PrintedPdfDocument
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlin.math.min

/** The OS owns printer selection. Copies and manual duplex are already imposed in the prepared PDF. */
class PdfPrintAdapter(private val context:Context,private val file:File,private val name:String):PrintDocumentAdapter(){
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private var attributes=PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).setResolution(PrintAttributes.Resolution("paper","Paper",300,300)).setMinMargins(PrintAttributes.Margins.NO_MARGINS).build()
    private val count=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use{fd->PdfRenderer(fd).use{it.pageCount}}
    override fun onLayout(oldAttributes:PrintAttributes?,newAttributes:PrintAttributes,cancellationSignal:CancellationSignal,callback:LayoutResultCallback,extras:android.os.Bundle?){
        if(cancellationSignal.isCanceled){callback.onLayoutCancelled();return}
        attributes=newAttributes
        callback.onLayoutFinished(PrintDocumentInfo.Builder(name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(count).build(),oldAttributes!=newAttributes)
    }
    override fun onWrite(ranges:Array<out PageRange>,destination:ParcelFileDescriptor,signal:CancellationSignal,callback:WriteResultCallback){
        val attrs=attributes
        worker.execute{
            try{
                val included=(0 until count).filter{i->ranges.any{i>=it.start&&i<=it.end}}
                signal.throwIfCanceled()
                PrintedPdfDocument(context,attrs).usePdf{pdf->
                    ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use{fd->PdfRenderer(fd).use{renderer->
                        included.forEach{index->
                            signal.throwIfCanceled();val page=pdf.startPage(index)
                            val b=PdfOutput.bitmap(renderer,index,200)
                            try{
                                page.canvas.drawColor(Color.WHITE)
                                val rect=page.info.contentRect
                                val scale=min(rect.width().toFloat()/b.width,rect.height().toFloat()/b.height)
                                val x=rect.left+(rect.width()-b.width*scale)/2;val y=rect.top+(rect.height()-b.height*scale)/2
                                val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                                if(attrs.colorMode==PrintAttributes.COLOR_MODE_MONOCHROME)paint.colorFilter=ColorMatrixColorFilter(ColorMatrix().apply{setSaturation(0f)})
                                page.canvas.drawBitmap(b,null,RectF(x,y,x+b.width*scale,y+b.height*scale),paint)
                            }finally{b.recycle()}
                            pdf.finishPage(page)
                        }
                    }}
                    signal.throwIfCanceled();FileOutputStream(destination.fileDescriptor).use{pdf.writeTo(it)}
                }
                // Report original page indexes rather than ALL_PAGES when the user selected a range.
                main.post{callback.onWriteFinished(included.map{PageRange(it,it)}.toTypedArray())}
            }catch(e:Exception){main.post{if(signal.isCanceled)callback.onWriteCancelled()else callback.onWriteFailed(e.message?:"Could not prepare print output.")}}
        }
    }
    override fun onFinish(){worker.shutdown()}
}
