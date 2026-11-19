// SPDX-License-Identifier: GPL-3.0-or-later
package app.paper.print

import org.json.JSONObject

enum class ReloadFace(val label:String) { UP("Blank side facing up"), DOWN("Blank side facing down") }
enum class ReloadEdge(val label:String) { FIRST("Marked edge enters first"), LAST("Marked edge enters last") }
data class DuplexSetup(val printerId:String,val face:ReloadFace,val edge:ReloadEdge,val reverseBacks:Boolean,val confirmed:Boolean=true) {
    val instructions get()="${face.label}. ${edge.label}. ${if(reverseBacks)"Keep back-side order reversed."else"Use forward back-side order."}"
    fun encode()=JSONObject().put("printerId",printerId).put("face",face.name).put("edge",edge.name).put("reverseBacks",reverseBacks).put("confirmed",confirmed).toString()
    companion object {
        fun decode(text:String):DuplexSetup?=runCatching{val j=JSONObject(text);require(j.getBoolean("confirmed"));DuplexSetup(j.getString("printerId"),ReloadFace.valueOf(j.getString("face")),ReloadEdge.valueOf(j.getString("edge")),j.getBoolean("reverseBacks"))}.getOrNull()
    }
}
