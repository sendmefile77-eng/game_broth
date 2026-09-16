package com.sendmefile77.gamebroth.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class PngPayloadTest {
    @Test
    fun acceptsCompletePngAndRejectsTruncatedOrRawData() {
        val png = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
        )
        assertTrue(PngPayload.isPng(png))
        assertFalse(PngPayload.isPng(png.copyOf(png.size - 8)))
        assertFalse(PngPayload.isPng(ByteArray(1024) { 0x7f }))
    }
}
