/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.common.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class Java8Utils {

  private Java8Utils() {}

  public static byte[] readAllBytes(final InputStream inputStream) throws IOException {
    final int bufLen = 4 * 0x400; // 4KB
    final byte[] buf = new byte[bufLen];
    int readLen;
    IOException exception = null;

    try {
      try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
        while ((readLen = inputStream.read(buf, 0, bufLen)) != -1)
          outputStream.write(buf, 0, readLen);

        return outputStream.toByteArray();
      }
    } catch (final IOException e) {
      exception = e;
      throw e;
    } finally {
      if (exception == null) inputStream.close();
      else
        try {
          inputStream.close();
        } catch (final IOException e) {
          exception.addSuppressed(e);
        }
    }
  }
}
