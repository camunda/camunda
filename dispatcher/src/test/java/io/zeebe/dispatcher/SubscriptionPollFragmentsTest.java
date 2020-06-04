/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.dispatcher;

import static io.zeebe.dispatcher.impl.PositionUtil.position;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.FRAME_ALIGNMENT;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.HEADER_LENGTH;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.TYPE_MESSAGE;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.TYPE_PADDING;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.alignedFramedLength;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.enableFlagFailed;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.flagsOffset;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.framedLength;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.lengthOffset;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.messageOffset;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.streamIdOffset;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.typeOffset;
import static org.agrona.BitUtil.align;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.zeebe.dispatcher.impl.log.DataFrameDescriptor;
import io.zeebe.dispatcher.impl.log.LogBuffer;
import io.zeebe.dispatcher.impl.log.LogBufferPartition;
import io.zeebe.util.sched.ActorCondition;
import java.util.Map;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

public final class SubscriptionPollFragmentsTest {
  static final int A_PARTITION_LENGTH = 1024;
  static final int A_MSG_PAYLOAD_LENGTH = 10;
  static final int A_FRAGMENT_LENGTH = align(A_MSG_PAYLOAD_LENGTH + HEADER_LENGTH, FRAME_ALIGNMENT);
  static final int A_PARTITION_ID = 10;
  static final int A_STREAM_ID = 20;

  UnsafeBuffer metadataBufferMock;
  UnsafeBuffer dataBufferMock;
  LogBufferPartition logBufferPartition;
  AtomicPosition mockSubscriberPosition;
  FragmentHandler mockFragmentHandler;

  Subscription subscription;

  @Before
  public void setup() {
    dataBufferMock = mock(UnsafeBuffer.class);
    metadataBufferMock = mock(UnsafeBuffer.class);

    when(dataBufferMock.capacity()).thenReturn(A_PARTITION_LENGTH);
    logBufferPartition = new LogBufferPartition(dataBufferMock, metadataBufferMock, 0);

    mockSubscriberPosition = mock(AtomicPosition.class);
    mockFragmentHandler = mock(FragmentHandler.class);
    final ActorCondition onConsumed = mock(ActorCondition.class);

    subscription =
        new Subscription(
            mockSubscriberPosition,
            mock(AtomicPosition.class),
            0,
            "0",
            onConsumed,
            mock(LogBuffer.class),
            Map.of());
  }

  @Test
  public void shouldReadSingleFragment() {
    final int fragOffset = 0;

    when(dataBufferMock.getIntVolatile(lengthOffset(fragOffset)))
        .thenReturn(framedLength(A_MSG_PAYLOAD_LENGTH));
    when(dataBufferMock.getShort(typeOffset(fragOffset))).thenReturn(TYPE_MESSAGE);
    when(dataBufferMock.getInt(streamIdOffset(fragOffset))).thenReturn(A_STREAM_ID);
    when(dataBufferMock.getByte(flagsOffset(fragOffset))).thenReturn((byte) 0);

    // when
    final int fragmentsRead =
        subscription.pollFragments(
            logBufferPartition,
            mockFragmentHandler,
            A_PARTITION_ID,
            fragOffset,
            1,
            position(A_PARTITION_ID, A_FRAGMENT_LENGTH),
            false);

    // then
    assertThat(fragmentsRead).isEqualTo(1);
    // the fragment handler was handed one fragment
    verify(mockFragmentHandler)
        .onFragment(
            dataBufferMock, messageOffset(fragOffset), A_MSG_PAYLOAD_LENGTH, A_STREAM_ID, false);
    verifyNoMoreInteractions(mockFragmentHandler);
    // and the position was increased by the fragment length
    verify(mockSubscriberPosition).set(position(A_PARTITION_ID, nextFragmentOffset(fragOffset)));
  }

  @Test
  public void shouldReadSingleFailedFragment() {
    final int fragOffset = 0;

    when(dataBufferMock.getIntVolatile(lengthOffset(fragOffset)))
        .thenReturn(framedLength(A_MSG_PAYLOAD_LENGTH));
    when(dataBufferMock.getShort(typeOffset(fragOffset))).thenReturn(TYPE_MESSAGE);
    when(dataBufferMock.getInt(streamIdOffset(fragOffset))).thenReturn(A_STREAM_ID);
    when(dataBufferMock.getByte(flagsOffset(fragOffset))).thenReturn(enableFlagFailed((byte) 0));

    // when
    final int fragmentsRead =
        subscription.pollFragments(
            logBufferPartition,
            mockFragmentHandler,
            A_PARTITION_ID,
            fragOffset,
            1,
            position(A_PARTITION_ID, A_FRAGMENT_LENGTH),
            false);

    // then
    assertThat(fragmentsRead).isEqualTo(1);
    // the fragment handler was handed one fragment
    verify(mockFragmentHandler)
        .onFragment(
            dataBufferMock, messageOffset(fragOffset), A_MSG_PAYLOAD_LENGTH, A_STREAM_ID, true);
    verifyNoMoreInteractions(mockFragmentHandler);
    // and the position was increased by the fragment length
    verify(mockSubscriberPosition).set(position(A_PARTITION_ID, nextFragmentOffset(fragOffset)));
  }

