/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util;

public final class VarDataUtil {

  public static byte[] readBytes(
      final VarDataReader reader, final VarDataLengthProvider lengthProvider) {
    return readBytes(reader, lengthProvider.length());
  }

  public static byte[] readBytes(final VarDataReader reader, final int length) {
    return readBytes(reader, 0, length);
  }

  public static byte[] readBytes(final VarDataReader reader, final int offset, final int length) {
    final byte[] buffer = new byte[length];
    reader.decode(buffer, offset, length);
    return buffer;
  }

  @FunctionalInterface
  public interface VarDataLengthProvider {
    int length();
  }

  @FunctionalInterface
  public interface VarDataReader {
    int decode(byte[] buffer, int offset, int length);
  }
}
