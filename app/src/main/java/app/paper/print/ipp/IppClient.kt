// SPDX-License-Identifier: GPL-3.0-or-later
package app.paper.print.ipp

import java.io.*
import java.net.HttpURLConnection
import java.util.concurrent.atomic.AtomicInteger

data class IppJobOptions(val media:String,val colourMode:String?,val sides:String="one-sided",val copies:Int=1,val resolution:IppResolution?=null)
class IppSubmissionUncertain(cause:Exception):IOException("The connection ended during submission. The printer may already have this document. Check its queue before creating another job.",cause)

/** No authentication storage, redirect following, certificate bypass, or submission retries. */
class IppClient {
    private val ids=AtomicInteger(1)
    fun capabilities(uri:String):IppCapabilities=IppCapabilities.from(call(uri,IppProtocol.GET_PRINTER,listOf(IppProtocol.texts("requested-attributes",listOf(
        "printer-name","document-format-supported","media-supported","sides-supported","print-color-mode-supported","operations-supported","copies-supported","printer-is-accepting-jobs","printer-resolution-supported","printer-state")))))
    fun submit(uri:String,file:File,options:IppJobOptions,cancelled:()->Boolean={false}):Int {
        require(file.isFile&&file.length() in 1..104_857_600){"Use a PDF no larger than 100 MB."}
        val attrs=buildList {
            add(IppProtocol.text("job-name","Paper document",0x42))
            add(IppProtocol.boolean("ipp-attribute-fidelity",true))
            add(IppProtocol.text("document-format","application/pdf",0x49))
            add(IppProtocol.text("media",options.media,group=2))
            if(options.colourMode!=null)add(IppProtocol.text("print-color-mode",options.colourMode,group=2))
            add(IppProtocol.text("sides",options.sides,group=2))
            if(options.copies>1)add(IppProtocol.integer("copies",options.copies))
            options.resolution?.let{add(IppAttribute(2,0x32,"printer-resolution",listOf(it.bytes())))}
        }
        val response=call(uri,IppProtocol.PRINT_JOB,attrs,file,cancelled)
        return response.int("job-id")?.takeIf{it>0}?:throw IOException("The printer accepted the request but returned no job ID. Check its queue before printing again.")
    }
    fun job(uri:String,id:Int)=IppJobStatus.from(call(uri,IppProtocol.GET_JOB,listOf(IppProtocol.integer("job-id",id,group=1),IppProtocol.texts("requested-attributes",listOf("job-state","job-state-reasons","job-impressions-completed")))))
    fun cancel(uri:String,id:Int){call(uri,IppProtocol.CANCEL_JOB,listOf(IppProtocol.integer("job-id",id,group=1)))}
    internal fun call(uri:String,operation:Int,attrs:List<IppAttribute>,file:File?=null,cancelled:()->Boolean={false}):IppResponse {
        val address=PrinterAddress.parse(uri);val id=ids.getAndIncrement();val header=IppProtocol.request(operation,id,address.canonical,attrs)
        val connection=address.http.toURL().openConnection() as HttpURLConnection
        var sending=false
        try {
            connection.requestMethod="POST";connection.doOutput=true;connection.instanceFollowRedirects=false
            connection.connectTimeout=6000;connection.readTimeout=if(operation==IppProtocol.GET_PRINTER)6000 else 15000
            connection.setRequestProperty("Content-Type","application/ipp");connection.setRequestProperty("Accept","application/ipp")
            connection.setFixedLengthStreamingMode(header.size.toLong()+(file?.length()?:0))
            if(cancelled())throw InterruptedIOException("Cancelled before sending")
            connection.outputStream.use{out->
                sending=file!=null;out.write(header)
                file?.inputStream()?.use{input->val b=ByteArray(16384);while(true){if(cancelled())throw InterruptedIOException("Cancelled during submission");val n=input.read(b);if(n<0)break;out.write(b,0,n)}}
            }
            val code=connection.responseCode
            if(code==401||code==403)throw IOException("This printer requires authentication, which this release does not support.")
            if(code!=200)throw IOException("Printer HTTP error $code. Check the printer address and queue.")
            val bytes=connection.inputStream.use{input->val out=ByteArrayOutputStream();val b=ByteArray(4096);while(true){val n=input.read(b);if(n<0)break;if(out.size()+n>2_097_152)throw IOException("Printer response exceeded the size limit");out.write(b,0,n)};out.toByteArray()}
            val response=IppProtocol.decode(bytes)
            if(response.requestId!=id)throw IOException("The printer response did not match this request.")
            // A complete IPP error is a definite rejection; connection/parse errors remain uncertain.
            sending=false
            return response.requireSuccess()
        }catch(e:Exception){if(sending)throw IppSubmissionUncertain(e);throw e}finally{connection.disconnect()}
    }
}
