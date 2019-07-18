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
import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.dispatcher.BlockPeek;
import io.zeebe.dispatcher.ClaimedFragment;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.dispatcher.FragmentHandler;
import io.zeebe.dispatcher.Subscription;
import io.zeebe.dispatcher.impl.log.LogBuffer;
import io.zeebe.util.ByteValue;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Rule;
import org.junit.Test;

public class DispatcherIntegrationTest {
  public static final FragmentHandler CONSUME =
      new FragmentHandler() {
        @Override
        public int onFragment(
            DirectBuffer buffer, int offset, int length, int streamId, boolean isMarkedFailed) {
          return FragmentHandler.CONSUME_FRAGMENT_RESULT;
        }
      };
  @Rule public ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule(1);

  @Test
  public void testOffer() throws Exception {
    // 1 million messages
    final int totalWork = 1_000_000;
    final UnsafeBuffer msg = new UnsafeBuffer(ByteBuffer.allocate(4534));

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

    offerMessage(dispatcher, msg, totalWork);

    consumerThread.join();

    dispatcher.close();
  }

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
  public void testPeekFragmentInPipelineMode() throws Exception {
    final int totalWork = 1000000;
    final UnsafeBuffer msg = new UnsafeBuffer(ByteBuffer.allocate(4534));

    final Consumer consumer1 = new Consumer();
    final Consumer consumer2 = new Consumer();

    final Dispatcher dispatcher =
        Dispatchers.create("default")
            .actorScheduler(actorSchedulerRule.get())
            .bufferSize(ByteValue.ofMegabytes(10))
            .modePipeline()
            .subscriptions("s1", "s2")
            .build();

    final Subscription subscription1 = dispatcher.getSubscription("s1");
    final Subscription subscription2 = dispatcher.getSubscription("s2");

    final Thread consumerThread1 =
        new Thread(
            () -> {
              while (consumer1.counter.get() < totalWork) {
                subscription1.peekAndConsume(consumer1, Integer.MAX_VALUE);
              }
            });

    final AtomicBoolean thread2OvertookThread1 = new AtomicBoolean(false);

    final Thread consumerThread2 =
        new Thread(
            () -> {
              while (consumer2.counter.get() < totalWork) {
                // in pipeline mode, the second consumer should not overtake the
                // first consumer
                if (consumer2.counter.get() > consumer1.counter.get()) {
                  thread2OvertookThread1.set(true);
                  // do not leave the loop or else the other thread won't be able to complete
                }

                subscription2.peekAndConsume(consumer2, Integer.MAX_VALUE);
              }
            });

    consumerThread1.start();
    consumerThread2.start();

    offerMessage(dispatcher, msg, totalWork);

    consumerThread1.join();
    consumerThread2.join();

    assertThat(thread2OvertookThread1).isFalse();

    dispatcher.close();
  }

  @Test
  public void testPeekBlockInPipelineMode() throws Exception {
    final int totalWork = 1000000;
    final ClaimedFragment claimedFragment = new ClaimedFragment();

    final BlockPeek blockPeek1 = new BlockPeek();
    final BlockPeek blockPeek2 = new BlockPeek();

    final Dispatcher dispatcher =
        Dispatchers.create("default")
            .actorScheduler(actorSchedulerRule.get())
            .bufferSize(ByteValue.ofMegabytes(10))
            .modePipeline()
            .subscriptions("s1", "s2")
            .build();

    final Subscription subscription1 = dispatcher.getSubscription("s1");
    final Subscription subscription2 = dispatcher.getSubscription("s2");

    final AtomicInteger counter1 = new AtomicInteger(0);
    final AtomicInteger counter2 = new AtomicInteger(0);

    final Thread consumerThread1 =
        new Thread(
            () -> {
              while (counter1.get() < totalWork) {
                while (subscription1.peekBlock(blockPeek1, alignedFramedLength(64), false) == 0) {}

                counter1.incrementAndGet();
                blockPeek1.markCompleted();
              }
            });

    final Thread consumerThread2 =
        new Thread(
            () -> {
              while (counter2.get() < totalWork) {
                // in pipeline mode, the second consumer should not overtake the first consumer
                assertThat(counter2.get()).isLessThanOrEqualTo(counter1.get());

                while (subscription2.peekBlock(blockPeek2, alignedFramedLength(64), false) == 0) {}

                counter2.incrementAndGet();
                blockPeek2.markCompleted();
              }
            });

    consumerThread1.start();
    consumerThread2.start();

    claimFragment(dispatcher, claimedFragment, totalWork);

    consumerThread1.join();
    consumerThread2.join();

    dispatcher.close();
  }

