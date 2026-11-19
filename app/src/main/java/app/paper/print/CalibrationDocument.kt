// SPDX-License-Identifier: GPL-3.0-or-later
package app.paper.print

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import java.io.File

object CalibrationDocument {
    fun create(context:Context):LocalDocument {
        val file=File(context.cacheDir,"duplex-setup-${System.nanoTime()}.pdf")
        PdfDocument().usePdf{pdf->
            repeat(4){index->
                val page=pdf.startPage(PdfDocument.PageInfo.Builder(595,842,index+1).create());val c=page.canvas
                c.drawColor(Color.WHITE);val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=16f}
                p.strokeWidth=3f;c.drawLine(60f,64f,535f,64f,p);c.drawLine(297f,120f,297f,78f,p);c.drawLine(297f,78f,286f,91f,p);c.drawLine(297f,78f,308f,91f,p)
                p.textAlign=Paint.Align.CENTER;p.typeface=Typeface.create("sans-serif",Typeface.BOLD);p.textSize=18f;c.drawText("TOP / MARKED EDGE",297f,151f,p)
                p.textSize=48f;c.drawText("SHEET ${index/2+1}",297f,300f,p);p.textSize=36f;c.drawText(if(index%2==0)"FRONT"else"BACK",297f,360f,p)
                p.typeface=Typeface.create("sans-serif",Typeface.NORMAL);p.textSize=14f
                listOf("Paper manual-duplex setup","A4 / portrait / long-edge binding","Each sheet must have its matching front and back.","Both TOP arrows should point to the same physical edge.","Save your reload instructions only after checking both sheets.").forEachIndexed{i,line->c.drawText(line,297f,450f+i*32,p)}
                c.drawText("Page ${index+1} of 4",297f,760f,p);pdf.finishPage(page)
            }
            file.outputStream().use{pdf.writeTo(it)}
        }
        return LocalDocument(file,"Manual duplex setup.pdf",4,file.length())
    }
}
