// SPDX-License-Identifier: GPL-3.0-or-later
package app.paper.print

import io.github.noble6.canonivec.core.Model
import io.github.noble6.canonivec.core.ModelDatabase
import org.json.JSONObject
import java.util.Locale

/** Keep experimental page/media geometry separate from the device's actual job framing. */
data class G2010Session(val model:Model,val soj:String) {
    val protocol get()=model.protocol
    val jobId get()=if(protocol==1)"00000001"else"00000002"
}

object G2010Profile {
    fun validateIdentity(deviceId:String) {
        val mdl=ModelDatabase.field(deviceId,"MDL").orEmpty()
        require(Regex("G2010(?:\\s+series)?",RegexOption.IGNORE_CASE).matches(mdl)){
            "This test driver requires a printer identifying itself as G2010. Reported model: ${mdl.ifBlank{"unknown"}}."
        }
        val commands=ModelDatabase.field(deviceId,"CMD").orEmpty().split(',').map{it.trim().uppercase(Locale.ROOT)}
        require("IVEC" in commands){"This printer did not report IVEC support. Nothing was printed."}
    }
    fun resolve(deviceId:String,profileJson:String):G2010Session {
        validateIdentity(deviceId)
        val commands=ModelDatabase.field(deviceId,"CMD").orEmpty().split(',').map{it.trim().uppercase(Locale.ROOT)}
        val soj=ModelDatabase.field(deviceId,"SOJ").orEmpty().uppercase(Locale.ROOT)
        // Use the upstream protocol rule. Absence of SOJ:CHMP selects V1, rather than rejecting IVEC.
        val protocol=if(ModelDatabase.isV1Device("CMD:${commands.joinToString(",")};SOJ:$soj;"))1 else 2
        val models=JSONObject(profileJson).getJSONArray("models")
        val base=(0 until models.length()).map{models.getJSONObject(it)}.firstOrNull{it.getString("id")=="canong3010"}
            ?:throw IllegalArgumentException("The experimental printer profile is missing.")
        base.put("protocol",protocol)
        if(protocol==1)base.put("datetime",false)
        return G2010Session(Model.fromJson(base),soj)
    }
}
