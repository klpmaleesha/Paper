package app.paper.print

import org.junit.Assert.*
import org.junit.Test

class PrintPlanTest {
    @Test fun rangesAreValidatedAndDeduplicated() { assertEquals(listOf(0,1,2,4),PrintPlan.pages("1-3, 2, 5",5)) }
    @Test(expected=IllegalArgumentException::class) fun reversedRangeIsRejected() { PrintPlan.pages("4-2",5) }
    @Test(expected=IllegalArgumentException::class) fun oversizedRangeIsRejected() { PrintPlan.pages("1-999999999",5) }
    @Test(expected=IllegalArgumentException::class) fun overflowedEndCannotSilentlyBecomeSinglePage() { PrintPlan.pages("1-99999999999999999999",5) }
    @Test(expected=IllegalArgumentException::class) fun emptySelectionIsRejected() { PrintPlan.build(PrintSettings(filter=PageFilter.EVEN),1) }
    @Test fun oddPageDuplexKeepsBlankBetweenCopies() {
        val p=PrintPlan.build(PrintSettings(sides=Sides.LONG,copies=2),3)
        assertEquals(listOf(listOf(0),listOf(2),listOf(0),listOf(2)),p.front.map{it.pages})
        assertEquals(listOf(emptyList(),listOf(1),emptyList(),listOf(1)),p.back.map{it.pages})
        assertEquals(4,p.sheets)
    }
    @Test fun nUpPairsPhysicalSidesNotDocumentPages() {
        val p=PrintPlan.build(PrintSettings(sides=Sides.SHORT,perSheet=2),5)
        assertEquals(listOf(listOf(0,1),listOf(4)),p.front.map{it.pages})
        assertEquals(listOf(emptyList(),listOf(2,3)),p.back.map{it.pages})
        assertTrue(p.back.all{it.rotate})
    }
    @Test fun longEdgeLandscapeRotatesBack() { assertTrue(PrintSettings(sides=Sides.LONG,landscape=true).rotateBack) }
    @Test fun longEdgePortraitDoesNotRotateBack() { assertFalse(PrintSettings(sides=Sides.LONG).rotateBack) }
    @Test fun shortEdgeLandscapeDoesNotRotateBack() { assertFalse(PrintSettings(sides=Sides.SHORT,landscape=true).rotateBack) }
    @Test fun uncollatedCopiesRepeatWholePairs() {
        val p=PrintPlan.build(PrintSettings(sides=Sides.LONG,copies=2,collate=false),4)
        assertEquals(listOf(listOf(0),listOf(0),listOf(2),listOf(2)),p.front.map{it.pages})
        assertEquals(listOf(listOf(3),listOf(3),listOf(1),listOf(1)),p.back.map{it.pages})
    }
    @Test fun reverseOneSidedAndFilter() {
        assertEquals(listOf(listOf(4),listOf(2),listOf(0)),PrintPlan.build(PrintSettings(filter=PageFilter.ODD,reverse=true),5).front.map{it.pages})
    }
    @Test(expected=IllegalArgumentException::class) fun duplexPhotoPaperRejected() { PrintPlan.build(PrintSettings(sides=Sides.LONG,photoPaper=true),2) }
    @Test(expected=IllegalArgumentException::class) fun duplexReverseRejected() { PrintPlan.build(PrintSettings(sides=Sides.LONG,reverse=true),2) }
    @Test fun backOrderCanBeCalibrated() {
        assertEquals(listOf(listOf(1),listOf(3)),PrintPlan.build(PrintSettings(sides=Sides.LONG,reverseBacks=false),4).back.map{it.pages})
    }
}
