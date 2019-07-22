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
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class DeflateDecompression {

  private static final byte[] compressed = byteArray(0x25, 0x8a, 0xb1, 0x01, 0x00, 0x00, 0x0c, 0xc1, 0x6e, 0x95, 0xfc, 0xff, 0x43, 0xb5, 0xb5, 0x10, 0x98, 0x40, 0x2a, 0x72, 0xf6, 0x09, 0xe3, 0x83, 0x6d, 0x4b, 0x37, 0xb2, 0x1f, 0x07);

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
