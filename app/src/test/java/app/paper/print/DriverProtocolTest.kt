// SPDX-License-Identifier: GPL-3.0-or-later
package app.paper.print

import io.github.noble6.canonivec.core.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class DriverProtocolTest {
    private fun header(w:Int,h:Int,gray:Boolean=false)=PwgHeader("plain","A4",600,intArrayOf(595,842),intArrayOf(10,14,586,834),IntArray(4),w,h,gray)
    private fun u32(b:ByteArray,o:Int)=((b[o].toInt()and 255)shl 24)or((b[o+1].toInt()and 255)shl 16)or((b[o+2].toInt()and 255)shl 8)or(b[o+3].toInt()and 255)
    // Independent decoder for the PWG row-repeat and pixel-repeat encoding.
    private fun decode(bytes:ByteArray):List<ByteArray>{
        assertEquals("RaS2",String(bytes,0,4,Charsets.US_ASCII))
        val width=u32(bytes,4+372);val height=u32(bytes,4+376);val channels=u32(bytes,4+388)/8
        var cursor=1800;val result=mutableListOf<ByteArray>()
        while(result.size<height){
            val repeats=(bytes[cursor++].toInt()and 255)+1;val row=ByteArray(width*channels);var x=0
            while(x<row.size){val tag=bytes[cursor++].toInt()and 255
                if(tag<128){repeat(tag+1){System.arraycopy(bytes,cursor,row,x,channels);x+=channels};cursor+=channels}
                else{val length=(257-tag)*channels;System.arraycopy(bytes,cursor,row,x,length);x+=length;cursor+=length}
            };repeat(repeats){result.add(row.copyOf())}
        };assertEquals(bytes.size,cursor);return result
    }
    @Test fun pwgRoundTripHandlesRunBoundariesAndRepeatedRows(){
        val w=301;val h=520;val random=java.util.Random(19)
        val rows=Array(h){y->ByteArray(w*3){x->if(y in 100..400)85 else if(x%7==0)random.nextInt().toByte()else(x/30).toByte()}}
        val encoder=PwgPageEncoder(header(w,h));rows.forEach{encoder.writeRow(it)}
        val actual=decode(encoder.finish());assertEquals(h,actual.size);rows.forEachIndexed{i,row->assertArrayEquals("row $i",row,actual[i])}
    }
    @Test fun fileBackedEncoderMatchesMemoryEncoderExactly(){
        val tmp=File.createTempFile("pwg-test-",".bin")
        try{
            val rows=Array(300){y->ByteArray(33*3){x->(x+y/100).toByte()}}
            val memory=PwgPageEncoder(header(33,300));rows.forEach{memory.writeRow(it)};val expected=memory.finish()
            tmp.outputStream().buffered().use{out->val disk=PwgPageEncoder(header(33,300),out);rows.forEach{disk.writeRow(it)};disk.finish()}
            assertArrayEquals(expected,tmp.readBytes())
        }finally{tmp.delete()}
    }
    @Test fun grayscalePwgUsesOneChannel(){
        val encoder=PwgPageEncoder(header(7,2,true));val row=byteArrayOf(0,0,1,17,90,127,-1);repeat(2){encoder.writeRow(row)}
        val bytes=encoder.finish();assertEquals(18,u32(bytes,4+400));decode(bytes).forEach{assertArrayEquals(row,it)}
    }
    @Test fun streamFromDiskMatchesByteArrayJob(){
        val tmp=File.createTempFile("ivec-test-",".pwg")
        val data=ByteArray(71000){(it%253).toByte()};tmp.writeBytes(data)
        fun output(disk:Boolean):ByteArray{val out=ByteArrayOutputStream();val j=Ivec.Job(out,2,true,"urn:uuid:test",clock={"20261116000000"});j.startJob();j.setConfiguration("iso_a4_210x297mm","stationery",false,true,false,"best");if(disk)j.sendPage(false,tmp)else j.sendPage(false,data);j.endJob();return out.toByteArray()}
        try{assertArrayEquals(output(false),output(true))}finally{tmp.delete()}
    }
    @Test fun profileReallyIsG3010AndNotClaimedToBeG2010(){
        val db=ModelDatabase.parse(File(System.getProperty("paper.profile")).readText())
        val m=db.byId("canong3010")!!;assertNull(db.byId("canong2010"));assertFalse(m.duplex)
        assertEquals(600,m.resolution);assertEquals(4800,m.size("A4")!!.printW);assertEquals(6826,m.size("A4")!!.printH)
        assertNotNull(m.size("legal"));assertNotNull(m.media("glossygold"));assertTrue(m.hasDraft);assertTrue(m.hasHigh)
    }
    @Test fun partialStatusDocumentsAreAccumulated(){
        val feed=StatusFeed();val b="<cmd><ivec:status>stopped</ivec:status><ivec:status_detail>MediaEmpty</ivec:status_detail><ivec:current_support_code>1000</ivec:current_support_code></cmd>".toByteArray()
        assertNull(feed.feed(b,30));val rest=b.copyOfRange(30,b.size);val status=feed.feed(rest,rest.size)!!
        assertEquals(PrinterStatus.Kind.PAPER_OUT,status.kind);assertTrue(status.blocking);assertTrue(status.message.contains("1000"))
    }
    @Test fun monochromeAndPhysicalDuplexOffReachProtocol(){
        val out=ByteArrayOutputStream();val job=Ivec.Job(out,2,true,"urn:uuid:test",clock={"20261116000000"})
        job.startJob();job.setConfiguration("iso_a4_210x297mm","stationery",false,true,false,"best");job.sendPage(false,byteArrayOf(1,2));job.endJob()
        val text=out.toString("UTF-8");assertTrue(text.contains("<ivec:printcolormode>monochrome</ivec:printcolormode>"));assertTrue(text.contains("<ivec:duplexprint>OFF</ivec:duplexprint>"));assertTrue(text.contains("<ivec:datasize>2</ivec:datasize>"));assertTrue(text.contains("<vcn:nextpage>OFF</vcn:nextpage>"))
    }
}
