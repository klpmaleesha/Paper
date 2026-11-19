// SPDX-License-Identifier: GPL-3.0-or-later
package app.paper.print

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import io.github.noble6.canonivec.UsbPrinter
import java.io.File
import java.io.IOException
import kotlin.math.ceil

/** Canon binary raster, from Gutenprint's G2000/G2100 family driver; no PWG payload. */
object CanonRaster {
    private var loaded=false
    @Synchronized private fun load(){
        if(loaded)return
        if("arm64-v8a" !in Build.SUPPORTED_ABIS)throw IOException("This experimental native driver currently needs a 64-bit ARM phone. Reported ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
        try{System.loadLibrary("paper_canon");loaded=true}catch(e:UnsatisfiedLinkError){throw IOException("The native Canon driver could not be loaded: ${e.message}",e)}
    }
    private external fun nativeGeometry(data:String,paper:String,gray:Boolean,quality:Int,photo:Boolean):DoubleArray
    private external fun nativeEncode(data:String,paper:String,gray:Boolean,quality:Int,photo:Boolean,rows:RasterRows,width:Int,height:Int,path:String,pageIndex:Int,first:Boolean,last:Boolean)
    private fun dataDirectory(context:Context):File {
        val dest=File(context.filesDir,"gutenprint-5.3.5")
        if(File(dest,"ready-v1").isFile)return dest
        fun copy(asset:String,target:File){
            val children=context.assets.list(asset).orEmpty()
            if(children.isEmpty()){target.parentFile?.mkdirs();context.assets.open(asset).use{input->target.outputStream().use{input.copyTo(it)}}}
            else{target.mkdirs();children.forEach{copy("$asset/$it",File(target,it))}}
        }
        copy("gutenprint",dest);File(dest,"ready-v1").writeText("5.3.5")
        return dest
    }
    fun render(context:Context,file:File,s:PrintSettings,output:File,cancelled:()->Boolean,progress:(Int,Int)->Unit){
        load();val data=dataDirectory(context)
        val paper=when(s.paper){PaperSize.PHOTO->"w288h432";PaperSize.LEGAL->"Legal";PaperSize.LETTER->"Letter";else->s.paper.name}
        val quality=when(s.quality){RenderQuality.DRAFT->0;RenderQuality.NORMAL->1;RenderQuality.HIGH->2}
        val box=nativeGeometry(data.path,paper,!s.color,quality,s.photoPaper)
        require(box.size==6&&box.all{it.isFinite()}){"The native print area is invalid."}
        UsbJobs.log("Encoder=Gutenprint 5.3.5; profile=bjc-PIXMA-G2100; format=Canon binary raster")
        UsbJobs.log("Print area left=${box[0]}, top=${box[1]}, width=${box[2]}, height=${box[3]} pt; native jobID=00000001")
        ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use{fd->PdfRenderer(fd).use{pdf->
            require(pdf.pageCount<=200){"Use no more than 200 prepared sides in one USB pass."}
            UsbJobs.log("Prepared sides=${pdf.pageCount}")
            for(index in 0 until pdf.pageCount){
                if(cancelled())throw UsbPrinter.CancelledException()
                progress(index+1,pdf.pageCount)
                pdf.openPage(index).use{page->
                    RasterRows(page,box,s.quality.dpi,cancelled).use{rows->
                        nativeEncode(data.path,paper,!s.color,quality,s.photoPaper,rows,rows.width,rows.height,output.path,index,index==0,index==pdf.pageCount-1)
                    }
                }
                if(cancelled())throw UsbPrinter.CancelledException()
            }
        }}
        CanonStream.validate(output)
    }
}

class RasterRows(private val page:PdfRenderer.Page,box:DoubleArray,dpi:Int,private val cancelled:()->Boolean):AutoCloseable {
    val width=ceil(box[2]*dpi/72.0).toInt()
    val height=ceil(box[3]*dpi/72.0).toInt()
    private val band=96
    private val bitmap=Bitmap.createBitmap(width,band,Bitmap.Config.ARGB_8888)
    private val pixels=IntArray(width*band)
    private var top=-1
    private val matrix=Matrix().apply{
        if((page.width>page.height)!=(box[4]>box[5])){postRotate(90f);postTranslate(page.height.toFloat(),0f)}
        postTranslate(-box[0].toFloat(),-box[1].toFloat())
        postScale((width/box[2]).toFloat(),(height/box[3]).toFloat())
    }
    // Called from the native encoder on the same worker thread.
    @Suppress("unused") fun readRow(row:Int,result:ByteArray):Boolean {
        if(cancelled())return false
        if(row !in 0 until height||result.size<width*3)throw IOException("The native encoder requested an invalid source row.")
        if(top<0||row<top||row>=top+band){
            top=row;bitmap.eraseColor(Color.WHITE)
            val transform=Matrix(matrix).apply{postTranslate(0f,-top.toFloat())}
            page.render(bitmap,null,transform,PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            bitmap.getPixels(pixels,0,width,0,0,width,band)
        }
        var to=0;val start=(row-top)*width
        for(x in 0 until width){val rgb=pixels[start+x];result[to++]=(rgb ushr 16).toByte();result[to++]=(rgb ushr 8).toByte();result[to++]=rgb.toByte()}
        return true
    }
    override fun close(){bitmap.recycle()}
}
