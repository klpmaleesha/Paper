package app.paper.print

import app.paper.print.ipp.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PresetsAndCapabilitiesTest {
    @Test fun savedSettingsRetainPhotoMedia(){val s=PrintSettings(paper=PaperSize.PHOTO,photoPaper=true,quality=RenderQuality.HIGH);assertEquals(s,SettingsCodec.decode(JSONObject(SettingsCodec.encode(s).toString())))}
    @Test fun presetDoesNotCarryDocumentSelection(){val p=PrintPresets.create(" My preset ",PrintSettings(range="3-5",copies=7,filter=PageFilter.ODD,reverse=true));assertEquals("My preset",p.name);assertEquals("",p.settings.range);assertEquals(1,p.settings.copies);assertEquals(PageFilter.ALL,p.settings.filter);assertFalse(p.settings.reverse)}
    @Test fun applyingPresetKeepsCurrentSelection(){val s=PrintSettings(range="2-6",copies=4,filter=PageFilter.EVEN);val result=PrintPresets.apply(PrintPresets.defaults.first{it.id=="notes"},s);assertEquals("2-6",result.range);assertEquals(4,result.copies);assertEquals(PageFilter.EVEN,result.filter);assertFalse(result.color);assertEquals(2,result.perSheet)}
    @Test fun presetCannotOverwriteCalibratedBackOrder(){val s=PrintSettings(reverseBacks=false);assertFalse(PrintPresets.apply(PrintPresets.defaults.first{it.id=="notes"},s).reverseBacks)}
    @Test fun reverseSelectionIsKeptUnlessManualDuplexRequiresPairing(){val s=PrintSettings(reverse=true);assertTrue(PrintPresets.apply(PrintPresets.defaults.first{it.id=="document"},s).reverse);assertFalse(PrintPresets.apply(PrintPresets.defaults.first{it.id=="notes"},s).reverse)}
    @Test fun customPresetsRoundTrip(){val p=PrintPresets.create("Photo",PrintSettings(paper=PaperSize.PHOTO,photoPaper=true));assertEquals(listOf(p),PrintPresets.decode(PrintPresets.encode(listOf(p))))}
    @Test fun corruptPresetDoesNotHideValidOnes(){val p=PrintPresets.create("Valid",PrintSettings());val text=PrintPresets.encode(listOf(p));assertEquals(listOf(p),PrintPresets.decode("[{},"+text.substring(1)))}
    @Test fun bundledPresetsAreNotWrittenAsCustom(){assertEquals("[]",PrintPresets.encode(PrintPresets.defaults))}
    @Test(expected=IllegalArgumentException::class) fun blankNameRejected(){PrintPresets.create("  ",PrintSettings())}
    @Test(expected=IllegalArgumentException::class) fun overlongNameRejected(){PrintPresets.create("x".repeat(41),PrintSettings())}
    @Test fun duplexSetupRoundTrips(){val s=DuplexSetup(PrinterProfiles.g2010.id,ReloadFace.DOWN,ReloadEdge.LAST,false);assertEquals(s,DuplexSetup.decode(s.encode()))}
    @Test fun unconfirmedDuplexSetupIsNotTrusted(){assertNull(DuplexSetup.decode(DuplexSetup("test",ReloadFace.UP,ReloadEdge.FIRST,true,false).encode()))}
    @Test fun g2010IsExplicitlyExperimentalAndManual(){assertEquals(Compatibility.EXPERIMENTAL,PrinterProfiles.g2010.compatibility);assertTrue(PrinterProfiles.g2010.automaticDuplex.isEmpty());assertTrue(PrinterProfiles.g2010.problems(PrintSettings()).isEmpty())}
    @Test fun unsupportedSettingsHaveSpecificReasons(){val p=PrinterCapabilities("mono","Mono",Compatibility.EXPERIMENTAL,setOf(PaperSize.A4),false);val errors=p.problems(PrintSettings(paper=PaperSize.PHOTO,photoPaper=true,quality=RenderQuality.HIGH));assertEquals(4,errors.size)}
    private fun caps(formats:Set<String> = setOf("application/pdf"))=IppCapabilities("Office",formats,setOf("iso_a4_210x297mm"),setOf("one-sided","two-sided-short-edge"),setOf("color","monochrome"),setOf(IppProtocol.PRINT_JOB,IppProtocol.GET_JOB,IppProtocol.CANCEL_JOB),4,resolutions=setOf(IppResolution(300,300)))
    @Test fun ippOnlyExposesAdvertisedDuplex(){val c=caps();assertTrue(c.supported);assertEquals(setOf(Sides.SHORT),c.profile.automaticDuplex);assertTrue(c.profile.grayscale)}
    @Test fun genericBinaryMimeTypeIsNotPdfSupport(){assertFalse(caps(setOf("application/octet-stream","image/pwg-raster")).supported)}
    @Test fun missingStatusSupportIsUnsupported(){assertFalse(caps().copy(operations=setOf(IppProtocol.PRINT_JOB)).supported)}
    @Test fun missingColourAndPaperAreNotGuessed(){assertFalse(caps().copy(colourModes=emptySet()).supported);assertFalse(caps().copy(media=setOf("vendor_custom")).supported)}
    @Test fun networkPrinterRoundTripsWithStableIdentity(){val p=NetworkPrinter(PrinterAddress.parse("IPP://PRINTER.local/ipp/print").canonical,caps());val q=NetworkPrinter.decode(JSONObject(p.encode().toString()));assertEquals(p,q);assertEquals(p.id,q.id)}
    @Test fun resolutionPreservesPrinterUnits(){val r=IppResolution(118,236,4);assertEquals(300,r.xDpi);assertEquals(599,r.yDpi);assertEquals(r,IppResolution.from(r.bytes()))}
    @Test fun resolutionIsNotInventedIfMissing(){assertFalse(caps().copy(resolutions=emptySet()).supported);assertNull(IppResolution.from(byteArrayOf(1,2)))}
    @Test fun autoModeDoesNotImplyColourHardware(){assertFalse(caps().copy(colourModes=setOf("auto","monochrome")).profile.colour);assertFalse(caps().copy(colourModes=setOf("auto")).supported)}
}
