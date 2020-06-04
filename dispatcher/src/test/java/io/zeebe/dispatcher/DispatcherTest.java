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
import static org.agrona.BitUtil.align;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.atomix.raft.zeebe.ZeebeEntry;
import io.zeebe.dispatcher.impl.log.LogBuffer;
import io.zeebe.dispatcher.impl.log.LogBufferAppender;
import io.zeebe.dispatcher.impl.log.LogBufferPartition;
import io.zeebe.util.TriConsumer;
import io.zeebe.util.sched.ActorCondition;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public final class DispatcherTest {

  static final byte[] A_MSG_PAYLOAD = "some bytes".getBytes(StandardCharsets.UTF_8);
  static final int A_MSG_PAYLOAD_LENGTH = A_MSG_PAYLOAD.length;
  static final int A_FRAGMENT_LENGTH = align(A_MSG_PAYLOAD_LENGTH + HEADER_LENGTH, FRAME_ALIGNMENT);
  static final int AN_INITIAL_PARTITION_ID = 0;
  static final int A_LOG_WINDOW_LENGTH = 128;
  static final int A_PARTITION_SIZE = 1024;
  static final int A_STREAM_ID = 20;
  @Rule public final ExpectedException thrown = ExpectedException.none();
  Dispatcher dispatcher;
  LogBuffer logBuffer;
  LogBufferPartition logBufferPartition0;
  LogBufferPartition logBufferPartition1;
  LogBufferPartition logBufferPartition2;
  LogBufferAppender logAppender;
  AtomicPosition publisherLimit;
  AtomicPosition publisherPosition;
  Subscription subscriptionSpy;
  FragmentHandler fragmentHandler;
  ClaimedFragment claimedFragment;
  AtomicPosition subscriberPosition;
  Map<Long, TriConsumer<ZeebeEntry, Long, Integer>> handlers;

  @Before
  public void setup() {
    logBuffer = mock(LogBuffer.class);
    logBufferPartition0 = mock(LogBufferPartition.class);
    logBufferPartition1 = mock(LogBufferPartition.class);
    logBufferPartition2 = mock(LogBufferPartition.class);
    handlers = mock(Map.class);

    when(logBuffer.getInitialPartitionId()).thenReturn(AN_INITIAL_PARTITION_ID);
    when(logBuffer.getPartitionCount()).thenReturn(3);
    when(logBuffer.getPartitionSize()).thenReturn(A_PARTITION_SIZE);
    when(logBuffer.getPartition(0)).thenReturn(logBufferPartition0);
    when(logBuffer.getPartition(1)).thenReturn(logBufferPartition1);
    when(logBuffer.getPartition(2)).thenReturn(logBufferPartition2);
    when(logBuffer.createRawBufferView()).thenReturn(ByteBuffer.allocate(32));

    logAppender = mock(LogBufferAppender.class);
    publisherLimit = mock(AtomicPosition.class);
    publisherPosition = mock(AtomicPosition.class);
    fragmentHandler = mock(FragmentHandler.class);
    claimedFragment = mock(ClaimedFragment.class);
    subscriberPosition = mock(AtomicPosition.class);

    dispatcher =
        new Dispatcher(
            logBuffer,
            logAppender,
            publisherLimit,
            publisherPosition,
            A_LOG_WINDOW_LENGTH,
            A_LOG_WINDOW_LENGTH,
            new String[0],
            "test") {
          @Override
          protected Subscription newSubscription(
              final int subscriberId,
              final String subscriberName,
              final ActorCondition onConsumption) {
            subscriptionSpy =
                spy(
                    new Subscription(
                        subscriberPosition,
                        determineLimit(),
                        subscriberId,
                        subscriberName,
                        onConsumption,
                        logBuffer,
                        handlers));
            return subscriptionSpy;
          }
        };
  }

  @Test
  public void shouldNotClaimBeyondPublisherLimit() {
    // given
    // position of 0,0
    when(logBuffer.getActivePartitionIdVolatile()).thenReturn(0);
    when(logBufferPartition0.getTailCounterVolatile()).thenReturn(0);
    // publisher limit of 0
    when(publisherLimit.get()).thenReturn(position(0, 0));

    // if
    final long newPosition = dispatcher.claim(claimedFragment, A_MSG_PAYLOAD_LENGTH);

    // then
    assertThat(newPosition).isEqualTo(-1);

    verify(publisherLimit).get();
    verifyNoMoreInteractions(publisherLimit);
    verifyNoMoreInteractions(logAppender);
    verifyNoMoreInteractions(claimedFragment);
    verify(logBuffer).getActivePartitionIdVolatile();
    verify(logBuffer).getPartition(0);
    verify(logBufferPartition0).getTailCounterVolatile();
  }

  @Test
  public void shouldClaimFragment() {
    // given
    // position is 0,0
    when(logBuffer.getActivePartitionIdVolatile()).thenReturn(0);
    when(logBufferPartition0.getTailCounterVolatile()).thenReturn(0);
    when(publisherLimit.get()).thenReturn(position(0, A_FRAGMENT_LENGTH));

    when(logAppender.claim(
            eq(logBufferPartition0),
            eq(0),
            eq(claimedFragment),
            eq(A_MSG_PAYLOAD_LENGTH),
            eq(A_STREAM_ID),
            any(),
            any()))
        .thenReturn(A_FRAGMENT_LENGTH);

    // if
    final long newPosition = dispatcher.claim(claimedFragment, A_MSG_PAYLOAD_LENGTH, A_STREAM_ID);

    // then
    assertThat(newPosition).isEqualTo(position(0, A_FRAGMENT_LENGTH));

    verify(logAppender)
        .claim(
            eq(logBufferPartition0),
            eq(0),
            eq(claimedFragment),
            eq(A_MSG_PAYLOAD_LENGTH),
            eq(A_STREAM_ID),
            any(),
            any());

    verify(publisherLimit).get();
    verify(publisherPosition).proposeMaxOrdered(position(0, A_FRAGMENT_LENGTH));

    verify(logBuffer).getActivePartitionIdVolatile();
    verify(logBuffer).getPartition(0);
    verify(logBufferPartition0).getTailCounterVolatile();
  }

  @Test
  public void shouldReadFragmentsFromPartition() {
    // given
    dispatcher.doOpenSubscription("test", mock(ActorCondition.class));
    when(subscriberPosition.get()).thenReturn(0L);
    when(publisherPosition.get()).thenReturn(position(0, A_FRAGMENT_LENGTH));

    doReturn(1)
        .when(subscriptionSpy)
        .pollFragments(
            logBufferPartition0, fragmentHandler, 0, 0, 2, position(0, A_FRAGMENT_LENGTH), false);

    // if
    final int fragmentsRead = subscriptionSpy.poll(fragmentHandler, 2);

    // then
    assertThat(fragmentsRead).isEqualTo(1);
    verify(subscriberPosition).get();
    verify(subscriptionSpy)
        .pollFragments(
            logBufferPartition0, fragmentHandler, 0, 0, 2, position(0, A_FRAGMENT_LENGTH), false);
  }

  @Test
  public void shouldNotReadBeyondPublisherPosition() {
    // given
    dispatcher.doOpenSubscription("test", mock(ActorCondition.class));
    when(subscriptionSpy.getPosition()).thenReturn(0L);
    when(publisherPosition.get()).thenReturn(0L);

    // if
    final int fragmentsRead = subscriptionSpy.poll(fragmentHandler, 1);

    // then
    assertThat(fragmentsRead).isEqualTo(0);
  }

  @Test
  public void shouldUpdatePublisherLimit() {
    when(subscriberPosition.get()).thenReturn(position(10, 100));

    dispatcher.doOpenSubscription("test", mock(ActorCondition.class));
    dispatcher.updatePublisherLimit();

    verify(publisherLimit).proposeMaxOrdered(position(10, 100 + A_LOG_WINDOW_LENGTH));
  }

  @Test
  public void shouldUpdatePublisherLimitToNextPartition() {
    when(subscriberPosition.get()).thenReturn(position(10, A_PARTITION_SIZE - A_LOG_WINDOW_LENGTH));

    dispatcher.doOpenSubscription("test", mock(ActorCondition.class));
    dispatcher.updatePublisherLimit();

    verify(publisherLimit).proposeMaxOrdered(position(11, A_LOG_WINDOW_LENGTH));
  }

  @Test
  public void shouldReadFragmentsFromPartitionOnPeekAndConsume() {
    // given
    dispatcher.doOpenSubscription("test", mock(ActorCondition.class));
    when(subscriberPosition.get()).thenReturn(0L);
    when(publisherPosition.get()).thenReturn(position(0, A_FRAGMENT_LENGTH));

    doReturn(1)
        .when(subscriptionSpy)
        .pollFragments(
            logBufferPartition0, fragmentHandler, 0, 0, 2, position(0, A_FRAGMENT_LENGTH), true);

    // if
    final int fragmentsRead = subscriptionSpy.peekAndConsume(fragmentHandler, 2);

    // then
    assertThat(fragmentsRead).isEqualTo(1);
    verify(subscriberPosition).get();
    verify(subscriptionSpy)
        .pollFragments(
            logBufferPartition0, fragmentHandler, 0, 0, 2, position(0, A_FRAGMENT_LENGTH), true);
  }

  @Test
  public void shouldNotOpenSubscriptionWithSameName() {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("subscription with name 's1' already exists");

    dispatcher.doOpenSubscription("s1", mock(ActorCondition.class));
    dispatcher.doOpenSubscription("s1", mock(ActorCondition.class));
  }
}