  @Test
  public void shouldReadMultipleFragments() {
    final int firstFragOffset = 0;
    final int secondFragOffset = nextFragmentOffset(firstFragOffset);
    final long limit = position(A_PARTITION_ID, 2 * A_FRAGMENT_LENGTH);

    when(dataBufferMock.getIntVolatile(lengthOffset(firstFragOffset)))
        .thenReturn(framedLength(A_MSG_PAYLOAD_LENGTH));
    when(dataBufferMock.getShort(typeOffset(firstFragOffset))).thenReturn(TYPE_MESSAGE);
    when(dataBufferMock.getInt(streamIdOffset(firstFragOffset))).thenReturn(A_STREAM_ID);
    when(dataBufferMock.getByte(flagsOffset(secondFragOffset))).thenReturn((byte) 0);

    when(dataBufferMock.getIntVolatile(lengthOffset(secondFragOffset)))
        .thenReturn(framedLength(A_MSG_PAYLOAD_LENGTH));
    when(dataBufferMock.getShort(typeOffset(secondFragOffset))).thenReturn(TYPE_MESSAGE);
    when(dataBufferMock.getInt(streamIdOffset(secondFragOffset))).thenReturn(A_STREAM_ID);
    when(dataBufferMock.getByte(flagsOffset(secondFragOffset)))
        .thenReturn(enableFlagFailed((byte) 0));

    // when
    final int fragmentsRead =
        subscription.pollFragments(
            logBufferPartition,
            mockFragmentHandler,
            A_PARTITION_ID,
            firstFragOffset,
            2,
            limit,
            false);

    // then
    assertThat(fragmentsRead).isEqualTo(2);

    // the fragment handler was handed two fragments
    final InOrder inOrder = inOrder(mockFragmentHandler);
    inOrder
        .verify(mockFragmentHandler)
        .onFragment(
            dataBufferMock,
            messageOffset(firstFragOffset),
            A_MSG_PAYLOAD_LENGTH,
            A_STREAM_ID,
            false);
    inOrder
        .verify(mockFragmentHandler)
        .onFragment(
            dataBufferMock,
            messageOffset(secondFragOffset),
            A_MSG_PAYLOAD_LENGTH,
            A_STREAM_ID,
            true);
    inOrder.verifyNoMoreInteractions();

    // and the position was increased by the fragment length
    final int expectedPartitionOffset = nextFragmentOffset(secondFragOffset);
    verify(mockSubscriberPosition).set(position(A_PARTITION_ID, expectedPartitionOffset));
  }

  @Test
  public void shouldNotReadBeyondLimit() {
    final int firstFragOffset = 0;
    final int secondFragOffset = nextFragmentOffset(firstFragOffset);
    final long limit = position(A_PARTITION_ID, A_FRAGMENT_LENGTH);

    when(dataBufferMock.getIntVolatile(lengthOffset(firstFragOffset)))
        .thenReturn(framedLength(A_MSG_PAYLOAD_LENGTH));
    when(dataBufferMock.getShort(typeOffset(firstFragOffset))).thenReturn(TYPE_MESSAGE);
    when(dataBufferMock.getInt(streamIdOffset(firstFragOffset))).thenReturn(A_STREAM_ID);
    when(dataBufferMock.getByte(flagsOffset(secondFragOffset))).thenReturn((byte) 0);

    when(dataBufferMock.getIntVolatile(lengthOffset(secondFragOffset)))
        .thenReturn(framedLength(A_MSG_PAYLOAD_LENGTH));
    when(dataBufferMock.getShort(typeOffset(secondFragOffset))).thenReturn(TYPE_MESSAGE);
    when(dataBufferMock.getInt(streamIdOffset(secondFragOffset))).thenReturn(A_STREAM_ID);
    when(dataBufferMock.getByte(flagsOffset(secondFragOffset)))
        .thenReturn(enableFlagFailed((byte) 0));

    // when
    final int fragmentsRead =
        subscription.pollFragments(
            logBufferPartition,
            mockFragmentHandler,
            A_PARTITION_ID,
            firstFragOffset,
            2,
            limit,
            false);

    // then
    assertThat(fragmentsRead).isEqualTo(1);

    // the fragment handler was handed one fragment
    final InOrder inOrder = inOrder(mockFragmentHandler);
    inOrder
        .verify(mockFragmentHandler)
        .onFragment(
            dataBufferMock,
            messageOffset(firstFragOffset),
            A_MSG_PAYLOAD_LENGTH,
            A_STREAM_ID,
            false);
    inOrder.verifyNoMoreInteractions();

    // and the position was increased by the fragment length
    verify(mockSubscriberPosition).set(position(A_PARTITION_ID, secondFragOffset));
  }

