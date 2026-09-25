/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.snapshot.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.snapshots.SnapshotChunk;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.charset.StandardCharsets;
import java.util.function.IntFunction;
import java.util.stream.Stream;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class SnapshotChunkImplTest {

  private static final byte[] CONTENT = "snapshot-chunk-content".getBytes(StandardCharsets.UTF_8);

  @ParameterizedTest(name = "{0} message at offset {1}")
  @MethodSource("messages")
  void shouldReadContentBufferFromMessage(
      final IntFunction<ByteBuffer> allocator, final int offset) {
    // given
    final var chunk = decode(allocator, CONTENT, offset);

    // when
    final var contentBuffer = chunk.getContentBuffer();

    // then
    assertThat(contentBuffer).isEqualTo(ByteBuffer.wrap(CONTENT));
    assertThat(contentBuffer).isEqualTo(ByteBuffer.wrap(chunk.getContent()));
  }

  @Test
  void shouldReadContentBufferWithoutCopyingTheMessage() {
    // given
    final var message = encode(CONTENT, 0);
    final var chunk = decode(new UnsafeBuffer(message));
    final var contentBuffer = chunk.getContentBuffer();
    assertThat(contentBuffer).isEqualTo(ByteBuffer.wrap(CONTENT));

    // when the message bytes change, the view must observe it, proving it is not a copy
    final var contentStart = message.length - CONTENT.length;
    message[contentStart] = (byte) (message[contentStart] + 1);

    // then
    assertThat(contentBuffer.get(0)).isEqualTo(message[contentStart]);
    assertThat(contentBuffer.remaining()).isEqualTo(CONTENT.length);
  }

  @Test
  void shouldReturnIndependentContentBufferViews() {
    // given
    final var chunk = decode(new UnsafeBuffer(encode(CONTENT, 0)));
    final var first = chunk.getContentBuffer();

    // when
    first.position(first.limit());
    final var second = chunk.getContentBuffer();

    // then
    assertThat(first.hasRemaining()).isFalse();
    assertThat(second.position()).isZero();
    assertThat(second.remaining()).isEqualTo(CONTENT.length);
  }

  @Test
  void shouldReturnReadOnlyContentBuffer() {
    // given
    final var chunk = decode(new UnsafeBuffer(encode(CONTENT, 0)));

    // when
    final var contentBuffer = chunk.getContentBuffer();

    // then
    assertThat(contentBuffer.isReadOnly()).isTrue();
    assertThatThrownBy(() -> contentBuffer.put(0, (byte) 1))
        .isInstanceOf(ReadOnlyBufferException.class);
  }

  @Test
  void shouldReturnEmptyContentBufferForEmptyContent() {
    // given
    final var chunk = decode(new UnsafeBuffer(encode(new byte[0], 0)));

    // when
    final var contentBuffer = chunk.getContentBuffer();

    // then
    assertThat(contentBuffer.hasRemaining()).isFalse();
    assertThat(chunk.getContentLength()).isZero();
  }

  private static Stream<Arguments> messages() {
    final var allocators =
        Stream.of(
            Named.<IntFunction<ByteBuffer>>of("heap", ByteBuffer::allocate),
            Named.<IntFunction<ByteBuffer>>of("direct", ByteBuffer::allocateDirect));

    return allocators.flatMap(
        allocator -> Stream.of(0, 7).map(offset -> Arguments.of(allocator, offset)));
  }

  private static SnapshotChunkImpl decode(
      final IntFunction<ByteBuffer> allocator, final byte[] content, final int offset) {
    final var encoded = new SnapshotChunkImpl(new TestChunk(content));
    final var message = allocator.apply(offset + encoded.getLength());
    encoded.write(new UnsafeBuffer(message), offset);

    return decode(new UnsafeBuffer(message, offset, encoded.getLength()));
  }

  private static byte[] encode(final byte[] content, final int offset) {
    final var chunk = new SnapshotChunkImpl(new TestChunk(content));
    final var bytes = new byte[offset + chunk.getLength()];
    chunk.write(new UnsafeBuffer(bytes), offset);
    return bytes;
  }

  private static SnapshotChunkImpl decode(final UnsafeBuffer message) {
    final var chunk = new SnapshotChunkImpl();
    chunk.wrap(message);
    return chunk;
  }

  private record TestChunk(byte[] content) implements SnapshotChunk {

    @Override
    public String getSnapshotId() {
      return "1-1-1-1";
    }

    @Override
    public int getTotalCount() {
      return 1;
    }

    @Override
    public String getChunkName() {
      return "file";
    }

    @Override
    public long getChecksum() {
      return 1L;
    }

    @Override
    public byte[] getContent() {
      return content;
    }

    @Override
    public long getFileBlockPosition() {
      return 0;
    }

    @Override
    public long getTotalFileSize() {
      return content.length;
    }

    @Override
    public long getContentLength() {
      return content.length;
    }
  }
}
