package io.github.diegog0477.zombiebox.client

import io.github.diegog0477.zombiebox.client.features.diagnostics.platform.HardwareScanner
import org.junit.Assert.*
import org.junit.Test

class HardwareScannerTest {

    @Test
    fun parseCpuInfoAbiDetectsArmv7FromVizioCoStarCpuInfo() {
        val cpuInfo =
            """
            Processor	: ARMv7 Processor rev 1 (v7l)
            BogoMIPS	: 1196.85
            Features	: swp half thumb fastmult vfp edsp thumbee neon vfpv3 tls
            CPU implementer	: 0x41
            CPU architecture: 7
            CPU variant	: 0x2
            CPU part	: 0xc09
            CPU revision	: 1

            Hardware	: MV88DE3100
            Revision	: 0000
            Serial		: 0000000000000000
            """
                .trimIndent()

        val abi = HardwareScanner.parseCpuInfoAbi(cpuInfo)
        assertEquals("armeabi-v7a", abi)
    }

    @Test
    fun parseCpuInfoAbiDetectsArm64() {
        val cpuInfo =
            "Processor: AArch64 Processor rev 4 (aarch64)\nFeatures: fp asimd\nCPU architecture: 8"
        assertEquals("arm64-v8a", HardwareScanner.parseCpuInfoAbi(cpuInfo))
    }

    @Test
    fun parseCpuInfoAbiDetectsX86AndX86_64() {
        val x86 = "model name : Intel(R) Atom(TM) CPU D2550   @ 1.86GHz"
        assertEquals("x86", HardwareScanner.parseCpuInfoAbi(x86))

        val x86_64 =
            "flags : fpu vme de pse tsc msr pae mce cx8 apic sep mtrr pge mca cmov pat pse36 clflush mmx fxsr sse sse2 ht syscall nx mmxext fxsr_opt lm ... x86_64"
        assertEquals("x86_64", HardwareScanner.parseCpuInfoAbi(x86_64))
    }

    @Test
    fun parseCpuInfoAbiReturnsNullOnUnrecognizedText() {
        assertNull(HardwareScanner.parseCpuInfoAbi("random chip without architecture details"))
    }
}
