package io.camunda.common.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class Java8Utils {

  private Java8Utils() {}

  public static byte[] readAllBytes(InputStream inputStream) throws IOException {
    final int bufLen = 4 * 0x400; // 4KB
    byte[] buf = new byte[bufLen];
    int readLen;
    IOException exception = null;

    try {
      try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
        while ((readLen = inputStream.read(buf, 0, bufLen)) != -1)
          outputStream.write(buf, 0, readLen);

        return outputStream.toByteArray();
      }
    } catch (IOException e) {
      exception = e;
      throw e;
    } finally {
      if (exception == null) inputStream.close();
      else
        try {
          inputStream.close();
        } catch (IOException e) {
          exception.addSuppressed(e);
        }
    }
  }
}
