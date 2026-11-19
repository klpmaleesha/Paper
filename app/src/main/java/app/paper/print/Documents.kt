package app.paper.print

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.min

data class LocalDocument(val file: File, val name: String, val pages: Int, val bytes: Long)

internal inline fun <T:PdfDocument,R> T.usePdf(block:(T)->R):R {
    try{return block(this)}finally{close()}
}

object Documents {
    private const val MAX_BYTES = 100L * 1024 * 1024
    fun import(context: Context, uri: Uri): LocalDocument {
        val resolver = context.contentResolver
        var name = "Document"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { cursor ->
            if(cursor.moveToFirst()) name = cursor.getString(0) ?: name
        }
        val type = resolver.getType(uri) ?: ""
        val incoming = File(context.cacheDir, "import-${UUID.randomUUID()}")
        try {
            resolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "This document could not be opened. Please choose it again." }
                FileOutputStream(incoming).use { out ->
                    val buffer = ByteArray(32768); var total = 0L
                    while(true) { val n=input.read(buffer); if(n<0)break; total+=n; require(total<=MAX_BYTES){"Choose a document smaller than 100 MB."}; out.write(buffer,0,n) }
                }
            }
            val file = when {
                type == "application/pdf" || name.endsWith(".pdf",true) -> incoming
                type.startsWith("image/") -> imageToPdf(context,incoming)
                type == "text/plain" || name.endsWith(".txt",true) -> textToPdf(context,incoming)
                else -> throw IllegalArgumentException("Choose a PDF, an image, or a plain text file. Export Word or Excel documents to PDF first.")
            }
            if(file!=incoming)incoming.delete()
            val count = ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { it.pageCount } }
            require(count in 1..2000) { "Choose a document with 1–2,000 pages." }
            return LocalDocument(file,name,count,file.length())
        } catch (e: Exception) { incoming.delete(); throw e }
    }
    private fun imageToPdf(context: Context, file: File): File {
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeFile(file.path,bounds)
        require(bounds.outWidth>0 && bounds.outHeight>0){"This image format could not be read. Try a JPEG or PNG."}
        var sample=1; while(bounds.outWidth/sample>3508 || bounds.outHeight/sample>3508)sample*=2
        val bitmap=BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply{inSampleSize=sample})
            ?: throw IllegalArgumentException("This image could not be opened.")
        val output=File(context.cacheDir,"image-${UUID.randomUUID()}.pdf")
        try { PdfDocument().usePdf { pdf ->
            val page=pdf.startPage(PdfDocument.PageInfo.Builder(595,842,1).create())
            page.canvas.drawColor(Color.WHITE)
            val factor=min(539f/bitmap.width,786f/bitmap.height)
            val w=bitmap.width*factor; val h=bitmap.height*factor
            page.canvas.drawBitmap(bitmap,null,RectF((595-w)/2,(842-h)/2,(595+w)/2,(842+h)/2),Paint(Paint.FILTER_BITMAP_FLAG))
            pdf.finishPage(page); output.outputStream().use{pdf.writeTo(it)}
        }} finally { bitmap.recycle() }
        return output
    }
    private fun textToPdf(context: Context,file: File): File {
        require(file.length()<=2*1024*1024){"Text files must be smaller than 2 MB."}
        val text=file.readText(Charsets.UTF_8)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=11f;typeface=Typeface.create("sans-serif",Typeface.NORMAL)}
        val lines=mutableListOf<String>()
        text.lines().forEach { raw ->
            var line=raw.replace("\t","    ")
            if(line.isEmpty())lines.add("")
            while(line.isNotEmpty()) { var n=paint.breakText(line,true,515f,null); if(n<1)n=1; lines.add(line.take(n));line=line.drop(n) }
        }
        val output=File(context.cacheDir,"text-${UUID.randomUUID()}.pdf")
        PdfDocument().usePdf{pdf ->
            lines.ifEmpty{listOf("")}.chunked(47).forEachIndexed { index, pageLines ->
                val page=pdf.startPage(PdfDocument.PageInfo.Builder(595,842,index+1).create());page.canvas.drawColor(Color.WHITE)
                pageLines.forEachIndexed{i,s->page.canvas.drawText(s,40f,52f+i*16f,paint)};pdf.finishPage(page)
            };output.outputStream().use{pdf.writeTo(it)}
        }
        return output
    }
    fun sample(context: Context): LocalDocument {
        val file=File(context.cacheDir,"paper-welcome.pdf")
        PdfDocument().usePdf{pdf->
            for(index in 1..4) {
                val p=pdf.startPage(PdfDocument.PageInfo.Builder(595,842,index).create()); val c=p.canvas
                c.drawColor(Color.WHITE)
                val ink=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.rgb(31,41,58);textSize=12f}
                ink.color=Color.rgb(36,87,214);c.drawRoundRect(RectF(44f,48f,82f,86f),12f,12f,ink)
                ink.color=Color.WHITE;ink.textSize=25f;c.drawText("p",56f,76f,ink)
                ink.color=Color.rgb(31,41,58);ink.textSize=11f;c.drawText("PAPER / PRINT TEST",96f,72f,ink)
                ink.textSize=34f;ink.typeface=Typeface.create("sans-serif",Typeface.BOLD)
                c.drawText(if(index==1)"A little more clarity." else "Every detail, considered.",44f,155f,ink)
                ink.textSize=13f;ink.typeface=Typeface.DEFAULT
                c.drawText("A simple document. A comfortable printing experience.",44f,188f,ink)
                ink.color=Color.rgb(235,240,251);c.drawRoundRect(RectF(44f,227f,551f,396f),16f,16f,ink)
                ink.color=Color.rgb(36,87,214);ink.textSize=18f;c.drawText("Make it yours",64f,263f,ink)
                ink.color=Color.rgb(58,67,83);ink.textSize=13f
                listOf("Colour or black & white. Your choice.","One side, or both. With a little guidance.","The pages you need. Nothing extra.").forEachIndexed{i,s->c.drawText(s,64f,300f+i*28f,ink)}
                ink.textSize=18f;c.drawText("A quick check before you print",44f,445f,ink)
                ink.textSize=12f
                listOf("1. Load plain A4 paper into your printer.","2. Select colour, layout, and the pages you need.","3. For both sides, start with a two-page test.","4. Let the ink dry before reloading the paper.").forEachIndexed{i,s->c.drawText(s,44f,480f+i*25f,ink)}
                val colors=intArrayOf(Color.rgb(36,87,214),Color.CYAN,Color.MAGENTA,Color.YELLOW,Color.BLACK)
                colors.forEachIndexed{i,color->ink.color=color;c.drawCircle(58f+i*31f,665f,9f,ink)}
                ink.color=Color.GRAY;ink.textSize=11f;c.drawText("Paper · Sample document",44f,790f,ink);c.drawText("$index / 4",520f,790f,ink)
                pdf.finishPage(p)
            };file.outputStream().use{pdf.writeTo(it)}
        }
        return LocalDocument(file,"A little more clarity.pdf",4,file.length())
    }
}

