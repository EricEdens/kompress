package org.kompress;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
            state.finished = state.lastBlock;
            return;
          }
          history.write((byte) (read & 0xff));
        }
        break;
      case FIXED:
      case DYNAMIC:
        // A single DEFLATE code can create
        // up to 258 bytes in the output.
        while (history.maxWrite() > 258) {
          int llCode = decode(state.lenLitDecoder);
          assert llCode <= 285;

          if (llCode < 256) {
            history.write((byte) (llCode & 0xff));
          } else if (llCode == 256) {
            // 256 is DEFLATE's sentinel value for
            // ending the current compressed block.
            state.inBlock = false;
            state.finished = state.lastBlock;
            return;
          } else {

            final int length;
            if (llCode < 265) {
              length = llCode - (257 - 3);
            } else if (llCode < 269) {
              length = llCode * 2 - (265 * 2 - 11) + bits(1);
            } else if (llCode < 273) {
              length = llCode * 4 - (269 * 4 - 19) + bits(2);
            } else if (llCode < 277) {
              length = llCode * 8 - (273 * 8 - 35) + bits(3);
            } else if (llCode < 281) {
              length = llCode * 16 - (277 * 16 - 67) + bits(4);
            } else if (llCode < 285) {
              length = llCode * 32 - (281 * 32 - 131) + bits(5);
            } else if (llCode == 285) {
              length = 258;
            } else {
              throw new AssertionError();
            }

            int dCode = decode(state.distDecoder);
            final int distance;

            if (dCode < 4) {
              distance = dCode + 1;
            } else {
              int nb = (dCode - 2) >> 1;
              int extra = (dCode & 1) << nb;
              extra |= bits(nb);
              distance = (1 << (nb + 1)) + 1 + extra;
            }

            history.lookback(length, distance);
          }
        }
    }
  }

  private int decode(Decoder decoder) throws IOException {
    int n = decoder.minCodeLen;

    while (true) {
      while (state.nbits < n) {
        readByte();
      }

      Code code = decoder.table[state.bits & decoder.tableMask];
      n = code.nbits;

      if (n <= state.nbits) {
        state.nbits -= code.nbits;
        state.bits = state.bits >>> code.nbits;
        return code.value;
      }
    }
  }

  private void initDynamic() throws IOException {
    int hlit = bits(5);
    int hdist = bits(5);
    int hclen = bits(4);

    List<Code> codeLenAlphabet = new ArrayList<>();

    int[] codingIndex = {16, 17, 18,
        0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15};

    for (int i = 0; i < hclen + 4; i++) {
      int codeLen = bits(3);
      if (codeLen > 0) {
        codeLenAlphabet.add(new Code(codingIndex[i], codeLen));
      }
    }

    Decoder headerDecoder = new Decoder(codeLenAlphabet.toArray(new Code[]{}));

    Code[] codeLengths = new Code[hlit + hdist + 258];

    for (int i = 0; i < codeLengths.length; i++) {
      int decoded = decode(headerDecoder);

      if (decoded < 16) {
        codeLengths[i] = new Code(i % (hlit  + 257), decoded);
        continue;
      }

      int repeatLen;
      int repeatVal;
      if (decoded == 16) {
        repeatLen = 3 + bits(2);
        repeatVal = codeLengths[i - 1].nbits;
      } else if (decoded == 17) {
        repeatLen = 3 + bits(3);
        repeatVal = 0;
      } else if (decoded == 18) {
        repeatLen = 11 + bits(7);
        repeatVal = 0;
      } else {
        throw new AssertionError();
      }

      for(int j = 0; j < repeatLen; j++) {
        codeLengths[i + j] = new Code((i + j) % (hlit  + 257), repeatVal);
      }
      i += repeatLen - 1;
    }

    state.lenLitDecoder = new Decoder(Arrays.copyOf(codeLengths, hlit + 257));
    state.distDecoder = new Decoder(Arrays.copyOfRange(codeLengths, hlit + 257, codeLengths.length));
  }

  private void initFixed() {

    Code[] distanceTable = new Code[30];
    for (int i = 0; i < distanceTable.length; i++) {
      distanceTable[i] = new Code(i, 5);
    }

    Code[] lenLitTable = new Code[286];
    for (int i = 0; i < lenLitTable.length; i++) {
      if (i < 144) {
        lenLitTable[i] = new Code(i, 8);
      } else if (i < 256) {
        lenLitTable[i] = new Code(i, 9);
      } else if (i < 280) {
        lenLitTable[i] = new Code(i, 7);
      } else {
        lenLitTable[i] = new Code(i, 8);
      }
    }

    state.distDecoder = new Decoder(distanceTable);
    state.lenLitDecoder = new Decoder(lenLitTable);
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
      readByte();
    }

    int ret = keepLastNBits(state.bits, n);
    state.bits = state.bits >> n;
    state.nbits -= n;
    return ret;
  }

  private void readByte() throws IOException {
    int read = compressed.read();
    if (read == -1) throw new EOFException();
    state.bits = state.bits | (read << state.nbits);
    state.nbits += 8;
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
    Decoder lenLitDecoder = null;
    Decoder distDecoder = null;
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

    public void lookback(int length, int distance) {
      int p = distance >= nextWrite
          ? capacity - (distance - nextWrite)
          : nextWrite - distance;
      for (int i = 0; i < length; i++) {
        write(bytes[p & mask]);
        p++;
      }
    }
  }

  private static class Decoder {
    final Code[] table;
    final int minCodeLen;
    final int maxCodeLen;
    final int tableMask;

    Decoder(Code[] codeLens) {
      Arrays.sort(codeLens);
      int codeIndex = 0;
      while(codeIndex < codeLens.length && codeLens[codeIndex].nbits == 0) {
        codeIndex++;
      }
      minCodeLen = codeLens[codeIndex].nbits;
      maxCodeLen = codeLens[codeLens.length - 1].nbits;
      table = new Code[1 << maxCodeLen];
      tableMask = (1 << maxCodeLen) - 1;
      int currBitCode = 0;
      for (int bitLen = 0; bitLen < 16; bitLen++) {
        while (codeIndex < codeLens.length && codeLens[codeIndex].nbits == bitLen) {
          Code code = codeLens[codeIndex];
          int reversed = Integer.reverse(currBitCode) >>> (32 - code.nbits);
          table[reversed] = code;
          if (code.nbits < maxCodeLen) {
            int numPads = 1 << (maxCodeLen - code.nbits);
            for (int i = 1; i < numPads; i++) {
              table[reversed | i << code.nbits] = code;
            }
          }
          currBitCode++;
          codeIndex++;
        }
        currBitCode <<= 1;
      }
    }
  }

  private static class Code implements Comparable<Code> {

    final int value;
    final int nbits;

    private Code(int value, int nbits) {
      this.value = value;
      this.nbits = nbits;
    }

    @Override
    public int compareTo(Code o) {
      if (nbits < o.nbits) {
        return -1;
      } else if (nbits > o.nbits) {
        return 1;
      } else {
        assert value != o.value;
        return Integer.compare(value, o.value);
      }
    }
  }
}
