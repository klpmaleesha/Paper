// SPDX-License-Identifier: GPL-3.0-or-later
package app.paper.print

import io.github.noble6.canonivec.core.Ivec
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class G2010ProfileTest {
    private val profile get()=File(requireNotNull(System.getProperty("paper.profile"))).readText()
    private val reportedIdentity="MFG:Canon;MDL:G2010 series;CMD:BJRaster3,IVEC;"
    @Test fun reportedG2010WithoutChmpUsesV1InsteadOfBeingRejected(){
        val session=G2010Profile.resolve(reportedIdentity,profile)
        assertEquals(1,session.protocol);assertEquals("00000001",session.jobId);assertFalse(session.model.datetime)
        assertEquals(4800,session.model.size("A4")!!.printW)
    }
    @Test fun chmpDeviceStillUsesV2(){
        val session=G2010Profile.resolve(reportedIdentity+"SOJ:CHMP;",profile)
        assertEquals(2,session.protocol);assertEquals("00000002",session.jobId);assertTrue(session.model.datetime)
    }
    @Test fun legacyIvecPliKeepsV1EvenWithChmp(){
        val session=G2010Profile.resolve("MDL:G2010 series;CMD:IVEC,IVECPLI;SOJ:CHMP;",profile)
        assertEquals(1,session.protocol)
    }
    @Test(expected=IllegalArgumentException::class) fun unrelatedModelIsStillRejected(){G2010Profile.resolve("MDL:G20100 series;CMD:IVEC;",profile)}
    @Test(expected=IllegalArgumentException::class) fun deviceWithoutIvecIsStillRejected(){G2010Profile.resolve("MDL:G2010 series;CMD:BJRaster3;",profile)}
    @Test fun normalizedIdentityDoesNotChangeProtocolSelection(){
        val session=G2010Profile.resolve(" mdl: g2010 series ; cmd: bjraster3, ivec ; soj: chmp ;",profile)
        assertEquals(2,session.protocol)
    }
    @Test fun v1JobHasClockPreludeAndMatchingCancellationId(){
        val session=G2010Profile.resolve(reportedIdentity,profile)
        val out=ByteArrayOutputStream()
        val job=Ivec.Job(out,session.protocol,session.model.datetime,"urn:uuid:test",clock={"20261116000000"})
        job.startJob();job.setConfiguration("iso_a4_210x297mm","stationery",false,true,false,null)
        job.sendPage(false,byteArrayOf(1,2,3));job.endJob()
        val wire=out.toString("ISO-8859-1")
        assertTrue(wire.contains("BJLSTART\nControlMode=Common\nSetTime=20261116000000\nBJLEND\n"))
        assertEquals(2,Regex("<ivec:operation>StartJob</ivec:operation>").findAll(wire).count())
        assertFalse(wire.contains("SetJobConfiguration"));assertFalse(wire.contains("00000002"))
        assertTrue(wire.contains("<ivec:jobID>00000001</ivec:jobID>"))
        assertTrue(Ivec.cancelJob("print",session.jobId).contains("<ivec:jobID>00000001</ivec:jobID>"))
    }
}
