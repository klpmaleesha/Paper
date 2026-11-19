package app.paper.print

import app.paper.print.ipp.*
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger

/** Real loopback HTTP exercises framing and no-resend behaviour without a printer. */
class IppTransportTest {
    private fun server(reply:(ByteArray)->Pair<Int,ByteArray>,run:(String)->Unit){
        val server=ServerSocket().apply{bind(InetSocketAddress("127.0.0.1",0))};val failure=AtomicReference<Throwable?>()
        val thread=Thread{
            try{while(!server.isClosed){server.accept().use{socket->
                socket.soTimeout=3000;val input=socket.getInputStream();val header=ByteArrayOutputStream()
                while(true){val n=input.read();if(n<0)throw EOFException();header.write(n);val b=header.toByteArray();if(b.size>=4&&b.takeLast(4)==listOf<Byte>(13,10,13,10))break;require(b.size<16384)}
                val length=header.toString("ISO-8859-1").lineSequence().first{it.startsWith("Content-Length:",true)}.substringAfter(':').trim().toInt()
                val request=ByteArray(length);DataInputStream(input).readFully(request);val(code,bytes)=reply(request)
                socket.getOutputStream().use{out->out.write("HTTP/1.1 $code Test\r\nContent-Type: application/ipp\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray());out.write(bytes);out.flush()}
            }}}catch(e:Throwable){if(!(e is SocketException&&server.isClosed))failure.set(e)}
        }.apply{isDaemon=true;start()}
        try{run("ipp://127.0.0.1:${server.localPort}/ipp/print")}finally{server.close();thread.join(4000);failure.get()?.let{throw it}}
    }
    private fun response(request:ByteArray,status:Int=0,idOffset:Int=0,jobId:Int?=null):ByteArray=ByteArrayOutputStream().also{b->DataOutputStream(b).apply{writeByte(2);writeByte(0);writeShort(status);writeInt(DataInputStream(ByteArrayInputStream(request,4,4)).readInt()+idOffset);if(jobId!=null){writeByte(2);writeByte(0x21);writeShort(6);writeBytes("job-id");writeShort(4);writeInt(jobId)};writeByte(3)}}.toByteArray()
    private fun pdf(run:(File)->Unit){val f=File.createTempFile("paper-transport-",".pdf");try{f.writeText("%PDF-1.4\nSynthetic transport fixture\n%%EOF");run(f)}finally{f.delete()}}
    private fun capabilityResponse(request:ByteArray):ByteArray=ByteArrayOutputStream().also{b->DataOutputStream(b).apply{
        write(response(request).dropLast(1).toByteArray());writeByte(4)
        fun attribute(tag:Int,name:String,value:ByteArray){writeByte(tag);writeShort(name.length);writeBytes(name);writeShort(value.size);write(value)}
        attribute(0x42,"printer-name","Simulated printer".toByteArray())
        attribute(0x49,"document-format-supported","application/pdf".toByteArray())
        attribute(0x44,"media-supported","iso_a4_210x297mm".toByteArray())
        attribute(0x44,"sides-supported","one-sided".toByteArray())
        attribute(0x44,"print-color-mode-supported","monochrome".toByteArray())
        attribute(0x23,"operations-supported",byteArrayOf(0,0,0,2));attribute(0x23,"",byteArrayOf(0,0,0,9))
        attribute(0x32,"printer-resolution-supported",byteArrayOf(0,0,1,44,0,0,1,44,3))
        attribute(0x22,"printer-is-accepting-jobs",byteArrayOf(1));attribute(0x23,"printer-state",byteArrayOf(0,0,0,3));writeByte(3)
    }}.toByteArray()
    @Test fun capabilityQueryReadsAnIndependentPrinterResponse(){server({r->assertEquals(11,DataInputStream(ByteArrayInputStream(r,2,2)).readUnsignedShort());200 to capabilityResponse(r)}){uri->val c=IppClient().capabilities(uri);assertTrue(c.supported);assertEquals("Simulated printer",c.name);assertFalse(c.profile.colour);assertEquals(setOf(IppResolution(300,300)),c.resolutions);assertTrue(c.accepting);assertEquals(3,c.printerState)}}
    @Test fun printJobSendsExactlyOnePdfAndReadsAssignedId(){
        val calls=AtomicInteger();pdf{f->server({request->calls.incrementAndGet();assertArrayEquals(f.readBytes(),request.takeLast(f.length().toInt()).toByteArray());assertTrue(request.toString(Charsets.ISO_8859_1).contains("application/pdf"));200 to response(request,jobId=42)}){uri->assertEquals(42,IppClient().submit(uri,f,IppJobOptions("iso_a4_210x297mm","monochrome")))}};assertEquals(1,calls.get())
    }
    @Test fun mismatchedResponseIsUncertainAndIsNotRetried(){val calls=AtomicInteger();pdf{f->server({r->calls.incrementAndGet();200 to response(r,idOffset=1,jobId=42)}){uri->try{IppClient().submit(uri,f,IppJobOptions("iso_a4_210x297mm",null));fail("Expected uncertain result")}catch(_:IppSubmissionUncertain){}}};assertEquals(1,calls.get())}
    @Test fun explicitIppErrorIsARejection(){pdf{f->server({r->200 to response(r,status=0x040b)}){uri->try{IppClient().submit(uri,f,IppJobOptions("iso_a4_210x297mm",null));fail("Expected rejection")}catch(e:IOException){assertFalse(e is IppSubmissionUncertain);assertTrue(e.message!!.contains("40b"))}}}}
    @Test fun cancellationBeforeSubmissionSendsNothing(){val calls=AtomicInteger();pdf{f->server({r->calls.incrementAndGet();200 to response(r,jobId=1)}){uri->try{IppClient().submit(uri,f,IppJobOptions("iso_a4_210x297mm",null)){true};fail("Expected cancellation")}catch(_:InterruptedIOException){}}};assertEquals(0,calls.get())}
}