  @Test
  public void shouldRollOverOnPaddingAtEndOfPartition() {
    final int fragOffset = A_PARTITION_LENGTH - alignedFramedLength(A_MSG_PAYLOAD_LENGTH);

    when(dataBufferMock.getIntVolatile(lengthOffset(fragOffset)))
        .thenReturn(framedLength(A_MSG_PAYLOAD_LENGTH));
    when(dataBufferMock.getShort(typeOffset(fragOffset))).thenReturn(TYPE_PADDING);

    // when
    final int fragmentsRead =
        subscription.pollFragments(
            logBufferPartition,
            mockFragmentHandler,
            A_PARTITION_ID,
            fragOffset,
            2,
            position(A_PARTITION_ID + 1, 0),
            false);

    // then
    assertThat(fragmentsRead).isEqualTo(0);
    // the fragment handler was not handed any fragments
    verifyNoMoreInteractions(mockFragmentHandler);
    // the position was set to the beginning of the next partition
    verify(mockSubscriberPosition).set(position(A_PARTITION_ID + 1, 0));
  }

  @Test
  public void shouldNotRollOverOnPaddingIfNotEndOfPartition() {
    final int fragOffset = 0;

    when(dataBufferMock.getIntVolatile(lengthOffset(fragOffset)))
        .thenReturn(framedLength(A_MSG_PAYLOAD_LENGTH));
    when(dataBufferMock.getShort(typeOffset(fragOffset))).thenReturn(TYPE_PADDING);

    // when
    final int fragmentsRead =
        subscription.pollFragments(
            logBufferPartition,
            mockFragmentHandler,
            A_PARTITION_ID,
            fragOffset,
            2,
            position(A_PARTITION_ID, A_FRAGMENT_LENGTH),
            false);

    // then
    assertThat(fragmentsRead).isEqualTo(0);
    // the fragment handler was not handed any fragments
    verifyNoMoreInteractions(mockFragmentHandler);

    // and the position was increased by the fragment length
    final int expectedPartitionOffset = nextFragmentOffset(fragOffset);
    verify(mockSubscriberPosition).set(position(A_PARTITION_ID, expectedPartitionOffset));
  }

  @Test
  public void shouldNotReadIncompleteMessage() {
    final int fragOffset = 0;

    when(dataBufferMock.getIntVolatile(lengthOffset(fragOffset)))
        .thenReturn(-framedLength(A_MSG_PAYLOAD_LENGTH));

    // when
    final int fragmentsRead =
        subscription.pollFragments(
            logBufferPartition, mockFragmentHandler, A_PARTITION_ID, fragOffset, 1, 0, false);

    // then
    assertThat(fragmentsRead).isEqualTo(0);
    // the fragment handler was not handed any fragments
    verifyNoMoreInteractions(mockFragmentHandler);
    // and the position was set but not increased
    verify(mockSubscriberPosition).set(position(A_PARTITION_ID, fragOffset));
  }

  @Test
  public void shouldNotUpdatePositionBasedOnHandlerPostponeResult() {
    final int fragOffset = 0;

    when(dataBufferMock.getIntVolatile(lengthOffset(fragOffset)))
        .thenReturn(framedLength(A_MSG_PAYLOAD_LENGTH));
    when(dataBufferMock.getShort(typeOffset(fragOffset))).thenReturn(TYPE_MESSAGE);
    when(dataBufferMock.getInt(streamIdOffset(fragOffset))).thenReturn(A_STREAM_ID);
    when(dataBufferMock.getByte(flagsOffset(fragOffset))).thenReturn((byte) 0);
    when(mockFragmentHandler.onFragment(any(), anyInt(), anyInt(), anyInt(), anyBoolean()))
        .thenReturn(FragmentHandler.POSTPONE_FRAGMENT_RESULT);

    // when
    final int fragmentsRead =
        subscription.pollFragments(
            logBufferPartition,
            mockFragmentHandler,
            A_PARTITION_ID,
            fragOffset,
            1,
            position(A_PARTITION_ID, A_FRAGMENT_LENGTH),
            true);

    // then
    assertThat(fragmentsRead).isEqualTo(0);
    // and the position was not increased
    verify(mockSubscriberPosition).set(position(A_PARTITION_ID, fragOffset));
  }

