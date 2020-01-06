/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.dispatcher.integration;

import static io.zeebe.dispatcher.impl.PositionUtil.position;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.alignedFramedLength;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.messageOffset;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.dispatcher.BlockPeek;
import io.zeebe.dispatcher.ClaimedFragment;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.dispatcher.FragmentHandler;
import io.zeebe.dispatcher.Subscription;
import io.zeebe.dispatcher.impl.log.LogBuffer;
import io.zeebe.util.ByteValue;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.junit.Rule;
import org.junit.Test;

public final class DispatcherIntegrationTest {
  public static final FragmentHandler CONSUME =
      (buffer, offset, length, streamId, isMarkedFailed) -> FragmentHandler.CONSUME_FRAGMENT_RESULT;
  @Rule public final ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule(1);

  @Test
  public void testClaim() throws Exception {
    final int totalWork = 1_000;
    final ClaimedFragment claimedFragment = new ClaimedFragment();

    final Dispatcher dispatcher =
        Dispatchers.create("default")
            .actorScheduler(actorSchedulerRule.get())
            .bufferSize(ByteValue.ofMegabytes(10))
            .build();

    final Consumer consumer = new Consumer();

    final Subscription subscription = dispatcher.openSubscription("test");

    final Thread consumerThread =
        new Thread(
            () -> {
              while (consumer.counter.get() < totalWork) {
                subscription.poll(consumer, Integer.MAX_VALUE);
              }
            });

    consumerThread.start();

    claimFragment(dispatcher, claimedFragment, totalWork);

    consumerThread.join();

    dispatcher.close();
  }

  @Test
  public void testClaimOnDifferentThreads() throws Exception {
    final int totalWork = 2;

    final Dispatcher dispatcher =
        Dispatchers.create("default")
            .actorScheduler(actorSchedulerRule.get())
            .bufferSize(ByteValue.ofMegabytes(10))
            .build();

    final Consumer consumer = new Consumer();

    final Subscription subscription = dispatcher.openSubscription("test");

    final Thread consumerThread =
        new Thread(
            () -> {
              while (consumer.counters.size() < totalWork) {
                if (subscription.hasAvailable()) {
                  subscription.poll(consumer, Integer.MAX_VALUE);
                }
              }
            });

    consumerThread.start();

    claimFragmentOnDifferentThreads(dispatcher, totalWork);

    consumerThread.join();

    dispatcher.close();
    assertThat(consumer.counters.size()).isEqualTo(totalWork);
    assertThat(consumer.counters).contains(1, 2);
  }

  @Test
  public void testPeekBlock() throws Exception {
    final int totalWork = 10000000;
    final ClaimedFragment claimedFragment = new ClaimedFragment();
    final BlockPeek blockPeek = new BlockPeek();

    final Dispatcher dispatcher =
        Dispatchers.create("default")
            .actorScheduler(actorSchedulerRule.get())
            .bufferSize(ByteValue.ofMegabytes(10))
            .build();

    final Subscription subscription = dispatcher.openSubscription("test");

    final Thread consumerThread =
        new Thread(
            new Runnable() {
              int counter = 0;

              @Override
              public void run() {
                while (counter < totalWork) {
                  while (subscription.peekBlock(blockPeek, alignedFramedLength(64), false) == 0) {}

                  final int newCounter =
                      Integer.reverseBytes(
                          blockPeek
                              .getRawBuffer()
                              .getInt(messageOffset(blockPeek.getBufferOffset())));
                  if (newCounter - 1 != counter) {
                    throw new RuntimeException(newCounter + " " + counter);
                  }
                  counter = newCounter;
                  blockPeek.markCompleted();
                }
              }
            });

    consumerThread.start();

    claimFragment(dispatcher, claimedFragment, totalWork);

    consumerThread.join();

    dispatcher.close();
  }

