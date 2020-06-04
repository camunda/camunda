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
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.TYPE_MESSAGE;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.TYPE_PADDING;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.framedLength;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.lengthOffset;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.streamIdOffset;
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
import io.zeebe.dispatcher.ClaimedFragment;
import io.zeebe.util.TriConsumer;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

public final class LogBufferAppenderClaimTest {
  static final int A_PARTITION_LENGTH = 1024;
  static final byte[] A_MSG_PAYLOAD = "some bytes".getBytes(StandardCharsets.UTF_8);
  static final int A_MSG_PAYLOAD_LENGTH = A_MSG_PAYLOAD.length;
  static final int A_FRAGMENT_LENGTH = align(A_MSG_PAYLOAD_LENGTH + HEADER_LENGTH, FRAME_ALIGNMENT);
  static final int A_PARTITION_ID = 10;
  static final int A_STREAM_ID = 20;
  private static final Runnable DO_NOTHING = () -> {};
  private static final BiConsumer<Long, TriConsumer<ZeebeEntry, Long, Integer>> ADD_NOTHING =
      (a, b) -> {};

  UnsafeBuffer metadataBufferMock;
  UnsafeBuffer dataBufferMock;
  LogBufferAppender logBufferAppender;
  LogBufferPartition logBufferPartition;
  ClaimedFragment claimedFragmentMock;

  @Before
  public void setup() {
    dataBufferMock = mock(UnsafeBuffer.class);
    metadataBufferMock = mock(UnsafeBuffer.class);
    claimedFragmentMock = mock(ClaimedFragment.class);

    when(dataBufferMock.capacity()).thenReturn(A_PARTITION_LENGTH);
    logBufferPartition = new LogBufferPartition(dataBufferMock, metadataBufferMock, 0);
    verify(dataBufferMock).verifyAlignment();
    verify(metadataBufferMock).verifyAlignment();

    logBufferAppender = new LogBufferAppender();
  }

  @Test
  public void shouldClaimFragment() {
    // given
    // that the message + next message header fit into the buffer and there is more space
    final int currentTail = 0;

    when(metadataBufferMock.getAndAddInt(PARTITION_TAIL_COUNTER_OFFSET, A_FRAGMENT_LENGTH))
        .thenReturn(currentTail);

    // if
    final int newTail =
        logBufferAppender.claim(
            logBufferPartition,
            A_PARTITION_ID,
            claimedFragmentMock,
            A_MSG_PAYLOAD_LENGTH,
            A_STREAM_ID,
            DO_NOTHING,
            ADD_NOTHING);

    // then
    assertThat(newTail).isEqualTo(currentTail + A_FRAGMENT_LENGTH);

    // the tail is moved by the aligned message length
    verify(metadataBufferMock).getAndAddInt(PARTITION_TAIL_COUNTER_OFFSET, A_FRAGMENT_LENGTH);
    verifyNoMoreInteractions(metadataBufferMock);

    // the negative header was written and the claimed fragment now wraps the buffer section
    final InOrder inOrder = inOrder(dataBufferMock, claimedFragmentMock);
    inOrder
        .verify(dataBufferMock)
        .putIntOrdered(lengthOffset(currentTail), -framedLength(A_MSG_PAYLOAD_LENGTH));
    inOrder.verify(dataBufferMock).putShort(typeOffset(currentTail), TYPE_MESSAGE);
    inOrder.verify(dataBufferMock).putInt(streamIdOffset(currentTail), A_STREAM_ID);
    inOrder
        .verify(claimedFragmentMock)
        .wrap(
            dataBufferMock,
            currentTail,
            A_MSG_PAYLOAD_LENGTH + HEADER_LENGTH,
            DO_NOTHING,
            ADD_NOTHING);
  }