  @Test
  public void shouldUpdatePositionBasedOnHandlerConsumeResult() {
    final int fragOffset = 0;

    when(dataBufferMock.getIntVolatile(lengthOffset(fragOffset)))
        .thenReturn(framedLength(A_MSG_PAYLOAD_LENGTH));
    when(dataBufferMock.getShort(typeOffset(fragOffset))).thenReturn(TYPE_MESSAGE);
    when(dataBufferMock.getInt(streamIdOffset(fragOffset))).thenReturn(A_STREAM_ID);
    when(dataBufferMock.getByte(flagsOffset(fragOffset))).thenReturn((byte) 0);
    when(mockFragmentHandler.onFragment(any(), anyInt(), anyInt(), anyInt(), anyBoolean()))
        .thenReturn(FragmentHandler.CONSUME_FRAGMENT_RESULT);

    // when
    final int fragmentsRead =
        subscription.pollFragments(
            logBufferPartition,
            mockFragmentHandler,
            A_PARTITION_ID,
            fragOffset,
            1,
            position(A_PARTITION_ID, A_FRAGMENT_LENGTH),
            true);

    // then
    assertThat(fragmentsRead).isEqualTo(1);
    // and the position was increased
    verify(mockSubscriberPosition).set(position(A_PARTITION_ID, nextFragmentOffset(fragOffset)));
  }

  @Test
  public void shouldUpdatePositionBasedOnHandlerFailedResult() {
    final int fragOffset = 0;

    when(dataBufferMock.getIntVolatile(lengthOffset(fragOffset)))
        .thenReturn(framedLength(A_MSG_PAYLOAD_LENGTH));
    when(dataBufferMock.getShort(typeOffset(fragOffset))).thenReturn(TYPE_MESSAGE);
    when(dataBufferMock.getInt(streamIdOffset(fragOffset))).thenReturn(A_STREAM_ID);
    when(dataBufferMock.getByte(flagsOffset(fragOffset))).thenReturn((byte) 0);
    when(mockFragmentHandler.onFragment(any(), anyInt(), anyInt(), anyInt(), anyBoolean()))
        .thenReturn(FragmentHandler.FAILED_FRAGMENT_RESULT);

    // when
    final int fragmentsRead =
        subscription.pollFragments(
            logBufferPartition,
            mockFragmentHandler,
            A_PARTITION_ID,
            fragOffset,
            1,
            position(A_PARTITION_ID, A_FRAGMENT_LENGTH),
            true);

    // then
    assertThat(fragmentsRead).isEqualTo(1);
    // and the position was increased by the fragment length
    verify(mockSubscriberPosition).set(position(A_PARTITION_ID, nextFragmentOffset(fragOffset)));
  }

  @Test
  public void shouldMarkFragmentAsFailedBasedOnHandlerFailedResult() {
    final int fragOffset = 0;
    final byte flags = (byte) 0;

    when(dataBufferMock.getIntVolatile(lengthOffset(fragOffset)))
        .thenReturn(framedLength(A_MSG_PAYLOAD_LENGTH));
    when(dataBufferMock.getShort(typeOffset(fragOffset))).thenReturn(TYPE_MESSAGE);
    when(dataBufferMock.getInt(streamIdOffset(fragOffset))).thenReturn(A_STREAM_ID);
    when(dataBufferMock.getByte(flagsOffset(fragOffset))).thenReturn(flags);
    when(mockFragmentHandler.onFragment(any(), anyInt(), anyInt(), anyInt(), anyBoolean()))
        .thenReturn(FragmentHandler.FAILED_FRAGMENT_RESULT);

    // when
    subscription.pollFragments(
        logBufferPartition,
        mockFragmentHandler,
        A_PARTITION_ID,
        fragOffset,
        1,
        position(A_PARTITION_ID, A_FRAGMENT_LENGTH),
        true);

    // then
    verify(dataBufferMock)
        .putByte(flagsOffset(fragOffset), DataFrameDescriptor.enableFlagFailed(flags));
  }

  private int nextFragmentOffset(final int currentOffset) {
    return currentOffset + A_FRAGMENT_LENGTH;
  }
}
