// SPDX-License-Identifier: GPL-3.0-or-later
package app.paper.print.ipp
import java.io.IOException

data class IppJobStatus(val state:Int,val reasons:List<String>) {
    val completedWithErrors get()=state==9&&"job-completed-with-errors" in reasons
    companion object {
        fun from(response:IppResponse):IppJobStatus {
            val state=response.requireSuccess().int("job-state")
            if(state==null||state !in 3..9)throw IOException("The printer returned an invalid job state. Check its queue before printing again.")
            return IppJobStatus(state,response.texts("job-state-reasons"))
        }
    }
}
