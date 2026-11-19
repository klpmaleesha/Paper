package app.paper.print

/** A silent endpoint must not leave the UI waiting indefinitely. No automatic data retry. */
class TransferWatch(private val startedAt:Long,private val idleLimitMs:Long=90000){
    private var lastActivity=startedAt
    fun timedOut(now:Long,printerHasFreshActivity:Boolean):Boolean {
        if(printerHasFreshActivity)lastActivity=now
        return now-lastActivity>=idleLimitMs
    }
}
