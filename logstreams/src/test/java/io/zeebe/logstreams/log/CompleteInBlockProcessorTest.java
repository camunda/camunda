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
import static io.zeebe.logstreams.impl.LogEntryDescriptor.getPosition;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.headerLength;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.positionOffset;
import static io.zeebe.logstreams.spi.LogStorage.OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.logstreams.impl.CompleteEventsInBlockProcessor;
import io.zeebe.logstreams.impl.log.fs.FsLogStorage;
import io.zeebe.logstreams.impl.log.fs.FsLogStorageConfiguration;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

/** @author Christopher Zell <christopher.zell@camunda.com> */
public class CompleteInBlockProcessorTest {
  protected static final int LENGTH = headerLength(0); // 44 -> 52
  protected static final int ALIGNED_LEN = alignedFramedLength(LENGTH); // 56 -> 64
  private static final int SEGMENT_SIZE = 1024 * 16;
  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  @Rule public ExpectedException thrown = ExpectedException.none();

  private CompleteEventsInBlockProcessor processor;
  private String logPath;
  private FsLogStorageConfiguration fsStorageConfig;
  private FsLogStorage fsLogStorage;
  private long appendedAddress;

  @Before
  public void init() throws IOException {
    processor = new CompleteEventsInBlockProcessor();
    logPath = tempFolder.getRoot().getAbsolutePath();
    fsStorageConfig = new FsLogStorageConfiguration(SEGMENT_SIZE, logPath, 0, false);
    fsLogStorage = new FsLogStorage(fsStorageConfig);

    final ByteBuffer writeBuffer = ByteBuffer.allocate(192);
    final MutableDirectBuffer directBuffer = new UnsafeBuffer(0, 0);
    directBuffer.wrap(writeBuffer);

    /*
    Buffer: [4test4asdf30012345678901234567890123456789]
    */
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

    fsLogStorage.open();
    appendedAddress = fsLogStorage.append(writeBuffer);
  }

  @Test
  public void shouldReadAndProcessFirstEvent() {
    // given buffer, which could contain first event
    final ByteBuffer readBuffer = ByteBuffer.allocate(ALIGNED_LEN);

    // when read into buffer and buffer was processed
    final long result = fsLogStorage.read(readBuffer, appendedAddress, processor);

    // then
    // result is equal to start address plus event size
    assertThat(result).isEqualTo(appendedAddress + ALIGNED_LEN);
    final DirectBuffer buffer = new UnsafeBuffer(0, 0);
    buffer.wrap(readBuffer);

    // first event was read
    assertThat(buffer.getInt(lengthOffset(0))).isEqualTo(framedLength(LENGTH));
    assertThat(getPosition(buffer, 0)).isEqualTo(1);
  }

  @Test
  public void shouldReadAndProcessTwoEvents() {
    // given buffer, which could contain 2 events
    final ByteBuffer readBuffer = ByteBuffer.allocate(2 * ALIGNED_LEN);

    // when read into buffer and buffer was processed
    final long result = fsLogStorage.read(readBuffer, appendedAddress, processor);

    // then
    // returned address is equal to start address plus two event sizes
    assertThat(result).isEqualTo(appendedAddress + ALIGNED_LEN * 2);
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
    final ByteBuffer readBuffer = ByteBuffer.allocate((int) (ALIGNED_LEN * 1.5));

    // when read into buffer and buffer was processed
    final long result = fsLogStorage.read(readBuffer, appendedAddress, processor);

    // then
    // result is equal to start address plus one event size
    assertThat(result).isEqualTo(appendedAddress + ALIGNED_LEN);
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
    final ByteBuffer readBuffer = ByteBuffer.allocate((ALIGNED_LEN + 3));

    // when read into buffer and buffer was processed
    final long result = fsLogStorage.read(readBuffer, appendedAddress, processor);

    // then
    // result is equal to start address plus one event size
    assertThat(result).isEqualTo(appendedAddress + ALIGNED_LEN);
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
    final ByteBuffer readBuffer = ByteBuffer.allocate((ALIGNED_LEN - 1));

    // when read into buffer and buffer was processed
    final long result = fsLogStorage.read(readBuffer, appendedAddress, processor);

    // then result is OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY
    assertThat(result).isEqualTo(OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY);
  }

  @Test
  public void shouldInsufficientBufferCapacityIfEventIsLargerThenBufferCapacity() {
    // given
    final ByteBuffer readBuffer = ByteBuffer.allocate(2 * ALIGNED_LEN + ALIGNED_LEN);

    // when
    final long result = fsLogStorage.read(readBuffer, appendedAddress, processor);

    // then
    assertThat(result).isEqualTo(OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY);
  }

  @Test
  public void shouldInsufficientBufferCapacityIfPosWasSetAndNewEventCantReadCompletely()
      throws IOException {
    // given
    final int largeEventMetadataSize = 8;
    final int writeBufferLength = (3 * ALIGNED_LEN) + largeEventMetadataSize;
    final ByteBuffer writeBuffer = ByteBuffer.allocate(writeBufferLength);
    final MutableDirectBuffer directBuffer = new UnsafeBuffer(0, 0);
    directBuffer.wrap(writeBuffer);

    int idx = 0;
    directBuffer.putInt(lengthOffset(idx), framedLength(LENGTH));
    directBuffer.putLong(positionOffset(messageOffset(idx)), 1);

    idx = ALIGNED_LEN;
    directBuffer.putInt(lengthOffset(idx), framedLength(LENGTH));
    directBuffer.putLong(positionOffset(messageOffset(idx)), 2);

    // a large event
    idx = 2 * ALIGNED_LEN;
    directBuffer.putInt(lengthOffset(idx), framedLength(headerLength(largeEventMetadataSize)));
    directBuffer.putLong(positionOffset(messageOffset(idx)), 3);

    final long appendedAddress = fsLogStorage.append(writeBuffer);

    final ByteBuffer smallBuffer = ByteBuffer.allocate(2 * ALIGNED_LEN);

    // when
    final long result = fsLogStorage.read(smallBuffer, appendedAddress, processor);

    // then
    assertThat(smallBuffer.position()).isEqualTo(smallBuffer.capacity());

    // when
    smallBuffer.position(ALIGNED_LEN);
    final long newResult = fsLogStorage.read(smallBuffer, result, processor);

    // then
    assertThat(newResult).isEqualTo(OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY);

    // when
    smallBuffer.limit(ALIGNED_LEN);
    smallBuffer.position(0);
    final ByteBuffer largerBuffer = ByteBuffer.allocate(4 * ALIGNED_LEN);
    largerBuffer.put(smallBuffer);
    final long opResult = fsLogStorage.read(largerBuffer, result, processor);

    // then
    assertThat(opResult).isGreaterThan(result);
    assertThat(largerBuffer.position()).isEqualTo(writeBufferLength - ALIGNED_LEN);
    assertThat(largerBuffer.limit()).isEqualTo(writeBufferLength - ALIGNED_LEN);
  }

  @Test
  public void shouldTruncateBufferOnHalfBufferWasRead() {
    // given buffer
    final ByteBuffer readBuffer = ByteBuffer.allocate(alignedFramedLength(headerLength(256)));

    // when read into buffer and buffer was processed
    final long result = fsLogStorage.read(readBuffer, appendedAddress, processor);

    // then only first 2 small events can be read
    // third event was to large, since position is EQUAL to remaining bytes,
    // which means buffer is half full, the corresponding next address will be returned
    // and block idx can for example be created
    assertThat(result).isEqualTo(appendedAddress + ALIGNED_LEN * 2);
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
