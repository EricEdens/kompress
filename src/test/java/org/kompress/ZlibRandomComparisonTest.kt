package org.kompress

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.util.zip.Deflater
import java.util.zip.DeflaterInputStream
import kotlin.random.Random

class ZlibRandomComparisonTest {

  @Test fun `64k alpha - uncompressed`() {
    compareToZlib(64 * 1024, "aab".toByteArray(), 0)
  }

  @Test fun `64k alpha - 1 compression`() {
    compareToZlib(64 * 1024, "aab".toByteArray(), 1)
  }

  @Test fun `64k alpha - 5 compression`() {
    compareToZlib(64 * 1024, "aab".toByteArray(), 5)
  }

  @Test fun `64k alpha - 9 compression`() {
    compareToZlib(64 * 1024, "aab".toByteArray(), 9)
  }

  @Test fun `256k alphanum - uncompressed`() {
    compareToZlib(256 * 1024, "aab12333".toByteArray(), 0)
  }

  @Test fun `256k alphanum - 1 compression`() {
    compareToZlib(256 * 1024, "aab12333".toByteArray(), 1)
  }

  @Test fun `256k alphanum - 5 compression`() {
    compareToZlib(256 * 1024, "aab12333".toByteArray(), 5)
  }

  @Test fun `256k alphanum - 9 compression`() {
    compareToZlib(256 * 1024, "aab12333".toByteArray(), 9)
  }

  private fun compareToZlib(length: Int, alphabet: ByteArray, compressionLevel: Int) {
    val uncompressed = ByteArray(length)

    for (i in 0 until length) {
      uncompressed[i] = alphabet[Random.nextInt(alphabet.size)]
    }

    val compressed = DeflaterInputStream(
      uncompressed.inputStream(), Deflater(compressionLevel, true)
    ).readBytes();

    check(uncompressed, compressed)
  }

  private fun check(uncompressed: ByteArray, compressed: ByteArray) {
    assertArrayEquals(uncompressed, Deflate.decompress(compressed))
  }
}