  @Test
  public void testInitialPartitionId() throws Exception {
    // 1 million messages
    final int totalWork = 1000000;

    final Dispatcher dispatcher =
        Dispatchers.create("default")
            .actorScheduler(actorSchedulerRule.get())
            .bufferSize(ByteValue.ofMegabytes(10))
            .initialPartitionId(2)
            .build();

    final LogBuffer logBuffer = dispatcher.getLogBuffer();
    final Subscription subscription = dispatcher.openSubscription("test");
    final Consumer consumer = new Consumer();

    assertThat(logBuffer.getInitialPartitionId()).isEqualTo(2);
    assertThat(logBuffer.getActivePartitionIdVolatile()).isEqualTo(2);

    assertThat(dispatcher.getPublisherPosition()).isEqualTo(position(2, 0));
    assertThat(subscription.getPosition()).isEqualTo(position(2, 0));

    final Thread consumerThread =
        new Thread(
            () -> {
              while (consumer.counter.get() < totalWork) {
                subscription.poll(consumer, Integer.MAX_VALUE);
              }
            });

    consumerThread.start();

    claimFragment(dispatcher, new ClaimedFragment(), totalWork);

    consumerThread.join();

    dispatcher.close();
  }

  @Test
  public void shouldCloseDispatcher() {
    // given
    final Dispatcher dispatcher =
        Dispatchers.create("default")
            .actorScheduler(actorSchedulerRule.get())
            .bufferSize(ByteValue.ofKilobytes(10))
            .build();

    // when
    dispatcher.close();

    // then
    assertThat(dispatcher.isClosed()).isTrue();
    assertThat(dispatcher.getLogBuffer().isClosed()).isTrue();
  }

  @Test
  public void shouldFailToCreateDispatcherIfBufferTooSmall() {
    final ByteValue frameLength = ByteValue.ofMegabytes(1);
    final int requiredBufferSize = (int) frameLength.toBytes() * 2 * 3;

    final var builder =
        Dispatchers.create("test")
            .actorScheduler(actorSchedulerRule.get())
            .maxFragmentLength(frameLength)
            .bufferSize(frameLength);

    assertThatThrownBy(() -> builder.build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Expected the buffer size to be greater than %s, but was %s. The max fragment length is set to %s.",
            requiredBufferSize, frameLength.toBytes(), frameLength.toBytes());
  }

  @Test
  public void shouldSetBufferSizeDependingOnMaxFrameLength() {
    final ByteValue frameLength = ByteValue.ofMegabytes(4);
    final int expectedPartitionSize = (int) frameLength.toBytes() * 2;

    final Dispatcher dispatcher =
        Dispatchers.create("test")
            .actorScheduler(actorSchedulerRule.get())
            .maxFragmentLength(frameLength)
            .build();

    assertThat(dispatcher.getMaxFragmentLength()).isEqualTo(frameLength.toBytes());
    assertThat(dispatcher.getLogBuffer().getPartitionSize()).isEqualTo(expectedPartitionSize);
  }

  protected void claimFragment(
      final Dispatcher dispatcher, final ClaimedFragment claimedFragment, final int totalWork) {
    for (int i = 1; i <= totalWork; i++) {
      while (dispatcher.claim(claimedFragment, 59) <= 0) {
        // spin
      }
      final MutableDirectBuffer buffer = claimedFragment.getBuffer();
      buffer.putInt(claimedFragment.getOffset(), i);
      claimedFragment.commit();
    }
  }

  protected void claimFragmentOnDifferentThreads(final Dispatcher dispatcher, final int totalWork) {
    for (int i = 1; i <= totalWork; i++) {
      final int runCount = i;
      new Thread() {
        @Override
        public void run() {
          final ClaimedFragment claimedFragment = new ClaimedFragment();
          while (dispatcher.claim(claimedFragment, 59) <= 0) {
            // spin
          }
          final MutableDirectBuffer buffer = claimedFragment.getBuffer();
          buffer.putInt(claimedFragment.getOffset(), runCount);
          claimedFragment.commit();
        }
      }.start();
    }
  }

  class Consumer implements FragmentHandler {
    final ArrayList<Integer> counters = new ArrayList<>();
    final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public int onFragment(
        final DirectBuffer buffer,
        final int offset,
        final int length,
        final int streamId,
        final boolean isMarkedFailed) {
      final int newCounter = buffer.getInt(offset);
      counters.add(newCounter);
      counter.lazySet(newCounter);
      return FragmentHandler.CONSUME_FRAGMENT_RESULT;
    }
  }
}
