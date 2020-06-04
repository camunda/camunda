/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.dispatcher.impl.log;

import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.FRAME_ALIGNMENT;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.HEADER_LENGTH;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.TYPE_PADDING;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.framedLength;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.lengthOffset;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.typeOffset;
import static io.zeebe.dispatcher.impl.log.LogBufferDescriptor.PARTITION_TAIL_COUNTER_OFFSET;
import static org.agrona.BitUtil.align;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.atomix.raft.zeebe.ZeebeEntry;
import io.zeebe.dispatcher.ClaimedFragmentBatch;
import io.zeebe.util.TriConsumer;
import java.util.function.BiConsumer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

public final class LogBufferAppenderClaimBatchTest {
  private static final Runnable DO_NOTHING = () -> {};
  private static final BiConsumer<Long, TriConsumer<ZeebeEntry, Long, Integer>> ADD_NOTHING =
      (a, b) -> {};

  private static final int PARTITION_ID = 10;
  private static final int PARTITION_LENGTH = 1024;

  private static final int BATCH_FRAGMENT_COUNT = 3;
  private static final int BATCH_MESSAGE_LENGTH = 16;

  private static final int SINGLE_BATCH_FRAGMENT_LENGTH = batchFragmentLength(1);
  private static final int BATCH_FRAGMENT_LENGTH = batchFragmentLength(BATCH_FRAGMENT_COUNT);

  private UnsafeBuffer metadataBufferMock;
  private UnsafeBuffer dataBufferMock;
  private LogBufferAppender logBufferAppender;
  private LogBufferPartition logBufferPartition;
  private ClaimedFragmentBatch claimedBatchMock;

  @Before
  public void setup() {
    dataBufferMock = mock(UnsafeBuffer.class);
    metadataBufferMock = mock(UnsafeBuffer.class);
    claimedBatchMock = mock(ClaimedFragmentBatch.class);

    when(dataBufferMock.capacity()).thenReturn(PARTITION_LENGTH);
    logBufferPartition = new LogBufferPartition(dataBufferMock, metadataBufferMock, 0);
    verify(dataBufferMock).verifyAlignment();
    verify(metadataBufferMock).verifyAlignment();

    logBufferAppender = new LogBufferAppender();
  }

  private static int batchLength(final int fragmentCount) {
    return BATCH_MESSAGE_LENGTH
        + fragmentCount * (HEADER_LENGTH + FRAME_ALIGNMENT)
        + FRAME_ALIGNMENT;
  }

  private static int batchFragmentLength(final int fragmentCount) {
    return align(batchLength(fragmentCount), FRAME_ALIGNMENT);
  }

  @Test
  public void shouldClaimSingleFragmentBatch() {
    // given
    final int currentTail = 0;

    when(metadataBufferMock.getAndAddInt(
            PARTITION_TAIL_COUNTER_OFFSET, SINGLE_BATCH_FRAGMENT_LENGTH))
        .thenReturn(currentTail);

    // when
    final int newTail =
        logBufferAppender.claim(
            logBufferPartition,
            PARTITION_ID,
            claimedBatchMock,
            1,
            BATCH_MESSAGE_LENGTH,
            DO_NOTHING,
            ADD_NOTHING);

    // then
    assertThat(newTail).isEqualTo(currentTail + SINGLE_BATCH_FRAGMENT_LENGTH);

    verify(claimedBatchMock)
        .wrap(
            dataBufferMock,
            PARTITION_ID,
            currentTail,
            SINGLE_BATCH_FRAGMENT_LENGTH,
            DO_NOTHING,
            ADD_NOTHING);

    verify(metadataBufferMock)
        .getAndAddInt(PARTITION_TAIL_COUNTER_OFFSET, SINGLE_BATCH_FRAGMENT_LENGTH);
    verifyNoMoreInteractions(metadataBufferMock);
  }

  @Test
  public void shouldClaimFragmentBatch() {
    // given
    final int currentTail = 0;

    when(metadataBufferMock.getAndAddInt(PARTITION_TAIL_COUNTER_OFFSET, BATCH_FRAGMENT_LENGTH))
        .thenReturn(currentTail);

    // when
    final int newTail =
        logBufferAppender.claim(
            logBufferPartition,
            PARTITION_ID,
            claimedBatchMock,
            BATCH_FRAGMENT_COUNT,
            BATCH_MESSAGE_LENGTH,
            DO_NOTHING,
            ADD_NOTHING);

    // then
    assertThat(newTail).isEqualTo(currentTail + BATCH_FRAGMENT_LENGTH);

    verify(claimedBatchMock)
        .wrap(
            dataBufferMock,
            PARTITION_ID,
            currentTail,
            BATCH_FRAGMENT_LENGTH,
            DO_NOTHING,
            ADD_NOTHING);

    verify(metadataBufferMock).getAndAddInt(PARTITION_TAIL_COUNTER_OFFSET, BATCH_FRAGMENT_LENGTH);
    verifyNoMoreInteractions(metadataBufferMock);
  }