  @Test
  public void shouldClaimIfRemainingCapacityIsEqualHeaderSize() {
    // given
    // that the message + next message header EXACTLY fit into the buffer
    final int currentTail = A_PARTITION_LENGTH - HEADER_LENGTH - A_FRAGMENT_LENGTH;

    when(metadataBufferMock.getAndAddInt(PARTITION_TAIL_COUNTER_OFFSET, A_FRAGMENT_LENGTH))
        .thenReturn(currentTail);

    // if
    final int newTail =
        logBufferAppender.claim(
            logBufferPartition,
            A_PARTITION_ID,
            claimedFragmentMock,
            A_MSG_PAYLOAD_LENGTH,
            A_STREAM_ID,
            DO_NOTHING,
            ADD_NOTHING);

    // then
    assertThat(newTail).isEqualTo(currentTail + A_FRAGMENT_LENGTH);

    // the tail is moved by the aligned message length
    verify(metadataBufferMock).getAndAddInt(PARTITION_TAIL_COUNTER_OFFSET, A_FRAGMENT_LENGTH);
    verifyNoMoreInteractions(metadataBufferMock);

    // the negative header was written and the claimed fragment now wraps the buffer section
    final InOrder inOrder = inOrder(dataBufferMock, claimedFragmentMock);
    inOrder
        .verify(dataBufferMock)
        .putIntOrdered(lengthOffset(currentTail), -framedLength(A_MSG_PAYLOAD_LENGTH));
    inOrder.verify(dataBufferMock).putShort(typeOffset(currentTail), TYPE_MESSAGE);
    inOrder.verify(dataBufferMock).putInt(streamIdOffset(currentTail), A_STREAM_ID);
    inOrder
        .verify(claimedFragmentMock)
        .wrap(
            dataBufferMock,
            currentTail,
            A_MSG_PAYLOAD_LENGTH + HEADER_LENGTH,
            DO_NOTHING,
            ADD_NOTHING);
  }

  @Test
  public void shouldRejectAndFillWithPaddingIfTrippsEndOfBuffer() {
    // given
    // that the message + next message header do NOT fit into the buffer
    final int currentTail = A_PARTITION_LENGTH - HEADER_LENGTH - A_FRAGMENT_LENGTH + 1;

    when(metadataBufferMock.getAndAddInt(PARTITION_TAIL_COUNTER_OFFSET, A_FRAGMENT_LENGTH))
        .thenReturn(currentTail);

    // if        throw new RuntimeException("File " + bufferFileName + " does not exist");

    final int newTail =
        logBufferAppender.claim(
            logBufferPartition,
            A_PARTITION_ID,
            claimedFragmentMock,
            A_MSG_PAYLOAD_LENGTH,
            A_STREAM_ID,
            DO_NOTHING,
            ADD_NOTHING);

    // then
    assertThat(newTail).isEqualTo(-2);

    // the tail is moved by the aligned message length
    verify(metadataBufferMock).getAndAddInt(PARTITION_TAIL_COUNTER_OFFSET, A_FRAGMENT_LENGTH);
    verifyNoMoreInteractions(metadataBufferMock);

    // and the buffer is filled with padding
    final int padLength = A_PARTITION_LENGTH - currentTail - HEADER_LENGTH;
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
    // that the current tail is that we exactly hit the trip point (ie. only a zero-length padding
    // header fits the buffer)
    final int currentTail = A_PARTITION_LENGTH - HEADER_LENGTH;

    when(metadataBufferMock.getAndAddInt(PARTITION_TAIL_COUNTER_OFFSET, A_FRAGMENT_LENGTH))
        .thenReturn(currentTail);

    // if
    final int newTail =
        logBufferAppender.claim(
            logBufferPartition,
            A_PARTITION_ID,
            claimedFragmentMock,
            A_MSG_PAYLOAD_LENGTH,
            A_STREAM_ID,
            DO_NOTHING,
            ADD_NOTHING);

    // then
    assertThat(newTail).isEqualTo(-2);

    // the tail is moved by the aligned message length
    verify(metadataBufferMock).getAndAddInt(PARTITION_TAIL_COUNTER_OFFSET, A_FRAGMENT_LENGTH);
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
    // that the tail is beyond the trip point
    final int currentTail = A_PARTITION_LENGTH - HEADER_LENGTH + 1;

    when(metadataBufferMock.getAndAddInt(PARTITION_TAIL_COUNTER_OFFSET, A_FRAGMENT_LENGTH))
        .thenReturn(currentTail);

    // if
    final int newTail =
        logBufferAppender.claim(
            logBufferPartition,
            A_PARTITION_ID,
            claimedFragmentMock,
            A_MSG_PAYLOAD_LENGTH,
            A_STREAM_ID,
            DO_NOTHING,
            ADD_NOTHING);

    // then
    assertThat(newTail).isEqualTo(-1);

    // the tail is moved by the aligned message length
    verify(metadataBufferMock).getAndAddInt(PARTITION_TAIL_COUNTER_OFFSET, A_FRAGMENT_LENGTH);
    verifyNoMoreInteractions(metadataBufferMock);

    // and no message / padding is written
    verify(dataBufferMock, times(0)).putIntOrdered(anyInt(), anyInt());
  }
}
