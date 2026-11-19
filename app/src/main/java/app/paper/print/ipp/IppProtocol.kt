// SPDX-License-Identifier: GPL-3.0-or-later
package app.paper.print.ipp

import java.io.*
import java.net.URI
import java.util.Locale

data class IppValue(val tag:Int,val bytes:ByteArray) {
    fun text()=bytes.toString(Charsets.UTF_8)
    fun int():Int?=if(bytes.size==4)DataInputStream(ByteArrayInputStream(bytes)).readInt()else null
    fun bool():Boolean?=if(bytes.size==1)bytes[0].toInt()!=0 else null
}
data class IppAttribute(val group:Int,val tag:Int,val name:String,val values:List<ByteArray>)
data class IppResponse(val status:Int,val requestId:Int,val attributes:Map<String,List<IppValue>>) {
    fun texts(name:String)=attributes[name].orEmpty().map{it.text()}
    fun int(name:String)=attributes[name]?.firstOrNull()?.int()
    fun bool(name:String)=attributes[name]?.firstOrNull()?.bool()
    fun requireSuccess():IppResponse {if(status>=0x0100)throw IOException("Printer rejected the request (IPP 0x${status.toString(16)}): ${texts("status-message").firstOrNull()?.take(180).orEmpty()}");return this}
}

/** Bounded RFC 8010 codec. Collections are skipped without leaking member attributes. */
object IppProtocol {
    const val PRINT_JOB=0x0002;const val CANCEL_JOB=0x0008;const val GET_JOB=0x0009;const val GET_PRINTER=0x000b
    fun text(name:String,value:String,tag:Int=0x44,group:Int=1)=IppAttribute(group,tag,name,listOf(value.toByteArray(Charsets.UTF_8)))
    fun texts(name:String,values:List<String>,tag:Int=0x44,group:Int=1)=IppAttribute(group,tag,name,values.map{it.toByteArray(Charsets.UTF_8)})
    fun integer(name:String,value:Int,tag:Int=0x21,group:Int=2)=IppAttribute(group,tag,name,listOf(ByteArrayOutputStream().also{b->DataOutputStream(b).writeInt(value)}.toByteArray()))
    fun boolean(name:String,value:Boolean,group:Int=1)=IppAttribute(group,0x22,name,listOf(byteArrayOf(if(value)1 else 0)))
    fun request(operation:Int,id:Int,uri:String,attributes:List<IppAttribute> = emptyList()):ByteArray {
        val all=listOf(text("attributes-charset","utf-8",0x47),text("attributes-natural-language","en",0x48),text("printer-uri",uri,0x45))+attributes
        val result=ByteArrayOutputStream();val out=DataOutputStream(result);out.writeByte(2);out.writeByte(0);out.writeShort(operation);out.writeInt(id)
        var group=0
        all.forEach{a->
            require(a.group in 1..2&&a.values.isNotEmpty());if(group!=a.group){require(a.group>group){"IPP groups must be ordered"};out.writeByte(a.group);group=a.group}
            a.values.forEachIndexed{i,v->val n=if(i==0)a.name.toByteArray(Charsets.US_ASCII)else byteArrayOf();require(n.size<=255&&v.size<=65535);out.writeByte(a.tag);out.writeShort(n.size);out.write(n);out.writeShort(v.size);out.write(v)}
        }
        out.writeByte(3);return result.toByteArray()
    }
    fun decode(bytes:ByteArray):IppResponse {
        if(bytes.size !in 9..2_097_152)throw IOException("Invalid IPP response size")
        val input=DataInputStream(ByteArrayInputStream(bytes));val major=input.readUnsignedByte();input.readUnsignedByte()
        if(major !in 1..2)throw IOException("Unsupported IPP version")
        val status=input.readUnsignedShort();val id=input.readInt();val attrs=linkedMapOf<String,MutableList<IppValue>>()
        var group=0;var previous="";var depth=0;var entries=0
        try{while(input.available()>0){
            val tag=input.readUnsignedByte()
            if(tag==3){if(depth!=0)throw IOException("Unclosed IPP collection");return IppResponse(status,id,attrs)}
            if(tag in 1..15){if(depth!=0)throw IOException("Unexpected group inside an IPP collection");group=tag;previous="";continue}
            if(group==0||++entries>16384)throw IOException("Malformed IPP attributes")
            val nl=input.readUnsignedShort();if(nl>255)throw IOException("IPP attribute name is too long")
            val name=ByteArray(nl).also{input.readFully(it)}.toString(Charsets.US_ASCII)
            val value=ByteArray(input.readUnsignedShort()).also{input.readFully(it)}
            if(tag==0x37){if(depth==0||value.isNotEmpty())throw IOException("Invalid IPP collection end");depth--;continue}
            if(depth>0){if(tag==0x34&&++depth>32)throw IOException("IPP collection is too deep");continue}
            if(nl>0)previous=name else if(previous.isEmpty())throw IOException("IPP value has no attribute name")
            if(tag==0x34){depth=1;continue}
            if(group==4||group==2)attrs.getOrPut(previous){mutableListOf()}.add(IppValue(tag,value))
            // Operation attributes include status-message, but never override printer/job attributes.
            else if(group==1&&previous !in attrs)attrs[previous]=mutableListOf(IppValue(tag,value))
        }}catch(e:EOFException){throw IOException("Truncated IPP response",e)}
        throw IOException("IPP response has no end marker")
    }
}

class PrinterAddress private constructor(val ipp:URI) {
    val canonical get()=ipp.toASCIIString()
    val http:URI get()=URI((if(ipp.scheme=="ipps")"https:"else"http:")+ipp.rawSchemeSpecificPart)
    companion object {
        fun parse(text:String):PrinterAddress {
            val u=try{URI(text.trim())}catch(e:Exception){throw IllegalArgumentException("Enter an address such as ipp://192.168.1.50:631/ipp/print",e)}
            val scheme=u.scheme?.lowercase(Locale.ROOT)
            require(scheme in setOf("ipp","ipps")&& !u.host.isNullOrBlank()&&u.rawUserInfo==null&&u.rawQuery==null&&u.rawFragment==null){"Use an ipp:// or ipps:// printer address without a password, query, or fragment."}
            require(u.port==-1||u.port in 1..65535){"The printer port must be between 1 and 65535."}
            val path=u.path.takeUnless{it.isNullOrEmpty()}?:"/ipp/print"
            require(path.startsWith('/')&&!path.contains('\u0000')&&!path.contains('\r')&&!path.contains('\n')){"Invalid printer path."}
            val authority=URI(scheme,null,u.host.lowercase(Locale.ROOT),if(u.port==-1)631 else u.port,null,null,null).toASCIIString()
            return PrinterAddress(URI(authority+(u.rawPath.takeUnless{it.isNullOrEmpty()}?:"/ipp/print")))
        }
    }
}
