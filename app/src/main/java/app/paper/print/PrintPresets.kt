// SPDX-License-Identifier: GPL-3.0-or-later
package app.paper.print

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Page selection and copy count belong to the document, never to a reusable preset. */
object SettingsCodec {
    fun reusable(s:PrintSettings)=s.copy(range="",filter=PageFilter.ALL,copies=1,reverse=false)
    fun encode(s:PrintSettings)=JSONObject().put("color",s.color).put("sides",s.sides.name).put("paper",s.paper.name)
        .put("landscape",s.landscape).put("perSheet",s.perSheet).put("marginMm",s.marginMm).put("scale",s.scale)
        .put("quality",s.quality.name).put("collate",s.collate).put("footer",s.footer)
        .put("reverseBacks",s.reverseBacks).put("photoPaper",s.photoPaper)
    fun decode(j:JSONObject)=PrintSettings(color=j.optBoolean("color",true),sides=Sides.valueOf(j.optString("sides","ONE")),
        paper=PaperSize.valueOf(j.optString("paper","A4")),landscape=j.optBoolean("landscape"),perSheet=j.optInt("perSheet",1),
        marginMm=j.optInt("marginMm",10),scale=j.optInt("scale",0),quality=RenderQuality.valueOf(j.optString("quality","NORMAL")),
        collate=j.optBoolean("collate",true),footer=j.optBoolean("footer"),reverseBacks=j.optBoolean("reverseBacks",true),photoPaper=j.optBoolean("photoPaper"))
        .also{it.validate()}
}
data class PrintPreset(val id:String,val name:String,val settings:PrintSettings,val builtIn:Boolean=false) {
    val summary get()="${if(settings.color)"Colour"else"Black & white"} · ${settings.paper.name} · ${if(settings.duplex)settings.sides.label else "One side"}"
}
object PrintPresets {
    val defaults=listOf(
        PrintPreset("document","Everyday document",PrintSettings(),true),
        PrintPreset("notes","Study notes",PrintSettings(color=false,sides=Sides.LONG,perSheet=2),true),
        PrintPreset("economy","Quick draft",PrintSettings(color=false,quality=RenderQuality.DRAFT),true),
        PrintPreset("photo","4 × 6 photo",PrintSettings(paper=PaperSize.PHOTO,photoPaper=true,quality=RenderQuality.HIGH,marginMm=5),true)
    )
    fun create(name:String,s:PrintSettings):PrintPreset {
        val clean=name.trim();require(clean.length in 1..40){"Use a preset name between 1 and 40 characters."}
        s.validate();return PrintPreset(UUID.randomUUID().toString(),clean,SettingsCodec.reusable(s))
    }
    fun encode(presets:List<PrintPreset>)=JSONArray().also{a->presets.filterNot{it.builtIn}.take(20).forEach{a.put(JSONObject().put("id",it.id).put("name",it.name).put("settings",SettingsCodec.encode(it.settings)))}}.toString()
    fun decode(text:String):List<PrintPreset> = runCatching {
        val a=JSONArray(text);(0 until minOf(a.length(),20)).mapNotNull{i->runCatching{
            val j=a.getJSONObject(i);val name=j.getString("name").trim();require(name.length in 1..40)
            val id=j.getString("id");require(id.isNotBlank()&&id !in defaults.map{it.id})
            PrintPreset(id,name,SettingsCodec.reusable(SettingsCodec.decode(j.getJSONObject("settings"))))
        }.getOrNull()}.distinctBy{it.id}
    }.getOrDefault(emptyList())
    fun apply(preset:PrintPreset,current:PrintSettings)=preset.settings.copy(range=current.range,filter=current.filter,copies=current.copies,
        reverseBacks=current.reverseBacks,reverse=if(preset.settings.duplex)false else current.reverse)
}
