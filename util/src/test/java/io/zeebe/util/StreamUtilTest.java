/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util;

import static io.zeebe.util.StreamUtil.readLong;
import static io.zeebe.util.StreamUtil.writeLong;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

/** */
public class StreamUtilTest {
  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  @Rule public ExpectedException exception = ExpectedException.none();

  private File file;

  @Before
  public void init() throws IOException {
    file = tempFolder.newFile();
  }

  @Test
  public void shouldReadAndWriteLong() throws Exception {
    for (int pow = 0; pow < 64; pow++) {
      // given
      final FileOutputStream fileOutputStream = new FileOutputStream(file);
      final long value = 1L << pow;

      // when
      writeLong(fileOutputStream, value);

      // then
      final FileInputStream fileInputStream = new FileInputStream(file);
      final long readValue = readLong(fileInputStream);
      assertThat(readValue).isEqualTo(value);
    }
  }

  @Test
  public void shouldReadStreamIntoExpandableBuffer() throws IOException {
    // given
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer(2);
    final byte[] thingsToRead = new byte[] {1, 2, 3, 4};
    final InputStream stream =
        new RepeatedlyFailingInputStream(new ByteArrayInputStream(thingsToRead), 2);

    // when
    StreamUtil.read(stream, buffer, 4);

    // then
    assertThat(buffer.capacity()).isGreaterThanOrEqualTo(4 + thingsToRead.length);

    assertThat(buffer.getByte(4)).isEqualTo((byte) 1);
    assertThat(buffer.getByte(5)).isEqualTo((byte) 2);
    assertThat(buffer.getByte(6)).isEqualTo((byte) 3);
    assertThat(buffer.getByte(7)).isEqualTo((byte) 4);
  }

  @Test
  public void shouldNotReadIntoDirectBuffer() throws IOException {
    // given
    final ExpandableDirectByteBuffer buffer = new ExpandableDirectByteBuffer(2);
    final byte[] thingsToRead = new byte[] {1, 2, 3, 4};
    final InputStream stream = new ByteArrayInputStream(thingsToRead);

    // then
    exception.expect(RuntimeException.class);
    exception.expectMessage("Cannot be used with direct byte buffers");

    // when
    StreamUtil.read(stream, buffer, 4);
  }

  @Test
  public void shouldWriteByteArrayBackedDirectBufferToStream() throws Exception {
    // given
    final byte[] value = "foo".getBytes();
    final DirectBuffer buffer = new UnsafeBuffer(value);
    final ByteArrayOutputStream outputStream = new ByteArrayOutputStream(buffer.capacity());

    // when
    StreamUtil.write(buffer, outputStream);

    // then
    assertThat(outputStream.toByteArray()).isEqualTo(value);

    // when
    final int offset = 1;
    outputStream.reset();
    StreamUtil.write(buffer, outputStream, offset, value.length - offset);

    // then
    assertThat(outputStream.toByteArray())
        .isEqualTo(Arrays.copyOfRange(value, offset, value.length));
  }

  @Test
  public void shouldWriteByteArrayBackedDirectBufferToStreamWithWrapAdjustement() throws Exception {
    final byte[] value = "foo".getBytes();
    final int bufferOffset = 1;
    final int bufferLength = value.length - bufferOffset;
    final DirectBuffer buffer = new UnsafeBuffer(value, bufferOffset, bufferLength);
    final ByteArrayOutputStream outputStream = new ByteArrayOutputStream(buffer.capacity());

    // when
    StreamUtil.write(buffer, outputStream, 0, bufferLength);

    // then
    assertThat(outputStream.toByteArray())
        .isEqualTo(Arrays.copyOfRange(value, bufferOffset, value.length));

    // when
    final int offset = 1;
    outputStream.reset();
    StreamUtil.write(buffer, outputStream, offset, bufferLength - offset);

    // then
    assertThat(outputStream.toByteArray())
        .isEqualTo(Arrays.copyOfRange(value, offset, value.length - offset));
  }

  @Test
  public void shouldWriteByteBufferBackedDirectBufferToStream() throws Exception {
    // given
    final byte[] value = "foo".getBytes();
    final ByteBuffer underlyingBuffer = ByteBuffer.allocateDirect(value.length);
    final DirectBuffer buffer = new UnsafeBuffer(underlyingBuffer);
    final ByteArrayOutputStream outputStream = new ByteArrayOutputStream(buffer.capacity());

    // when
    underlyingBuffer.put(value);
    StreamUtil.write(buffer, outputStream);

    // then
    assertThat(outputStream.toByteArray()).isEqualTo(value);

    // when
    final int offset = 1;
    outputStream.reset();
    StreamUtil.write(buffer, outputStream, offset, value.length - offset);

    // then
    assertThat(outputStream.toByteArray())
        .isEqualTo(Arrays.copyOfRange(value, offset, value.length));
  }

  @Test
  public void shouldWriteByteBufferBackedDirectBufferToStreamWithWrapAdjustement()
      throws Exception {
    final byte[] value = "foo".getBytes();
    final int bufferOffset = 1;
    final int bufferLength = value.length - bufferOffset;
    final ByteBuffer underlyingBuffer = ByteBuffer.allocateDirect(value.length);
    final DirectBuffer buffer = new UnsafeBuffer(underlyingBuffer, bufferOffset, bufferLength);
    final ByteArrayOutputStream outputStream = new ByteArrayOutputStream(buffer.capacity());

    // when
    underlyingBuffer.put(value);
    StreamUtil.write(buffer, outputStream, 0, bufferLength);

    // then
    assertThat(outputStream.toByteArray())
        .isEqualTo(Arrays.copyOfRange(value, bufferOffset, value.length));

    // when
    final int offset = 1;
    outputStream.reset();
    StreamUtil.write(buffer, outputStream, offset, bufferLength - offset);

    // then
    assertThat(outputStream.toByteArray())
        .isEqualTo(Arrays.copyOfRange(value, offset, value.length - 1));
  }
}