  @Test
  public void testMarkFragmentAsFailed() throws Exception {
    final int totalWork = 1000000;
    final UnsafeBuffer msg = new UnsafeBuffer(ByteBuffer.allocate(4534));

    final AtomicInteger counter1 = new AtomicInteger(0);
    final AtomicInteger counter2 = new AtomicInteger(0);

    final FragmentHandler failedConsumer =
        (buffer, offset, length, streamId, isMarkedFailed) -> {
          counter1.incrementAndGet();
          return FragmentHandler.FAILED_FRAGMENT_RESULT;
        };

    final FragmentHandler checkFailedConsumer =
        (buffer, offset, length, streamId, isMarkedFailed) -> {
          counter2.incrementAndGet();
          assertThat(isMarkedFailed).isTrue();
          return FragmentHandler.CONSUME_FRAGMENT_RESULT;
        };

    final Dispatcher dispatcher =
        Dispatchers.create("default")
            .actorScheduler(actorSchedulerRule.get())
            .bufferSize(ByteValue.ofMegabytes(10))
            .modePipeline()
            .subscriptions("s1", "s2")
            .build();

    final Subscription subscription1 = dispatcher.getSubscription("s1");
    final Subscription subscription2 = dispatcher.getSubscription("s2");

    final Thread consumerThread1 =
        new Thread(
            () -> {
              while (counter1.get() < totalWork) {
                subscription1.peekAndConsume(failedConsumer, Integer.MAX_VALUE);
              }
            });

    final Thread consumerThread2 =
        new Thread(
            () -> {
              while (counter2.get() < totalWork) {
                subscription2.peekAndConsume(checkFailedConsumer, Integer.MAX_VALUE);
              }
            });

    consumerThread1.start();
    consumerThread2.start();

    offerMessage(dispatcher, msg, totalWork);

    consumerThread1.join();
    consumerThread2.join();

    dispatcher.close();
  }

  @Test
  public void testInitialPartitionId() throws Exception {
    // 1 million messages
    final int totalWork = 1000000;
    final UnsafeBuffer msg = new UnsafeBuffer(ByteBuffer.allocate(4534));

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

    offerMessage(dispatcher, msg, totalWork);

    consumerThread.join();

    dispatcher.close();
  }

  @Test
  public void shouldSubscribeToFragmentsWithLengthZero() {
    // given
    final Dispatcher dispatcher =
        Dispatchers.create("default")
            .actorScheduler(actorSchedulerRule.get())
            .bufferSize(ByteValue.ofKilobytes(10))
            .initialPartitionId(2)
            .build();

    final Subscription subscription = dispatcher.openSubscription("foo");

    doRepeatedly(() -> dispatcher.offer(new UnsafeBuffer(0, 0))).until(p -> p >= 0);

    final LoggingFragmentHandler handler = new LoggingFragmentHandler();

    // when
    doRepeatedly(() -> subscription.poll(handler, Integer.MAX_VALUE)).until(v -> v > 0);

    // then
    assertThat(handler.handledFragmentLengths).hasSize(1);
    assertThat(handler.handledFragmentLengths).containsExactly(0);
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
  public void shouldPublishToDispatcherWithoutSubscription() {
    // given
    final Dispatcher dispatcher =
        Dispatchers.create("default")
            .actorScheduler(actorSchedulerRule.get())
            .bufferSize(ByteValue.ofKilobytes(10))
            .build();

    // when
    final long position = dispatcher.offer(BufferUtil.wrapBytes(16));

    // then
    assertThat(position).isGreaterThanOrEqualTo(0);
  }

  @Test
  public void shouldUpdatePublisherLimitOnSubscriptionRemoval() {
    // given
    final Dispatcher dispatcher =
        Dispatchers.create("default")
            .actorScheduler(actorSchedulerRule.get())
            .bufferSize(ByteValue.ofKilobytes(10))
            .build();

    final Subscription subscription1 = dispatcher.openSubscription("sub1");
    final Subscription subscription2 = dispatcher.openSubscription("sub2");

    final DirectBuffer msg = new UnsafeBuffer(new byte[32]);

    // fill dispatcher until saturated
    long claimedOffset;
    do {
      claimedOffset = dispatcher.offer(msg);
    } while (claimedOffset != -1);

    // advance subscription1 by one message
    subscription1.poll(CONSUME, 1);

    // when
    dispatcher.closeSubscription(subscription2);

    // then it is possible to publish one more fragment,
    // because the publisher limit could now be updated.
    // must try this a couple of times as publisher limit update is asynchronous
    claimedOffset = doRepeatedly(() -> dispatcher.offer(msg)).until(offset -> offset >= 0);

    assertThat(claimedOffset).isGreaterThanOrEqualTo(0);
  }

  protected void offerMessage(
      final Dispatcher dispatcher, final UnsafeBuffer msg, final int totalWork) {
    for (int i = 1; i <= totalWork; i++) {
      msg.putInt(0, i);
      while (dispatcher.offer(msg) <= 0) {
        // spin
      }
    }
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

  protected static class LoggingFragmentHandler implements FragmentHandler {
    protected List<Integer> handledFragmentLengths = new ArrayList<>();

    @Override
    public int onFragment(
        DirectBuffer buffer, int offset, int length, int streamId, boolean isMarkedFailed) {
      handledFragmentLengths.add(length);
      return FragmentHandler.CONSUME_FRAGMENT_RESULT;
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
        boolean isMarkedFailed) {
      final int newCounter = buffer.getInt(offset);
      counters.add(newCounter);
      this.counter.lazySet(newCounter);
      return FragmentHandler.CONSUME_FRAGMENT_RESULT;
    }
  }
}
