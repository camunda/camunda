/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.log;

import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.alignedFramedLength;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.framedLength;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.lengthOffset;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.messageOffset;
import static io.zeebe.logstreams.impl.log.LogEntryDescriptor.getPosition;
import static io.zeebe.logstreams.impl.log.LogEntryDescriptor.headerLength;
import static io.zeebe.logstreams.impl.log.LogEntryDescriptor.positionOffset;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.logstreams.impl.log.CompleteEventsInBlockProcessor;
import io.zeebe.logstreams.spi.LogStorage;
import java.nio.ByteBuffer;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/** @author Christopher Zell <christopher.zell@camunda.com> */
public class CompleteInBlockProcessorTest {
  private static final int LENGTH = headerLength(0); // 44 -> 52
  private static final int ALIGNED_LEN = alignedFramedLength(LENGTH); // 56 -> 64
  private static final int SEGMENT_SIZE = 1024 * 16;
  private static final ByteBuffer SOURCE_BUFFER = ByteBuffer.allocate(SEGMENT_SIZE);

  @Rule public final ExpectedException thrown = ExpectedException.none();
  private final CompleteEventsInBlockProcessor processor = new CompleteEventsInBlockProcessor();

  /** Prepares some data to read from */
  @BeforeClass
  public static void setUp() {
    final MutableDirectBuffer directBuffer = new UnsafeBuffer(0, 0);
    directBuffer.wrap(SOURCE_BUFFER);

    // Buffer: [4test4asdf30012345678901234567890123456789]
    // small events
    int idx = 0;
    directBuffer.putInt(lengthOffset(idx), framedLength(LENGTH));
    directBuffer.putLong(positionOffset(messageOffset(idx)), 1);

    idx = ALIGNED_LEN;
    directBuffer.putInt(lengthOffset(idx), framedLength(LENGTH));
    directBuffer.putLong(positionOffset(messageOffset(idx)), 2);

    // a large event
    idx = 2 * ALIGNED_LEN;
    directBuffer.putInt(lengthOffset(idx), framedLength(headerLength(256)));
    directBuffer.putLong(positionOffset(messageOffset(idx)), 3);
  }

  @Test
  public void shouldReadAndProcessFirstEvent() {
    // given buffer, which could contain first event
    final int readResult = ALIGNED_LEN;
    final ByteBuffer readBuffer = SOURCE_BUFFER.slice().position(readResult);

    // when read into buffer and buffer was processed
    final long result = processor.process(readBuffer, readResult);

    // then
    // result is equal to start address plus event size
    assertThat(result).isEqualTo(ALIGNED_LEN);
    final DirectBuffer buffer = new UnsafeBuffer(0, 0);
    buffer.wrap(readBuffer);

    // first event was read
    assertThat(buffer.getInt(lengthOffset(0))).isEqualTo(framedLength(LENGTH));
    assertThat(getPosition(buffer, 0)).isEqualTo(1);
  }

  @Test
  public void shouldReadAndProcessTwoEvents() {
    // given buffer, which could contain 2 events
    final int readResult = 2 * ALIGNED_LEN;
    final ByteBuffer readBuffer = SOURCE_BUFFER.slice().position(2 * ALIGNED_LEN);

    // when read into buffer and buffer was processed
    final long result = processor.process(readBuffer, readResult);

    // then
    // returned address is equal to start address plus two event sizes
    assertThat(result).isEqualTo(readResult);
    final DirectBuffer buffer = new UnsafeBuffer(0, 0);
    buffer.wrap(readBuffer);

    // first event was read
    assertThat(buffer.getInt(lengthOffset(0))).isEqualTo(framedLength(LENGTH));
    assertThat(getPosition(buffer, 0)).isEqualTo(1);

    // second event was read as well
    assertThat(buffer.getInt(lengthOffset(ALIGNED_LEN))).isEqualTo(framedLength(LENGTH));
    assertThat(getPosition(buffer, ALIGNED_LEN)).isEqualTo(2);
  }

