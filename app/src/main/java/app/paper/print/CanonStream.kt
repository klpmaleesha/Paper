// SPDX-License-Identifier: GPL-3.0-or-later
package app.paper.print

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/** Check command family and complete job boundaries before opening a print stream. */
object CanonStream {
    fun validate(file:File) {
        if(file.length()<700)throw IOException("The Canon encoder produced an incomplete job. Nothing was sent.")
        RandomAccessFile(file,"r").use{input->
            fun readAt(offset:Long,count:Int):String {input.seek(offset);val b=ByteArray(count);input.readFully(b);return b.toString(Charsets.ISO_8859_1)}
            val count=minOf(8192L,input.length()).toInt()
            val head=readAt(0,count)
            val tail=readAt(maxOf(0,input.length()-4096),minOf(4096L,input.length()).toInt())
            val valid=head.startsWith("<?xml")&&head.contains(">StartJob<")&&head.contains(">ModeShift<")&&
                head.contains("\u001b[K")&&!head.contains("PWGRaster")&&!head.contains("RaS2")&&tail.contains(">EndJob<")
            if(!valid)throw IOException("The encoder did not produce a complete Canon binary-raster job. Nothing was sent.")
        }
    }
}
