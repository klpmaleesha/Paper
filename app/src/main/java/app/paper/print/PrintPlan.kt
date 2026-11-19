package app.paper.print

enum class Sides(val label: String) { ONE("One-sided"), LONG("Long edge"), SHORT("Short edge") }
enum class PaperSize(val label: String, val width: Int, val height: Int) {
    A4("A4 · 210 × 297 mm", 595, 842), A5("A5 · 148 × 210 mm", 420, 595),
    LETTER("Letter · 8.5 × 11 in", 612, 792), LEGAL("Legal · 8.5 × 14 in", 612, 1008),
    PHOTO("Photo · 4 × 6 in", 288, 432)
}
enum class PageFilter(val label: String) { ALL("All pages"), ODD("Odd pages"), EVEN("Even pages") }
enum class RenderQuality(val label: String, val dpi: Int) { DRAFT("Draft · 150 dpi",150), NORMAL("Standard · 200 dpi",200), HIGH("High · 300 dpi",300) }
data class PrintSettings(
    val color: Boolean = true, val sides: Sides = Sides.ONE, val copies: Int = 1,
    val range: String = "", val filter: PageFilter = PageFilter.ALL,
    val paper: PaperSize = PaperSize.A4, val landscape: Boolean = false,
    val perSheet: Int = 1, val marginMm: Int = 10, val scale: Int = 0,
    val quality: RenderQuality = RenderQuality.NORMAL, val collate: Boolean = true,
    val reverse: Boolean = false, val footer: Boolean = false,
    val photoPaper: Boolean = false, val reverseBacks: Boolean = true
) {
    val width get() = if (landscape) paper.height else paper.width
    val height get() = if (landscape) paper.width else paper.height
    val duplex get() = sides != Sides.ONE
    val rotateBack get() = (sides == Sides.SHORT && !landscape) || (sides == Sides.LONG && landscape)
    fun validate() {
        require(copies in 1..99) { "Choose between 1 and 99 copies." }
        require(perSheet in listOf(1,2,4)) { "Choose 1, 2 or 4 pages per side." }
        require(marginMm in 5..30) { "Margins must be between 5 and 30 mm." }
        require(scale == 0 || scale in 25..200) { "Scale must be between 25% and 200%." }
        require(!duplex || !photoPaper) { "Two-sided printing needs plain paper on the G2010." }
        require(!duplex || !reverse) { "Reverse order is available for one-sided jobs. Two-sided jobs use paired sheet order." }
    }
}
data class SheetSide(val pages: List<Int>, val rotate: Boolean = false)
data class PrintPlan(val front: List<SheetSide>, val back: List<SheetSide>, val sheets: Int, val selected: Int) {
    val all get() = front
    companion object {
        fun pages(text: String, count: Int): List<Int> {
            require(count in 1..2000) { "Documents must contain between 1 and 2,000 pages." }
            if (text.isBlank()) return (0 until count).toList()
            val pages = sortedSetOf<Int>()
            text.split(',').forEach { token ->
                val m = Regex("^\\s*(\\d+)(?:\\s*-\\s*(\\d+))?\\s*$").matchEntire(token)
                    ?: throw IllegalArgumentException("Use page numbers or ranges, such as 1–3, 5. Use a hyphen for ranges.")
                val start = m.groupValues[1].toIntOrNull() ?: 0
                val end = if(m.groupValues[2].isEmpty())start else m.groupValues[2].toIntOrNull()
                    ?: throw IllegalArgumentException("That page number is too large.")
                require(start in 1..count && end in start..count) { "Page ranges must be between 1 and $count, in ascending order." }
                (start..end).forEach { pages.add(it-1) }
            }
            return pages.toList()
        }
        fun build(settings: PrintSettings, count: Int): PrintPlan {
            settings.validate()
            var selected = pages(settings.range, count).filter {
                when(settings.filter) { PageFilter.ALL -> true; PageFilter.ODD -> (it+1)%2==1; PageFilter.EVEN -> (it+1)%2==0 }
            }
            require(selected.isNotEmpty()) { "No pages match this selection." }
            if(settings.reverse) selected = selected.reversed()
            val sides = selected.chunked(settings.perSheet).map { SheetSide(it) }
            fun repeatCopies(list: List<SheetSide>): List<SheetSide> = if(settings.collate)
                (1..settings.copies).flatMap { list } else list.flatMap { side -> (1..settings.copies).map { side } }
            if(!settings.duplex) return PrintPlan(repeatCopies(sides), emptyList(), sides.size*settings.copies, selected.size)
            val paired = sides.chunked(2).map { it[0] to (it.getOrNull(1) ?: SheetSide(emptyList())) }
            // Repeat complete sheet pairs: a blank back can never consume the next copy's first page.
            val copies = if(settings.collate) (1..settings.copies).flatMap { paired }
                else paired.flatMap { pair -> (1..settings.copies).map { pair } }
            val fronts = copies.map { it.first }
            val backs = copies.map { it.second.copy(rotate = settings.rotateBack) }.let { if(settings.reverseBacks) it.reversed() else it }
            return PrintPlan(fronts, backs, copies.size, selected.size)
        }
    }
}
