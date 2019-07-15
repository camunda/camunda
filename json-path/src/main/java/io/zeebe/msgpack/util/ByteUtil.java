/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.util;

import org.agrona.DirectBuffer;

public class ByteUtil {

  public static boolean equal(byte[] arr1, DirectBuffer buf2, int buf2Offset, int length) {
    if (arr1.length != length || buf2.capacity() < buf2Offset + length) {
      return false;
    } else {
      boolean equal = true;
      for (int i = 0; i < arr1.length && equal; i++) {
        equal = arr1[i] == buf2.getByte(buf2Offset + i);
      }
      return equal;
    }
  }

  public static boolean equal(
      DirectBuffer buf1,
      int buf1Offset,
      int buf1Length,
      DirectBuffer buf2,
      int buf2Offset,
      int buf2Length) {
    if (buf1Length != buf2Length) {
      return false;
    } else {
      boolean equal = true;
      for (int i = 0; i < buf1Length && equal; i++) {
        equal = buf1.getByte(buf1Offset + i) == buf2.getByte(buf2Offset + i);
      }
      return equal;
    }
  }

  public static String bytesToBinary(byte[] bytes) {
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < bytes.length; i++) {
      final String binaryString = Integer.toBinaryString(Byte.toUnsignedInt(bytes[i]));
      final int missingLeadingZeroes = 8 - binaryString.length();
      for (int j = 0; j < missingLeadingZeroes; j++) {
        sb.append("0");
      }
      sb.append(binaryString);
      sb.append(", ");
    }
    return sb.toString();
  }

  /** with respect to utf8 */
  public static boolean isNumeric(DirectBuffer buffer, int offset, int length) {
    for (int i = offset; i < offset + length; i++) {
      final byte curr = buffer.getByte(i);
      if (curr < 48 || curr > 57) {
        return false;
      }
    }

    return true;
  }

  /**
   * With respect to utf8. Assuming {@link #isNumeric(DirectBuffer, int, int)} returns true for this
   * buffer portion.
   */
  public static int parseInteger(DirectBuffer buffer, int offset, int length) {
    int value = 0;
    int exponent = 1;
    for (int i = length - 1; i >= 0; i--) {
      final byte curr = buffer.getByte(offset + i);
      value += (curr - 48) * exponent;
      exponent *= 10;
    }

    return value;
  }
}
