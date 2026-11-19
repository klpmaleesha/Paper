package app.paper.print

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException

class UsbReliabilityTest {
    @Test fun silentEndpointStopsAtNinetySeconds(){
        val watch=TransferWatch(1000)
        assertFalse(watch.timedOut(90999,false));assertTrue(watch.timedOut(91000,false))
    }
    @Test fun printerActivityAllowsPrintingAndRestartsTheSilenceWindow(){
        val watch=TransferWatch(0)
        assertFalse(watch.timedOut(180000,true))
        assertFalse(watch.timedOut(269999,false));assertTrue(watch.timedOut(270000,false))
    }
    private fun stream(body:String,action:(File)->Unit){
        val file=File.createTempFile("paper-wire-",".prn")
        try{file.writeBytes(body.toByteArray(Charsets.ISO_8859_1));action(file)}finally{file.delete()}
    }
    private val nativeHeader="<?xml version=\"1.0\"?><ivec:operation>StartJob</ivec:operation><vcn:ijoperation>ModeShift</vcn:ijoperation>\u001b[K"
    private val end="<ivec:operation>EndJob</ivec:operation>"
    @Test fun nativeCanonJobPassesPreflight(){stream(nativeHeader+"\u0000".repeat(1000)+end){CanonStream.validate(it)}}
    @Test(expected=IOException::class) fun oldPwgJobIsRejected(){stream(nativeHeader+"PWGRaster RaS2"+"\u0000".repeat(1000)+end){CanonStream.validate(it)}}
    @Test(expected=IOException::class) fun truncatedNativeJobIsRejected(){stream(nativeHeader+"\u0000".repeat(1000)){CanonStream.validate(it)}}
    @Test(expected=IOException::class) fun missingBinaryRasterIsRejected(){stream(nativeHeader.replace("\u001b[K","")+"\u0000".repeat(1000)+end){CanonStream.validate(it)}}
    @Test fun preflightHandlesJobsLargerThanItsReadWindows(){stream(nativeHeader+"\u0000".repeat(20000)+end){CanonStream.validate(it)}}
}
