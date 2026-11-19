// SPDX-License-Identifier: GPL-3.0-or-later
package app.paper.print

enum class Compatibility(val label:String) { VERIFIED("Verified"), EXPERIMENTAL("Experimental"), UNSUPPORTED("Unsupported") }
data class PrinterCapabilities(
    val id:String,val name:String,val compatibility:Compatibility,
    val papers:Set<PaperSize>,val colour:Boolean,val grayscale:Boolean=true,
    val automaticDuplex:Set<Sides> = emptySet(),val photoPaper:Boolean=false,
    val qualities:Set<RenderQuality> = setOf(RenderQuality.NORMAL),val note:String=""
) {
    fun problems(s:PrintSettings):List<String> = buildList {
        if(compatibility==Compatibility.UNSUPPORTED)add("This printer has no compatible driver yet.")
        if(s.paper !in papers)add("${s.paper.name} is not reported as supported by this printer.")
        if(s.color&&!colour)add("This printer does not report colour printing.")
        if(!s.color&&!grayscale)add("This printer does not report a grayscale mode.")
        if(s.photoPaper&&!photoPaper)add("Photo-paper mode is not available for this printer.")
        if(s.quality !in qualities)add("${s.quality.label} is not available for this printer.")
    }
}
object PrinterProfiles {
    val g2010=PrinterCapabilities("canon-g2010-usb","Canon PIXMA G2010",Compatibility.EXPERIMENTAL,
        PaperSize.entries.toSet(),true,photoPaper=true,qualities=RenderQuality.entries.toSet(),
        note="Basic G2010 USB printing was user-confirmed with Paper 0.2.0. Individual colour, duplex and recovery checks remain pending. Two-sided printing is manual.")
}