  @Test
  public void shouldClaimIfRemainingCapacityIsEqualHeaderSize() {
    // given
    final int currentTail = PARTITION_LENGTH - HEADER_LENGTH - BATCH_FRAGMENT_LENGTH;

    when(metadataBufferMock.getAndAddInt(PARTITION_TAIL_COUNTER_OFFSET, BATCH_FRAGMENT_LENGTH))
        .thenReturn(currentTail);

    // when
    final int newTail =
        logBufferAppender.claim(
            logBufferPartition,
            PARTITION_ID,
            claimedBatchMock,
            BATCH_FRAGMENT_COUNT,
            BATCH_MESSAGE_LENGTH,
            DO_NOTHING,
            ADD_NOTHING);

    // then
    assertThat(newTail).isEqualTo(currentTail + BATCH_FRAGMENT_LENGTH);

    verify(claimedBatchMock)
        .wrap(
            dataBufferMock,
            PARTITION_ID,
            currentTail,
            BATCH_FRAGMENT_LENGTH,
            DO_NOTHING,
            ADD_NOTHING);

    verify(metadataBufferMock).getAndAddInt(PARTITION_TAIL_COUNTER_OFFSET, BATCH_FRAGMENT_LENGTH);
    verifyNoMoreInteractions(metadataBufferMock);
  }

  @Test
  public void shouldRejectAndFillWithPaddingIfTrippsEndOfBuffer() {
    // given
    final int currentTail = PARTITION_LENGTH - HEADER_LENGTH - BATCH_FRAGMENT_LENGTH + 1;

    // when
    when(metadataBufferMock.getAndAddInt(PARTITION_TAIL_COUNTER_OFFSET, BATCH_FRAGMENT_LENGTH))
        .thenReturn(currentTail);

    final int newTail =
        logBufferAppender.claim(
            logBufferPartition,
            PARTITION_ID,
            claimedBatchMock,
            BATCH_FRAGMENT_COUNT,
            BATCH_MESSAGE_LENGTH,
            DO_NOTHING,
            ADD_NOTHING);

    // then
    assertThat(newTail).isEqualTo(-2);

    // the tail is moved by the aligned message length
    verify(metadataBufferMock).getAndAddInt(PARTITION_TAIL_COUNTER_OFFSET, BATCH_FRAGMENT_LENGTH);
    verifyNoMoreInteractions(metadataBufferMock);

    // and the buffer is filled with padding
    final int padLength = PARTITION_LENGTH - currentTail - HEADER_LENGTH;
    final InOrder inOrder = inOrder(dataBufferMock);
    inOrder
        .verify(dataBufferMock)
        .putIntOrdered(lengthOffset(currentTail), -framedLength(padLength));
    inOrder.verify(dataBufferMock).putShort(typeOffset(currentTail), TYPE_PADDING);
    inOrder
        .verify(dataBufferMock)
        .putIntOrdered(lengthOffset(currentTail), framedLength(padLength));
  }

  @Test
  public void shouldRejectAndFillWithZeroLengthPaddingIfExactlyHitsTrippPoint() {
    // given
    final int currentTail = PARTITION_LENGTH - HEADER_LENGTH;

    when(metadataBufferMock.getAndAddInt(PARTITION_TAIL_COUNTER_OFFSET, BATCH_FRAGMENT_LENGTH))
        .thenReturn(currentTail);

    // when
    final int newTail =
        logBufferAppender.claim(
            logBufferPartition,
            PARTITION_ID,
            claimedBatchMock,
            BATCH_FRAGMENT_COUNT,
            BATCH_MESSAGE_LENGTH,
            DO_NOTHING,
            ADD_NOTHING);

    // then
    assertThat(newTail).isEqualTo(-2);

    // the tail is moved by the aligned message length
    verify(metadataBufferMock).getAndAddInt(PARTITION_TAIL_COUNTER_OFFSET, BATCH_FRAGMENT_LENGTH);
    verifyNoMoreInteractions(metadataBufferMock);

    // and the buffer is filled with padding
    final int padLength = 0;
    final InOrder inOrder = inOrder(dataBufferMock);
    inOrder
        .verify(dataBufferMock)
        .putIntOrdered(lengthOffset(currentTail), -framedLength(padLength));
    inOrder.verify(dataBufferMock).putShort(typeOffset(currentTail), TYPE_PADDING);
    inOrder
        .verify(dataBufferMock)
        .putIntOrdered(lengthOffset(currentTail), framedLength(padLength));
  }

  @Test
  public void shouldRejectIfTailIsBeyondTripPoint() {
    // given
    final int currentTail = PARTITION_LENGTH - HEADER_LENGTH + 1;

    when(metadataBufferMock.getAndAddInt(PARTITION_TAIL_COUNTER_OFFSET, BATCH_FRAGMENT_LENGTH))
        .thenReturn(currentTail);

    // when
    final int newTail =
        logBufferAppender.claim(
            logBufferPartition,
            PARTITION_ID,
            claimedBatchMock,
            BATCH_FRAGMENT_COUNT,
            BATCH_MESSAGE_LENGTH,
            DO_NOTHING,
            ADD_NOTHING);

    // then
    assertThat(newTail).isEqualTo(-1);

    // the tail is moved by the aligned message length
    verify(metadataBufferMock).getAndAddInt(PARTITION_TAIL_COUNTER_OFFSET, BATCH_FRAGMENT_LENGTH);
    verifyNoMoreInteractions(metadataBufferMock);

    // and no message / padding is written
    verify(dataBufferMock, times(0)).putIntOrdered(anyInt(), anyInt());
  }
}
