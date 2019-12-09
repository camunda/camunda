/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util;

import static io.zeebe.util.StringUtil.fromBytes;
import static io.zeebe.util.StringUtil.getBytes;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class StreamUtil {
  protected static final int DEFAULT_BUFFER_SIZE = 4 * 1024;

  public static MessageDigest getDigest(final String algorithm) {
    try {
      return MessageDigest.getInstance(algorithm);
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public static MessageDigest getSha1Digest() {
    return getDigest("SHA1");
  }

  public static MessageDigest updateDigest(
      final MessageDigest messageDigest, final InputStream data) throws IOException {
    final byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];

    int n;
    while ((n = data.read(buffer)) > -1) {
      messageDigest.update(buffer, 0, n);
    }
    return messageDigest;
  }

  public static String digestAsHex(final MessageDigest messageDigest) {
    final byte[] digest = messageDigest.digest();
    final byte[] hexByteArray = BitUtil.toHexByteArray(digest);

    return fromBytes(hexByteArray);
  }

  public static int copy(final InputStream input, final OutputStream output) throws IOException {
    final byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];

    int count = 0;
    int n;
    while ((n = input.read(buffer)) > -1) {
      output.write(buffer, 0, n);
      count += n;
    }
    return count;
  }

  public static void write(final File file, final String data) throws IOException {
    try (FileOutputStream os = new FileOutputStream(file)) {
      os.write(getBytes(data));
    }
  }

  public static void write(
      final File file, final InputStream data, final MessageDigest messageDigest)
      throws IOException {
    try (DigestOutputStream os =
        new DigestOutputStream(new FileOutputStream(file), messageDigest)) {
      copy(data, os);
    }

    final String digest = digestAsHex(messageDigest);
    final String fileName = file.getName();

    final String content = String.format("%s %s", digest, fileName);

    final String algorithm = messageDigest.getAlgorithm().toLowerCase();
    final String targetFileName = String.format("%s.%s", file.getAbsolutePath(), algorithm);

    write(new File(targetFileName), content);
  }

  public static int read(final InputStream input, final byte[] dst) throws IOException {
    return read(input, dst, 0);
  }

  public static int read(
      final InputStream input, final MutableDirectBuffer buffer, final int offset)
      throws IOException {
    int bytesRead;

    if (buffer.byteArray() == null) {
      throw new RuntimeException("Cannot be used with direct byte buffers");
    }

    int writeOffset = offset;

    do {
      buffer.checkLimit(
          offset + DEFAULT_BUFFER_SIZE); // for expandable buffers, this triggers expansion
      bytesRead = input.read(buffer.byteArray(), writeOffset, DEFAULT_BUFFER_SIZE);

      if (bytesRead > 0) {
        writeOffset += bytesRead;
      }

    } while (bytesRead >= 0);

    return writeOffset - offset;
  }

  public static int read(final InputStream input, final byte[] dst, final int offset)
      throws IOException {
    int remaining = dst.length - offset;
    int location = offset;

    while (remaining > 0) {
      final int count = input.read(dst, location, remaining);
      if (count == -1) {
        break;
      }
      remaining -= count;
      location += count;
    }

    return location - offset;
  }

  public static byte[] read(final InputStream input) throws IOException {
    final byte[] byteBuffer = new byte[DEFAULT_BUFFER_SIZE];
    int readBytes;

    try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
      while ((readBytes = input.read(byteBuffer, 0, byteBuffer.length)) != -1) {
        buffer.write(byteBuffer, 0, readBytes);
      }

      buffer.flush();

      return buffer.toByteArray();
    }
  }

  /**
   * Writes the {@link DirectBuffer#capacity} bytes given buffer to the destination output.
   *
   * @param source buffer to write
   * @param destination output to write to
   * @throws IOException
   */
  public static void write(final DirectBuffer source, final OutputStream destination)
      throws IOException {
    write(source, destination, 0, source.capacity());
  }

  /**
   * Writes length bytes from source buffer, starting at the given offset, into the given
   * destination.
   *
   * @param source buffer to write
   * @param destination output to write to
   * @param offset offset at which to start writing from buffer
   * @param length number of bytes to write
   * @throws IOException
   */
  public static void write(
      final DirectBuffer source, final OutputStream destination, final int offset, final int length)
      throws IOException {
    final int realOffset = source.wrapAdjustment() + offset;
    if (source.byteArray() != null) {
      destination.write(source.byteArray(), realOffset, length);
    } else {
      final WritableByteChannel channel = Channels.newChannel(destination);
      final ByteBuffer writeBuffer = source.byteBuffer().asReadOnlyBuffer();

      writeBuffer.position(realOffset);
      writeBuffer.limit(realOffset + length);
      channel.write(writeBuffer);
    }
  }

  public static void writeLong(OutputStream outputStream, long longValue) throws IOException {
    outputStream.write((byte) longValue);
    outputStream.write((byte) (longValue >>> 8));
    outputStream.write((byte) (longValue >>> 16));
    outputStream.write((byte) (longValue >>> 24));
    outputStream.write((byte) (longValue >>> 32));
    outputStream.write((byte) (longValue >>> 40));
    outputStream.write((byte) (longValue >>> 48));
    outputStream.write((byte) (longValue >>> 56));
  }

  public static long readLong(InputStream inputStream) throws IOException {
    long value = inputStream.read();
    value += ((long) inputStream.read() << 8);
    value += ((long) inputStream.read() << 16);
    value += ((long) inputStream.read() << 24);
    value += ((long) inputStream.read() << 32);
    value += ((long) inputStream.read() << 40);
    value += ((long) inputStream.read() << 48);
    value += ((long) inputStream.read() << 56);
    return value;
  }
}
