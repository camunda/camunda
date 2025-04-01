/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.buffer;

import static io.camunda.zeebe.util.EnsureUtil.ensureGreaterThanOrEqual;
import static io.camunda.zeebe.util.StringUtil.getBytes;

import java.nio.charset.StandardCharsets;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class BufferUtil {
  public static final int NO_WRAP = 1;
  private static final int DEFAULT_WRAP = 16; // bytes

  private static final char[] HEX_CODE = "0123456789ABCDEF".toCharArray();

  private BufferUtil() { // avoid instantiation of util class
  }

  public static String bufferAsString(final DirectBuffer buffer) {
    return bufferAsString(buffer, 0, buffer.capacity());
  }

  public static String bufferAsString(
      final DirectBuffer buffer, final int offset, final int length) {
    if (length == 0) {
      return "";
    }
    final byte[] bytes = new byte[length];

    buffer.getBytes(offset, bytes);

    return new String(bytes, StandardCharsets.UTF_8);
  }

  public static DirectBuffer wrapString(final String argument) {
    return new UnsafeBuffer(getBytes(argument));
  }

  /** Compare the given buffers. */
  public static boolean equals(final DirectBuffer buffer1, final DirectBuffer buffer2) {
    if (buffer1 instanceof UnsafeBuffer && buffer2 instanceof UnsafeBuffer) {
      return buffer1.equals(buffer2);
    } else if (buffer1 instanceof ExpandableArrayBuffer
        && buffer2 instanceof ExpandableArrayBuffer) {
      return buffer1.equals(buffer2);
    } else {
      return contentsEqual(buffer1, buffer2);
    }
  }

  /** byte-by-byte comparison of two buffers */
  public static boolean contentsEqual(final DirectBuffer buffer1, final DirectBuffer buffer2) {

    if (buffer1.capacity() == buffer2.capacity()) {
      boolean equal = true;

      for (int i = 0; i < buffer1.capacity() && equal; i++) {
        equal &= buffer1.getByte(i) == buffer2.getByte(i);
      }

      return equal;
    } else {
      return false;
    }
  }

  public static DirectBuffer createCopy(final BufferWriter writer) {
    final var buffer = new UnsafeBuffer(new byte[writer.getLength()]);
    writer.write(buffer, 0);
    return buffer;
  }

  /**
   * Copies the contents of the source writer into the destination reader via a fresh intermediate
   * buffer.
   *
   * @param source the buffer to copy from
   * @param dest the buffer to write to
   */
  public static void copy(final BufferWriter source, final BufferReader dest) {
    final var buffer = createCopy(source);
    dest.wrap(buffer, 0, buffer.capacity());
  }

  /**
   * Creates a new instance of the src buffer class and copies the underlying bytes.
   *
   * @param src the buffer to copy from
   * @return the new buffer instance
   */
  public static DirectBuffer cloneBuffer(final DirectBuffer src) {
    return cloneBuffer(src, 0, src.capacity());
  }

  /**
   * Creates a new instance of the src buffer class and copies the underlying bytes.
   *
   * @param src the buffer to copy from
   * @param offset the offset to start in the src buffer
   * @param length the number of bytes to clone
   * @return the new buffer instance
   */
  public static DirectBuffer cloneBuffer(
      final DirectBuffer src, final int offset, final int length) {
    final int availableBytes = src.capacity() - offset;

    ensureGreaterThanOrEqual("available bytes", availableBytes, length);

    if (src instanceof UnsafeBuffer) {
      final byte[] dst = new byte[length];
      src.getBytes(offset, dst);
      return new UnsafeBuffer(dst);
    } else if (src instanceof ExpandableArrayBuffer) {
      final ExpandableArrayBuffer dst = new ExpandableArrayBuffer(length);
      src.getBytes(offset, dst, 0, length);
      return dst;
    } else {
      throw new RuntimeException(
          "Unable to clone buffer of class " + src.getClass().getSimpleName());
    }
  }

  public static String bufferAsHexString(final DirectBuffer buffer) {
    return bufferAsHexString(buffer, DEFAULT_WRAP);
  }

  private static String bufferAsHexString(final DirectBuffer buffer, final int wrap) {
    return bufferAsHexString(buffer, 0, buffer.capacity(), wrap);
  }

  private static String bufferAsHexString(
      final DirectBuffer buffer, final int offset, final int length, final int wrap) {
    final byte[] bytes = new byte[length];
    buffer.getBytes(offset, bytes, 0, length);

    return bytesAsHexString(bytes, wrap);
  }

  public static String bytesAsHexString(final byte[] bytes) {
    return bytesAsHexString(bytes, DEFAULT_WRAP);
  }

  public static String bytesAsHexString(final byte[] bytes, final int wrap) {
    final int length = bytes.length;

    final StringBuilder builder = new StringBuilder(length * 4);
    final StringBuilder hexBuilder = new StringBuilder(wrap * 3);
    final StringBuilder asciiBuilder = new StringBuilder(wrap);

    for (int line = 0; line <= (length / wrap); line++) {
      builder.append(String.format("0x%08x: ", line * wrap));
      for (int i = 0; i < wrap; i++) {
        final int index = (line * wrap) + i;

        if (index < length) {
          final byte b = bytes[index];
          hexBuilder.append(HEX_CODE[(b >> 4) & 0xF]).append(HEX_CODE[(b & 0xF)]).append(' ');

          // check if byte is ASCII character range other wise use . as placeholder
          if (b > 31 && b < 126) {
            asciiBuilder.append((char) b);
          } else {
            asciiBuilder.append('.');
          }
        } else {
          // padding
          hexBuilder.append("   ");
        }
      }
      builder
          .append(hexBuilder.toString())
          .append('|')
          .append(asciiBuilder.toString())
          .append("|\n");

      asciiBuilder.delete(0, asciiBuilder.length());
      hexBuilder.delete(0, hexBuilder.length());
    }

    return builder.toString();
  }

  /**
   * @return a new array that is a copy of the buffer's contents
   */
  public static byte[] bufferAsArray(final DirectBuffer buffer) {
    final byte[] array;

    array = new byte[buffer.capacity()];
    buffer.getBytes(0, array);

    return array;
  }

  public static byte[] bufferAsArray(final BufferWriter buffer) {

    final var bytes = new byte[buffer.getLength()];
    final var writeBuffer = new UnsafeBuffer();
    writeBuffer.wrap(bytes);

    buffer.write(writeBuffer, 0);
    return bytes;
  }

  public static MutableDirectBuffer wrapArray(final byte[] array) {
    return new UnsafeBuffer(array);
  }

  /**
   * Performs byte wise comparison of a given byte array and a prefix.
   *
   * @param prefix the prefix to look for
   * @param prefixOffset offset in the prefix array
   * @param prefixLength length of the prefix
   * @param content the array to check against
   * @param contentOffset the offset in the content array
   * @param contentLength the length of the content to check
   * @return true if array starts with the all bytes contained in prefix
   */
  public static boolean startsWith(
      final byte[] prefix,
      final int prefixOffset,
      final int prefixLength,
      final byte[] content,
      int contentOffset,
      final int contentLength) {
    if (contentLength < prefixLength) {
      return false;
    }

    for (int i = prefixOffset; i < prefixLength; i++, contentOffset++) {
      if (content[contentOffset] != prefix[i]) {
        return false;
      }
    }

    return true;
  }
}
