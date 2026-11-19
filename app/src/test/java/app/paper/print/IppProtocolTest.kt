package app.paper.print

import app.paper.print.ipp.*
import org.junit.Assert.*
import org.junit.Test
import java.io.*

class IppProtocolTest {
    private fun wire(build:DataOutputStream.()->Unit):ByteArray=ByteArrayOutputStream().also{DataOutputStream(it).apply{write(byteArrayOf(2,0,0,0,0,0,0,7));build();writeByte(3)}}.toByteArray()
    private fun DataOutputStream.attribute(tag:Int,name:String,value:ByteArray){writeByte(tag);writeShort(name.length);writeBytes(name);writeShort(value.size);write(value)}
    @Test fun decodesRepeatedPrinterFormats(){val bytes=wire{writeByte(4);attribute(0x49,"document-format-supported","application/pdf".toByteArray());attribute(0x49,"","image/pwg-raster".toByteArray())};val r=IppProtocol.decode(bytes);assertEquals(7,r.requestId);assertEquals(listOf("application/pdf","image/pwg-raster"),r.texts("document-format-supported"))}
    @Test fun collectionMembersCannotSpoofTopLevelCapabilities(){val bytes=wire{writeByte(4);attribute(0x34,"media-col-database",byteArrayOf());attribute(0x4a,"","document-format-supported".toByteArray());attribute(0x49,"","application/pdf".toByteArray());attribute(0x37,"",byteArrayOf());attribute(0x49,"document-format-supported","image/pwg-raster".toByteArray())};assertEquals(listOf("image/pwg-raster"),IppProtocol.decode(bytes).texts("document-format-supported"))}
    @Test fun readsIntegerAndBooleanValues(){val bytes=wire{writeByte(4);attribute(0x23,"printer-state",byteArrayOf(0,0,0,3));attribute(0x22,"printer-is-accepting-jobs",byteArrayOf(1))};val r=IppProtocol.decode(bytes);assertEquals(3,r.int("printer-state"));assertEquals(true,r.bool("printer-is-accepting-jobs"))}
    @Test(expected=IOException::class) fun truncatedResponseRejected(){IppProtocol.decode(wire{writeByte(4);writeByte(0x49);writeShort(20)})}
    @Test(expected=IOException::class) fun missingEndMarkerRejected(){IppProtocol.decode(wire{writeByte(4)}.dropLast(1).toByteArray())}
    @Test(expected=IOException::class) fun unnamedFirstValueRejected(){IppProtocol.decode(wire{writeByte(4);attribute(0x49,"","application/pdf".toByteArray())})}
    @Test(expected=IOException::class) fun unclosedCollectionRejected(){IppProtocol.decode(wire{writeByte(4);attribute(0x34,"collection",byteArrayOf())})}
    @Test fun encodedRequestHasRequiredCharsetFirst(){val bytes=IppProtocol.request(11,7,"ipp://printer.local:631/ipp/print");assertEquals("0200000b0000000701470012",bytes.take(12).joinToString(""){"%02x".format(it.toInt()and 255)});assertEquals(3,bytes.last().toInt())}
    @Test fun addressesNormalizeSchemeHostAndPort(){assertEquals("ipp://printer.local:631/ipp/print",PrinterAddress.parse(" IPP://PRINTER.local ").canonical);assertEquals("https://printer.local:631/queue",PrinterAddress.parse("ipps://printer.local/queue").http.toString())}
    @Test fun ipv6AddressIsPreserved(){assertEquals("http://[fd00::1]:631/ipp/print",PrinterAddress.parse("ipp://[fd00::1]/ipp/print").http.toString())}
    @Test fun escapedPathIsNotChangedToAnotherQueue(){assertEquals("http://printer.local:631/queues/one%2Ftwo",PrinterAddress.parse("ipp://printer.local/queues/one%2Ftwo").http.toString())}
    @Test fun completionWithErrorsIsNotSuccessful(){val r=IppResponse(0,1,mapOf("job-state" to listOf(IppValue(0x23,byteArrayOf(0,0,0,9))),"job-state-reasons" to listOf(IppValue(0x44,"job-completed-with-errors".toByteArray()))));assertTrue(IppJobStatus.from(r).completedWithErrors)}
    @Test(expected=IOException::class) fun missingJobStateCannotLookLikeProcessing(){IppJobStatus.from(IppResponse(0,1,emptyMap()))}
    @Test(expected=IllegalArgumentException::class) fun credentialsAreRejected(){PrinterAddress.parse("ipp://user:secret@printer.local/ipp/print")}
    @Test(expected=IllegalArgumentException::class) fun redirectsAndQueriesCannotBeEnteredAsPrinterAddresses(){PrinterAddress.parse("https://example.com/?printer=x")}
    @Test(expected=IllegalArgumentException::class) fun invalidPortIsRejected(){PrinterAddress.parse("ipp://printer.local:99999/ipp/print")}
    @Test(expected=IllegalArgumentException::class) fun encodedHeaderInjectionIsRejected(){PrinterAddress.parse("ipp://printer.local/ipp/%0d%0aprint")}
}
