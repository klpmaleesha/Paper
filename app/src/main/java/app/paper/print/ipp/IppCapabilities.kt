// SPDX-License-Identifier: GPL-3.0-or-later
package app.paper.print.ipp

import app.paper.print.*
import org.json.JSONObject
import java.security.MessageDigest
import java.io.*
import kotlin.math.roundToInt

data class IppResolution(val x:Int,val y:Int,val units:Int=3) {
    val xDpi get()=if(units==3)x else (x*2.54).roundToInt()
    val yDpi get()=if(units==3)y else (y*2.54).roundToInt()
    val id get()="$x-$y-$units"
    fun bytes()=ByteArrayOutputStream().also{b->DataOutputStream(b).apply{writeInt(x);writeInt(y);writeByte(units)}}.toByteArray()
    fun encode()=JSONObject().put("x",x).put("y",y).put("units",units)
    companion object {
        fun from(bytes:ByteArray):IppResolution?=runCatching{require(bytes.size==9);val input=DataInputStream(ByteArrayInputStream(bytes));IppResolution(input.readInt(),input.readInt(),input.readUnsignedByte()).also{require(it.units in 3..4&&it.xDpi in 72..9600&&it.yDpi in 72..9600)}}.getOrNull()
    }
}

data class IppCapabilities(
    val name:String,val formats:Set<String>,val media:Set<String>,val sides:Set<String>,val colourModes:Set<String>,
    val operations:Set<Int>,val maxCopies:Int=1,val accepting:Boolean=true,val resolutions:Set<IppResolution> = emptySet(),val printerState:Int=3
) {
    val supported get()="application/pdf" in formats&&IppProtocol.PRINT_JOB in operations&&IppProtocol.GET_JOB in operations&&paperSizes.isNotEmpty()&&resolutions.isNotEmpty()&&"one-sided" in sides&&colourModes.any{it in setOf("color","monochrome")}
    val unsupportedReason get()=when {
        "application/pdf" !in formats->"This printer does not advertise direct PDF printing. A raster driver is needed."
        IppProtocol.PRINT_JOB !in operations->"This printer does not advertise the Print-Job operation."
        IppProtocol.GET_JOB !in operations->"This printer does not advertise job-status tracking."
        paperSizes.isEmpty()->"No supported paper sizes were reported."
        resolutions.isEmpty()->"No supported printer resolutions were reported."
        "one-sided" !in sides->"This printer did not report its supported sides modes."
        colourModes.none{it in setOf("color","monochrome")}->"This printer did not report an explicit colour or monochrome mode."
        else->null
    }
    val paperSizes get()=media.mapNotNull{mediaToPaper[it]}.toSet()
    val profile get()=PrinterCapabilities("ipp",name,if(supported)Compatibility.EXPERIMENTAL else Compatibility.UNSUPPORTED,paperSizes,
        colour="color" in colourModes,grayscale="monochrome" in colourModes,
        automaticDuplex=buildSet{if("two-sided-long-edge" in sides)add(Sides.LONG);if("two-sided-short-edge" in sides)add(Sides.SHORT)},
        note=unsupportedReason?:"PDF-over-IPP capabilities checked. Physical printing has not been verified by Paper.")
    fun encode()=JSONObject().put("name",name).put("formats",formats.toList()).put("media",media.toList()).put("sides",sides.toList()).put("colourModes",colourModes.toList()).put("operations",operations.toList()).put("maxCopies",maxCopies).put("accepting",accepting).put("resolutions",org.json.JSONArray().also{a->resolutions.forEach{a.put(it.encode())}}).put("printerState",printerState)
    companion object {
        private fun printerName(text:String?)=text.orEmpty().filterNot{it.isISOControl()}.trim().take(100).ifBlank{"Network printer"}
        val mediaToPaper=mapOf("iso_a4_210x297mm" to PaperSize.A4,"iso_a5_148x210mm" to PaperSize.A5,"na_letter_8.5x11in" to PaperSize.LETTER,"na_legal_8.5x14in" to PaperSize.LEGAL,"na_index-4x6_4x6in" to PaperSize.PHOTO,"oe_photo-4x6_4x6in" to PaperSize.PHOTO)
        fun from(r:IppResponse):IppCapabilities {
            r.requireSuccess()
            fun strings(key:String)=r.texts(key).filter{it.length<=256}.take(256).toSet()
            val copies=r.attributes["copies-supported"]?.firstOrNull()?.takeIf{it.tag==0x33}?.bytes
            val max=if(copies?.size==8)java.io.DataInputStream(java.io.ByteArrayInputStream(copies)).let{it.readInt();it.readInt().coerceIn(1,999)}else 1
            val resolutions=r.attributes["printer-resolution-supported"].orEmpty().take(64).filter{it.tag==0x32}.mapNotNull{IppResolution.from(it.bytes)}.toSet()
            return IppCapabilities(printerName(r.texts("printer-name").firstOrNull()),strings("document-format-supported"),strings("media-supported"),strings("sides-supported"),strings("print-color-mode-supported"),r.attributes["operations-supported"].orEmpty().take(256).mapNotNull{it.int()}.toSet(),max,r.bool("printer-is-accepting-jobs")?:false,resolutions,r.int("printer-state")?.takeIf{it in 3..5}?:5)
        }
        fun decode(j:JSONObject):IppCapabilities {
            fun strings(key:String)=j.optJSONArray(key)?.let{a->(0 until minOf(a.length(),256)).map{a.getString(it)}.filter{it.length<=256}.toSet()}.orEmpty()
            val ops=j.optJSONArray("operations")
            val resolutions=j.optJSONArray("resolutions")?.let{a->(0 until minOf(a.length(),64)).mapNotNull{i->runCatching{val r=a.getJSONObject(i);IppResolution.from(IppResolution(r.getInt("x"),r.getInt("y"),r.getInt("units")).bytes())}.getOrNull()}.toSet()}.orEmpty()
            return IppCapabilities(printerName(j.optString("name")),strings("formats"),strings("media"),strings("sides"),strings("colourModes"),ops?.let{a->(0 until minOf(a.length(),256)).map{a.getInt(it)}.toSet()}.orEmpty(),j.optInt("maxCopies",1).coerceIn(1,999),j.optBoolean("accepting",false),resolutions,j.optInt("printerState",5))
        }
    }
}
data class NetworkPrinter(val uri:String,val capabilities:IppCapabilities) {
    val id get()=MessageDigest.getInstance("SHA-256").digest(uri.toByteArray()).take(12).joinToString(""){"%02x".format(it.toInt()and 255)}
    fun encode()=JSONObject().put("uri",uri).put("capabilities",capabilities.encode())
    companion object {fun decode(j:JSONObject)=NetworkPrinter(PrinterAddress.parse(j.getString("uri")).canonical,IppCapabilities.decode(j.getJSONObject("capabilities")))}
}
