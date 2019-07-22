package org.kompress

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.io.File
import kotlin.random.Random

class ZopfliRandomComparisonTest {

  private val zopfliAvail: Boolean = {
    var avail = true
    try {
      Runtime.getRuntime().exec("zopfli");
    } catch (e: Exception) {
      avail = false
    }
    avail
  }()

  @Test fun `64k onebyte`() {
    compareToZopfli(64 * 1024, "a".toByteArray())
  }

  @Test fun `128k alpha`() {
    compareToZopfli(128 * 1024, "aab".toByteArray())
  }

  @Test fun `256k alphanum`() {
    compareToZopfli(256 * 1024, "aab12333".toByteArray())
  }

  private fun compareToZopfli(length: Int, alphabet: ByteArray) {
    if (!zopfliAvail) {
      println("To run this suite, install zopfli and put on system path.")
      return
    }

    val uncompressed = File.createTempFile("tempfile", ".tmp")
    uncompressed.outputStream().use {
      for (i in 0 until length) {
        it.write(alphabet[Random.nextInt(alphabet.size)].toInt() and 0xff)
      }
    }
    val zopfli = ProcessBuilder("zopfli", "--deflate", "-c", uncompressed.absolutePath).start()
    assertArrayEquals(uncompressed.readBytes(), Deflate.decompress(zopfli.inputStream.readBytes()))
  }

}
