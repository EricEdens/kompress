package org.kompress;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Decompresses using the
 * <a href="https://www.ietf.org/rfc/rfc1951.txt">DEFLATE</a> compression scheme.
 */
public class DeflateInputStream extends InputStream {

  private final DeflateState state = new DeflateState();
  private final CircularByteBuffer history = new CircularByteBuffer(1 << 15);
  private final InputStream compressed;

  public DeflateInputStream(InputStream compressed) {
    this.compressed = compressed;
  }

  @Override
  public int read() throws IOException {
    if (history.maxRead() > 0) {
      return history.read();
    }

    return slowRead();
  }

  private int slowRead() throws IOException {
    if (state.finished) {
      return -1;
    }

    refill();

    if (history.maxRead() == 0) {
      state.finished = true;
      return -1;
    } else {
      return history.read();
    }
  }

  private void refill() throws IOException {
    assert !state.finished;

    if (!state.inBlock) {
      state.lastBlock = bits(1) == 1;
      switch (bits(2)) {
        case 0:
          state.blockType = BlockType.NONE;
          initUncompressed();
          break;
        case 1:
          state.blockType = BlockType.FIXED;
          initFixed();
          break;
        case 2:
          state.blockType = BlockType.DYNAMIC;
          initDynamic();
          break;
        default:
          throw new IllegalStateException();
      }
      state.inBlock = true;
    }

    switch (state.blockType) {
      case NONE:
        while (history.maxWrite() > 0) {
          int read = compressed.read();
          if (read == -1) {
            state.inBlock = false;
            state.finished = true;
            return;
          }
          history.write((byte) (read & 0xff));
        }
        break;
      case FIXED:
        throw new UnsupportedOperationException();
      case DYNAMIC:
        throw new UnsupportedOperationException();
    }
  }

  private void initDynamic() {
    throw new UnsupportedOperationException();
  }

  private void initFixed() {
    throw new UnsupportedOperationException();
  }

  private void initUncompressed() throws IOException {

    // Move to byte boundary
    if (state.nbits < 8 && state.nbits > 0) {
      state.nbits = 0;
      state.bits = 0;
    } else if (state.nbits > 8) {
      int rem = state.nbits % 8;
      state.nbits -= rem;
      state.bits = state.bits >>> rem;
    }

    int b1 = bits(8);
    int b2 = bits(8);
    int b3 = bits(8);
    int b4 = bits(8);

    if ((b1 | b3) != 0xff || (b2 | b4) != 0xff) {
      throw new IllegalStateException();
    }

    int len = b1 | (b2 << 8);
    assert len >= 0 && len <= 65535;

    state.uncompressedLen = len;
  }

  private int bits(int n) throws IOException {
    assert n > 0 && n < 17;
    while (state.nbits < n) {
      int read = compressed.read();
      if (read == -1) throw new EOFException();
      state.bits = state.bits | (read << state.nbits);
      state.nbits += 8;
    }

    int ret = keepLastNBits(state.bits, n);
    state.bits = state.bits >> n;
    state.nbits -= n;
    return ret;
  }

  private int keepLastNBits(int value, int n) {
    return value & ((1 << n) - 1);
  }

  private enum BlockType {
    NONE, FIXED, DYNAMIC
  }

  private static class DeflateState {
    int uncompressedLen;
    int nbits;
    int bits;
    boolean finished = false;
    boolean lastBlock = false;
    boolean inBlock = false;
    BlockType blockType = null;
  }

  private static class CircularByteBuffer {
    private final int capacity;
    private final int mask;
    private final byte[] bytes;
    private int maxRead = 0;
    private int nextRead = 0;
    private int nextWrite = 0;

    private CircularByteBuffer(int capacity) {
      this.capacity = roundUpToPower2(capacity);
      this.mask = this.capacity - 1;
      this.bytes = new byte[this.capacity];
    }

    private int roundUpToPower2(int capacity) {
      for (int pow = 1; pow <= 20; pow++) {
        if (capacity <= (1 << pow)) {
          return 1 << pow;
        }
      }
      throw new AssertionError(String.format("Max capacity is %d. Given %d.", 1 << 20, capacity));
    }

    public int maxRead() {
      return maxRead;
    }

    public int read() {
      assert maxRead > 0;
      maxRead--;
      int readIndex = nextRead;
      nextRead = (nextRead + 1) & mask;
      return bytes[readIndex];
    }

    public int maxWrite() {
      return capacity - maxRead;
    }

    public void write(byte b) {
      assert maxWrite() > 0;
      maxRead++;
      int writeIndex = nextWrite;
      nextWrite = (nextWrite + 1) & mask;
      bytes[writeIndex] = b;
    }
  }
}
