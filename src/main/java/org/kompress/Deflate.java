package org.kompress;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.Inflater;

public class Deflate {

  public static byte[] decompress(byte[] compressed) {
    InputStream input = new DeflateInputStream(new ByteArrayInputStream(compressed));
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    while (true) {
      int read;
      try {
        read = input.read();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      if (read == -1) break;
      output.write(read);
    }

    return output.toByteArray();
  }
}
