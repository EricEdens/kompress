package org.kompress;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class DeflateDecompression {

  private static final byte[] compressed =
    readBytes(DeflateDecompression.class.getClassLoader().getResourceAsStream("zopflibig"));

  private static byte[] readBytes(InputStream inputStream) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    while (true) {
      int read;
      try {
        read = inputStream.read();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      if (read == -1) {
        break;
      }
      out.write(read);
    }
    return out.toByteArray();
  }

  @Benchmark
  public int kompress() throws IOException {
    return consume(new DeflateInputStream(new ByteArrayInputStream(compressed)));
  }

  @Benchmark
  public int zlib() throws IOException {
    return consume(new BufferedInputStream(new InflaterInputStream(new ByteArrayInputStream(compressed), new Inflater(true))));
  }

  private int consume(InputStream inputStream) throws IOException {
    int read;
    int total = 0;
    while ((read = inputStream.read()) != -1) {
      total += read;
    }

    return total;
  }

  private static byte[] byteArray(int... ints) {
    byte[] bytes = new byte[ints.length];

    for (int i = 0; i < ints.length; i++) {
      bytes[i] = (byte) ints[i];
    }

    return bytes;
  }

  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .include(DeflateDecompression.class.getSimpleName())
      .forks(1)
      .build();

    new Runner(opt).run();
  }
}