  @Test
  public void shouldTruncateHalfEvent() {
    // given buffer, which could contain 1.5 events
    final int readResult = Math.floorDiv(ALIGNED_LEN * 3, 2);
    final ByteBuffer readBuffer = SOURCE_BUFFER.slice().position(readResult);

    // when read into buffer and buffer was processed
    final long result = processor.process(readBuffer, readResult);

    // then
    // result is equal to start address plus one event size
    assertThat(result).isEqualTo(ALIGNED_LEN);
    final DirectBuffer buffer = new UnsafeBuffer(0, 0);
    buffer.wrap(readBuffer);

    // and only first event is read
    assertThat(buffer.getInt(lengthOffset(0))).isEqualTo(framedLength(LENGTH));
    assertThat(getPosition(buffer, 0)).isEqualTo(1);

    // position and limit is reset
    assertThat(readBuffer.position()).isEqualTo(ALIGNED_LEN);
    assertThat(readBuffer.limit()).isEqualTo(ALIGNED_LEN);
  }

  @Test
  public void shouldTruncateEventWithMissingLen() {
    // given buffer, which could contain one event and only 3 next bits
    // so not the complete next message len
    final int readResult = ALIGNED_LEN + 3;
    final ByteBuffer readBuffer = SOURCE_BUFFER.slice().position(readResult);

    // when read into buffer and buffer was processed
    final long result = processor.process(readBuffer, readResult);

    // then
    // result is equal to start address plus one event size
    assertThat(result).isEqualTo(ALIGNED_LEN);
    final DirectBuffer buffer = new UnsafeBuffer(0, 0);
    buffer.wrap(readBuffer);

    // and only first event is read
    assertThat(buffer.getInt(lengthOffset(0))).isEqualTo(framedLength(LENGTH));
    assertThat(getPosition(buffer, 0)).isEqualTo(1);

    // position and limit is reset
    assertThat(readBuffer.position()).isEqualTo(ALIGNED_LEN);
    assertThat(readBuffer.limit()).isEqualTo(ALIGNED_LEN);
  }

  @Test
  public void shouldInsufficientBufferCapacity() {
    // given buffer, which could not contain an event
    final int readResult = ALIGNED_LEN - 1;
    final ByteBuffer readBuffer = SOURCE_BUFFER.slice().position(readResult);

    // when read into buffer and buffer was processed
    final long result = processor.process(readBuffer, readResult);

    // then result is OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY
    assertThat(result).isEqualTo(LogStorage.OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY);
  }

  @Test
  public void shouldTruncateBufferOnHalfBufferWasRead() {
    // given buffer
    final int readResult = alignedFramedLength(headerLength(256));
    final ByteBuffer readBuffer = SOURCE_BUFFER.slice().position(readResult);

    // when read into buffer and buffer was processed
    final long result = processor.process(readBuffer, readResult);

    // then only first 2 small events can be read
    // third event was to large, since position is EQUAL to remaining bytes,
    // which means buffer is half full, the corresponding next address will be returned
    // and block idx can for example be created
    assertThat(result).isEqualTo(ALIGNED_LEN * 2);
    final DirectBuffer buffer = new UnsafeBuffer(0, 0);
    buffer.wrap(readBuffer);

    // first event was read
    assertThat(buffer.getInt(lengthOffset(0))).isEqualTo(framedLength(LENGTH));
    assertThat(getPosition(buffer, 0)).isEqualTo(1);

    // second event was read as well
    assertThat(buffer.getInt(lengthOffset(ALIGNED_LEN))).isEqualTo(framedLength(LENGTH));
    assertThat(getPosition(buffer, ALIGNED_LEN)).isEqualTo(2);

    // position and limit is reset
    assertThat(readBuffer.position()).isEqualTo(2 * ALIGNED_LEN);
    assertThat(readBuffer.limit()).isEqualTo(2 * ALIGNED_LEN);
  }
}
