/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.dispatcher.impl.log;

import static io.zeebe.dispatcher.impl.PositionUtil.position;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.FRAME_ALIGNMENT;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.HEADER_LENGTH;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.TYPE_MESSAGE;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.TYPE_PADDING;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.alignedFramedLength;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.enableFlagBatchBegin;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.enableFlagBatchEnd;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.flagsOffset;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.framedLength;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.lengthOffset;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.messageOffset;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.streamIdOffset;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.typeOffset;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.raft.zeebe.ZeebeEntry;
import io.zeebe.dispatcher.ClaimedFragmentBatch;
import io.zeebe.util.TriConsumer;
import java.util.function.BiConsumer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public final class ClaimedFragmentBatchTest {
  private static final Runnable DO_NOTHING = () -> {};
  private static final BiConsumer<Long, TriConsumer<ZeebeEntry, Long, Integer>> ADD_NOTHING =
      (a, b) -> {};

  private static final int PARTITION_ID = 1;
  private static final int PARTITION_OFFSET = 16;
  private static final int FRAGMENT_LENGTH = 1024;

  private static final byte[] MESSAGE = "message".getBytes();
  private static final int MESSAGE_LENGTH = MESSAGE.length;
  @Rule public final ExpectedException thrown = ExpectedException.none();
  private UnsafeBuffer underlyingBuffer;
  private ClaimedFragmentBatch claimedBatch;

  @Before
  public void init() {
    underlyingBuffer = new UnsafeBuffer(new byte[PARTITION_OFFSET + FRAGMENT_LENGTH]);
    claimedBatch = new ClaimedFragmentBatch();
  }

  @Test
  public void shouldAddFragment() {
    // given
    claimedBatch.wrap(
        underlyingBuffer, PARTITION_ID, PARTITION_OFFSET, FRAGMENT_LENGTH, DO_NOTHING, ADD_NOTHING);

    // when
    final long position = claimedBatch.nextFragment(MESSAGE_LENGTH, 1);

    final int fragmentOffset = claimedBatch.getFragmentOffset();
    claimedBatch.getBuffer().putBytes(fragmentOffset, MESSAGE);

    // then
    assertThat(position)
        .isEqualTo(position(PARTITION_ID, PARTITION_OFFSET + alignedFramedLength(MESSAGE_LENGTH)));
    assertThat(fragmentOffset).isEqualTo(HEADER_LENGTH);

    assertThat(underlyingBuffer.getInt(lengthOffset(PARTITION_OFFSET)))
        .isEqualTo(-framedLength(MESSAGE_LENGTH));
    assertThat(underlyingBuffer.getShort(typeOffset(PARTITION_OFFSET))).isEqualTo(TYPE_MESSAGE);
    assertThat(underlyingBuffer.getInt(streamIdOffset(PARTITION_OFFSET))).isEqualTo(1);

    final byte[] buffer = new byte[MESSAGE_LENGTH];
    underlyingBuffer.getBytes(messageOffset(PARTITION_OFFSET), buffer);
    assertThat(buffer).isEqualTo(MESSAGE);
  }

  @Test
  public void shouldAddMultipleFragments() {
    // given
    claimedBatch.wrap(
        underlyingBuffer, PARTITION_ID, PARTITION_OFFSET, FRAGMENT_LENGTH, DO_NOTHING, ADD_NOTHING);

    claimedBatch.nextFragment(MESSAGE_LENGTH, 1);

    // when
    final long position = claimedBatch.nextFragment(MESSAGE_LENGTH, 2);
    final int fragmentOffset = claimedBatch.getFragmentOffset();

    // then
    assertThat(position)
        .isEqualTo(
            position(PARTITION_ID, PARTITION_OFFSET + 2 * alignedFramedLength(MESSAGE_LENGTH)));
    assertThat(fragmentOffset).isEqualTo(HEADER_LENGTH + alignedFramedLength(MESSAGE_LENGTH));

    final int bufferOffset = PARTITION_OFFSET + alignedFramedLength(MESSAGE_LENGTH);
    assertThat(underlyingBuffer.getInt(lengthOffset(bufferOffset)))
        .isEqualTo(-framedLength(MESSAGE_LENGTH));
    assertThat(underlyingBuffer.getShort(typeOffset(bufferOffset))).isEqualTo(TYPE_MESSAGE);
    assertThat(underlyingBuffer.getInt(streamIdOffset(bufferOffset))).isEqualTo(2);
  }

  @Test
  public void shouldCommitBatch() {
    // given
    claimedBatch.wrap(
        underlyingBuffer, PARTITION_ID, PARTITION_OFFSET, FRAGMENT_LENGTH, DO_NOTHING, ADD_NOTHING);

    claimedBatch.nextFragment(MESSAGE_LENGTH, 1);
    claimedBatch.nextFragment(MESSAGE_LENGTH, 2);

    // when
    claimedBatch.commit((a, b, c) -> {});

    // then
    int bufferOffset = PARTITION_OFFSET;
    assertThat(underlyingBuffer.getInt(lengthOffset(bufferOffset)))
        .isEqualTo(framedLength(MESSAGE_LENGTH));
    assertThat(underlyingBuffer.getShort(typeOffset(bufferOffset))).isEqualTo(TYPE_MESSAGE);
    assertThat(underlyingBuffer.getByte(flagsOffset(bufferOffset)))
        .isEqualTo(enableFlagBatchBegin((byte) 0));

    bufferOffset += alignedFramedLength(MESSAGE_LENGTH);
    assertThat(underlyingBuffer.getInt(lengthOffset(bufferOffset)))
        .isEqualTo(framedLength(MESSAGE_LENGTH));
    assertThat(underlyingBuffer.getShort(typeOffset(bufferOffset))).isEqualTo(TYPE_MESSAGE);
    assertThat(underlyingBuffer.getByte(flagsOffset(bufferOffset)))
        .isEqualTo(enableFlagBatchEnd((byte) 0));
  }

  @Test
  public void shouldAbortBatch() {
    // given
    claimedBatch.wrap(
        underlyingBuffer, PARTITION_ID, PARTITION_OFFSET, FRAGMENT_LENGTH, DO_NOTHING, ADD_NOTHING);

    claimedBatch.nextFragment(MESSAGE_LENGTH, 1);
    claimedBatch.nextFragment(MESSAGE_LENGTH, 2);

    // when
    claimedBatch.abort();

    // then
    int bufferOffset = PARTITION_OFFSET;
    assertThat(underlyingBuffer.getInt(lengthOffset(bufferOffset)))
        .isEqualTo(framedLength(MESSAGE_LENGTH));
    assertThat(underlyingBuffer.getShort(typeOffset(bufferOffset))).isEqualTo(TYPE_PADDING);
    assertThat(underlyingBuffer.getByte(flagsOffset(bufferOffset))).isEqualTo((byte) 0);

    bufferOffset += alignedFramedLength(MESSAGE_LENGTH);
    assertThat(underlyingBuffer.getInt(lengthOffset(bufferOffset)))
        .isEqualTo(framedLength(MESSAGE_LENGTH));
    assertThat(underlyingBuffer.getShort(typeOffset(bufferOffset))).isEqualTo(TYPE_PADDING);
    assertThat(underlyingBuffer.getByte(flagsOffset(bufferOffset))).isEqualTo((byte) 0);
  }

  @Test
  public void shouldFillRemainingBatchLengthOnCommit() {
    // given
    claimedBatch.wrap(
        underlyingBuffer, PARTITION_ID, PARTITION_OFFSET, FRAGMENT_LENGTH, DO_NOTHING, ADD_NOTHING);

    claimedBatch.nextFragment(MESSAGE_LENGTH, 1);
    claimedBatch.nextFragment(MESSAGE_LENGTH, 2);

    // when
    claimedBatch.commit((a, b, c) -> {});

    // then
    final int bufferOffset = PARTITION_OFFSET + 2 * alignedFramedLength(MESSAGE_LENGTH);
    assertThat(underlyingBuffer.getInt(lengthOffset(bufferOffset)))
        .isEqualTo(FRAGMENT_LENGTH - 2 * alignedFramedLength(MESSAGE_LENGTH));
    assertThat(underlyingBuffer.getShort(typeOffset(bufferOffset))).isEqualTo(TYPE_PADDING);
  }

  @Test
  public void shouldFillRemainingBatchLengthOnAbort() {
    // given
    claimedBatch.wrap(
        underlyingBuffer, PARTITION_ID, PARTITION_OFFSET, FRAGMENT_LENGTH, DO_NOTHING, ADD_NOTHING);

    claimedBatch.nextFragment(MESSAGE_LENGTH, 1);
    claimedBatch.nextFragment(MESSAGE_LENGTH, 2);

    // when
    claimedBatch.abort();

    // then
    final int bufferOffset = PARTITION_OFFSET + 2 * alignedFramedLength(MESSAGE_LENGTH);
    assertThat(underlyingBuffer.getInt(lengthOffset(bufferOffset)))
        .isEqualTo(FRAGMENT_LENGTH - 2 * alignedFramedLength(MESSAGE_LENGTH));
    assertThat(underlyingBuffer.getShort(typeOffset(bufferOffset))).isEqualTo(TYPE_PADDING);
  }

  @Test
  public void shouldCommitSingleFragmentBatch() {
    // given
    claimedBatch.wrap(
        underlyingBuffer, PARTITION_ID, PARTITION_OFFSET, FRAGMENT_LENGTH, DO_NOTHING, ADD_NOTHING);

    claimedBatch.nextFragment(MESSAGE_LENGTH, 1);

    // when
    claimedBatch.commit((a, b, c) -> {});

    // then
    int bufferOffset = PARTITION_OFFSET;
    assertThat(underlyingBuffer.getInt(lengthOffset(bufferOffset)))
        .isEqualTo(framedLength(MESSAGE_LENGTH));
    assertThat(underlyingBuffer.getShort(typeOffset(bufferOffset))).isEqualTo(TYPE_MESSAGE);
    assertThat(underlyingBuffer.getByte(flagsOffset(bufferOffset))).isEqualTo((byte) 0);

    bufferOffset += alignedFramedLength(MESSAGE_LENGTH);
    assertThat(underlyingBuffer.getInt(lengthOffset(bufferOffset)))
        .isEqualTo(FRAGMENT_LENGTH - alignedFramedLength(MESSAGE_LENGTH));
    assertThat(underlyingBuffer.getShort(typeOffset(bufferOffset))).isEqualTo(TYPE_PADDING);
    assertThat(underlyingBuffer.getByte(flagsOffset(bufferOffset))).isEqualTo((byte) 0);
  }

  @Test
  public void shouldFillBatchCompletely() {
    // given
    claimedBatch.wrap(
        underlyingBuffer, PARTITION_ID, PARTITION_OFFSET, FRAGMENT_LENGTH, DO_NOTHING, ADD_NOTHING);

    claimedBatch.nextFragment(MESSAGE_LENGTH, 1);

    // when
    final int remainingCapacity = FRAGMENT_LENGTH - alignedFramedLength(MESSAGE_LENGTH);
    final int fragmentLength = remainingCapacity - HEADER_LENGTH;

    claimedBatch.nextFragment(fragmentLength, 2);
    claimedBatch.commit((a, b, c) -> {});

    // then
    final int bufferOffset = PARTITION_OFFSET + alignedFramedLength(MESSAGE_LENGTH);
    assertThat(underlyingBuffer.getInt(lengthOffset(bufferOffset)))
        .isEqualTo(framedLength(fragmentLength));
  }

  @Test
  public void shouldAddFragmentIfRemainingCapacityIsLessThanAlignment() {
    // given
    claimedBatch.wrap(
        underlyingBuffer, PARTITION_ID, PARTITION_OFFSET, FRAGMENT_LENGTH, DO_NOTHING, ADD_NOTHING);

    claimedBatch.nextFragment(MESSAGE_LENGTH, 1);

    // when
    final int remainingCapacity = FRAGMENT_LENGTH - alignedFramedLength(MESSAGE_LENGTH);
    final int fragmentLength = remainingCapacity - HEADER_LENGTH - FRAME_ALIGNMENT + 1;

    claimedBatch.nextFragment(fragmentLength, 2);
    claimedBatch.commit((a, b, c) -> {});

    // then
    final int bufferOffset = PARTITION_OFFSET + alignedFramedLength(MESSAGE_LENGTH);
    assertThat(underlyingBuffer.getInt(lengthOffset(bufferOffset)))
        .isEqualTo(framedLength(fragmentLength));
  }

  @Test
  public void shouldFailToAddFragmentIfGreaterThanRemainingCapacity() {
    // given
    claimedBatch.wrap(
        underlyingBuffer, PARTITION_ID, PARTITION_OFFSET, FRAGMENT_LENGTH, DO_NOTHING, ADD_NOTHING);

    claimedBatch.nextFragment(MESSAGE_LENGTH, 1);

    // then
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("The given fragment length is greater than the remaining capacity");

    // when
    final int remainingCapacity = FRAGMENT_LENGTH - alignedFramedLength(MESSAGE_LENGTH);

    claimedBatch.nextFragment(remainingCapacity - HEADER_LENGTH + 1, 2);
  }

  @Test
  public void shouldFailToAddFragmentIfRemainingCapacityIsLessThanPaddingMessage() {
    // given
    claimedBatch.wrap(
        underlyingBuffer, PARTITION_ID, PARTITION_OFFSET, FRAGMENT_LENGTH, DO_NOTHING, ADD_NOTHING);

    claimedBatch.nextFragment(MESSAGE_LENGTH, 1);

    // then
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("The given fragment length is greater than the remaining capacity");

    // when
    final int remainingCapacity = FRAGMENT_LENGTH - alignedFramedLength(MESSAGE_LENGTH);

    claimedBatch.nextFragment(remainingCapacity - HEADER_LENGTH - FRAME_ALIGNMENT, 2);
  }
}