object PdfOutput {
    fun bitmap(renderer: PdfRenderer,index: Int,dpi: Int): Bitmap = renderer.openPage(index).use { page ->
        val w=(page.width*dpi/72f).toInt().coerceIn(1,5000)
        val h=(page.height.toLong()*w/page.width).toInt().coerceAtLeast(1)
        require(w.toLong()*h<=22_000_000){"This page is too large to render. Try standard quality."}
        val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888); bitmap.eraseColor(Color.WHITE)
        try{page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_PRINT);bitmap}catch(e:Exception){bitmap.recycle();throw e}
    }
    fun draw(canvas: Canvas,renderer: PdfRenderer,side: SheetSide,s: PrintSettings,dpi: Int,name: String) {
        canvas.drawColor(Color.WHITE)
        val save=canvas.save()
        if(side.rotate)canvas.rotate(180f,s.width/2f,s.height/2f)
        val margin=s.marginMm*72f/25.4f
        val footer=if(s.footer)18f else 0f
        val columns=if(s.perSheet==4 || s.perSheet==2 && s.landscape)2 else 1
        val rows=(s.perSheet+columns-1)/columns
        val gap=if(s.perSheet>1)10f else 0f
        val cw=(s.width-2*margin-gap*(columns-1))/columns
        val ch=(s.height-2*margin-footer-gap*(rows-1))/rows
        val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            if(!s.color)colorFilter=ColorMatrixColorFilter(ColorMatrix().apply{setSaturation(0f)})
        }
        side.pages.forEachIndexed { slot, page ->
            val b=bitmap(renderer,page,dpi)
            try {
                val cellX=margin+(slot%columns)*(cw+gap);val cellY=margin+(slot/columns)*(ch+gap)
                val factor=if(s.scale==0)min(cw/b.width,ch/b.height) else 72f/dpi*s.scale/100f
                val w=b.width*factor;val h=b.height*factor
                val clip=canvas.save();canvas.clipRect(cellX,cellY,cellX+cw,cellY+ch)
                canvas.drawBitmap(b,null,RectF(cellX+(cw-w)/2,cellY+(ch-h)/2,cellX+(cw+w)/2,cellY+(ch+h)/2),paint)
                canvas.restoreToCount(clip)
            } finally { b.recycle() }
        }
        if(s.footer){val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.DKGRAY;textSize=8f};canvas.drawText(name.take(65),margin,s.height-margin,p);p.textAlign=Paint.Align.RIGHT;canvas.drawText(side.pages.joinToString(", "){(it+1).toString()},s.width-margin,s.height-margin,p)}
        canvas.restoreToCount(save)
    }
    fun preview(document: LocalDocument,side: SheetSide,s: PrintSettings): Bitmap {
        val w=650;val h=(s.height.toFloat()/s.width*w).toInt()
        val image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(image);canvas.scale(w.toFloat()/s.width,h.toFloat()/s.height)
        ParcelFileDescriptor.open(document.file,ParcelFileDescriptor.MODE_READ_ONLY).use{fd->PdfRenderer(fd).use{draw(canvas,it,side,s,90,document.name)}}
        return image
    }
    fun write(document: LocalDocument,sides: List<SheetSide>,s: PrintSettings,destination: File,signal: CancellationSignal,progress:(Int)->Unit) {
        require(sides.isNotEmpty()){"There are no pages to prepare."}
        try {
            ParcelFileDescriptor.open(document.file,ParcelFileDescriptor.MODE_READ_ONLY).use{fd->PdfRenderer(fd).use{renderer->
                PdfDocument().usePdf{pdf->
                    sides.forEachIndexed{i,side->
                        signal.throwIfCanceled(); val p=pdf.startPage(PdfDocument.PageInfo.Builder(s.width,s.height,i+1).create())
                        draw(p.canvas,renderer,side,s,s.quality.dpi,document.name);pdf.finishPage(p);progress((i+1)*100/sides.size)
                    }
                    signal.throwIfCanceled();destination.outputStream().use{pdf.writeTo(it)}
                }
            }}
        } catch(e:Exception) { destination.delete();throw e }
    }
}
