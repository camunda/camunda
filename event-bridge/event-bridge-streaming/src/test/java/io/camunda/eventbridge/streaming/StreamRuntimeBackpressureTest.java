/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.eventbridge.client.Consumer;
import io.camunda.eventbridge.client.Event;
import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.client.RebalanceListener;
import io.camunda.eventbridge.client.TopicPartition;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Verifies the per-partition pause/resume back-pressure of the source stage: a partition whose
 * queue fills is paused on the consumer and its overflow parked, instead of the source loop
 * blocking — so one slow partition never head-of-line blocks the others.
 */
final class StreamRuntimeBackpressureTest {

  private static final String TOPIC = "facts";

  @Test
  void shouldPauseOnlyTheSlowPartitionAndKeepProcessingOthers() throws Exception {
    // given — a tiny per-partition queue, a batch overflowing p1 (whose task blocks on a latch),
    // and records for p2 in the same poll
    final List<String> p1Processed = Collections.synchronizedList(new ArrayList<>());
    final CountDownLatch p1Unblocked = new CountDownLatch(1);
    final Map<Integer, Long> committedOffsets = new ConcurrentHashMap<>();
    final CountDownLatch p2Committed = new CountDownLatch(1);
    final CountDownLatch p1FullyCommitted = new CountDownLatch(1);
    final CountDownLatch p1Paused = new CountDownLatch(1);
    final CountDownLatch p1Resumed = new CountDownLatch(1);

    final Consumer consumer = mock(Consumer.class);
    final EventBridgeClient client = mock(EventBridgeClient.class);
    when(client.subscribe(any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(consumer));
    when(consumer.sendHeartbeat()).thenReturn(CompletableFuture.completedFuture(null));
    when(consumer.poll(anyInt(), any()))
        .thenReturn(
            List.of(
                event(1, 1L, "a"),
                event(1, 2L, "b"),
                event(1, 3L, "c"),
                event(1, 4L, "d"),
                event(1, 5L, "e"),
                event(1, 6L, "f"),
                event(2, 1L, "x"),
                event(2, 2L, "y")))
        .thenReturn(List.of());
    when(consumer.commitOffset(any(), anyInt(), anyLong()))
        .thenAnswer(
            invocation -> {
              final int partition = invocation.getArgument(1);
              final long offset = invocation.getArgument(2);
              committedOffsets.put(partition, offset);
              if (partition == 2 && offset == 2L) {
                p2Committed.countDown();
              }
              if (partition == 1 && offset == 6L) {
                p1FullyCommitted.countDown();
              }
              return CompletableFuture.completedFuture(null);
            });
    doAnswer(
            invocation -> {
              p1Paused.countDown();
              return null;
            })
        .when(consumer)
        .pause(any());
    doAnswer(
            invocation -> {
              p1Resumed.countDown();
              return null;
            })
        .when(consumer)
        .resume(any());

    final Task<String> blockingTask =
        record -> {
          p1Processed.add(record);
          try {
            p1Unblocked.await();
          } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        };
    final List<String> p2Processed = Collections.synchronizedList(new ArrayList<>());
    final Task<String> fastTask = p2Processed::add;

    final StreamRuntime<String> runtime =
        StreamRuntime.<String>builder()
            .client(client)
            .group("g")
            .instanceId("i")
            .sourceTopic(TOPIC)
            .deserializer(
                (payload, partition, offset) -> new String(payload, StandardCharsets.UTF_8))
            .taskFactory(partition -> partition == 1 ? blockingTask : fastTask)
            .transactionRunner(Runnable::run)
            .offsetStore(noOpOffsets())
            .partitionQueueCapacity(2)
            .commitInterval(Duration.ZERO)
            .build();

    // when
    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();

    // then — p1's overflow paused it (exactly once), and p2 was fully routed, processed and
    // committed while p1's task was still blocked (no head-of-line blocking)
    assertThat(p1Paused.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(p2Committed.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(p2Processed).containsExactly("x", "y");
    verify(consumer, times(1)).pause(List.of(new TopicPartition(TOPIC, 1)));

    // and when — p1's task unblocks
    p1Unblocked.countDown();

    // then — the parked entries are flushed in offset order, the partition is resumed once its
    // backlog drains past the watermark, and the commit advances over the whole batch
    assertThat(p1FullyCommitted.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(p1Resumed.await(5, TimeUnit.SECONDS)).isTrue();
    verify(consumer, atLeastOnce()).resume(List.of(new TopicPartition(TOPIC, 1)));
    runtime.stop();
    loop.join(TimeUnit.SECONDS.toMillis(5));
    assertThat(p1Processed).containsExactly("a", "b", "c", "d", "e", "f");
    assertThat(committedOffsets).containsEntry(1, 6L).containsEntry(2, 2L);
  }

  @Test
  void shouldParkTheFilteredTailWhenItsPartitionIsPaused() throws Exception {
    // given — accepted records overflowing the queue (pausing p1) followed by a filtered-only tail
    final List<String> processed = Collections.synchronizedList(new ArrayList<>());
    final Map<Integer, Long> committedOffsets = new ConcurrentHashMap<>();
    final CountDownLatch tailCommitted = new CountDownLatch(1);

    final Consumer consumer = mock(Consumer.class);
    final EventBridgeClient client = mock(EventBridgeClient.class);
    when(client.subscribe(any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(consumer));
    when(consumer.sendHeartbeat()).thenReturn(CompletableFuture.completedFuture(null));
    when(consumer.poll(anyInt(), any()))
        .thenReturn(
            List.of(
                event(1, 1L, "keep-a"),
                event(1, 2L, "keep-b"),
                event(1, 3L, "keep-c"),
                event(1, 4L, "skip-d"),
                event(1, 5L, "skip-e")))
        .thenReturn(List.of());
    when(consumer.commitOffset(any(), anyInt(), anyLong()))
        .thenAnswer(
            invocation -> {
              committedOffsets.put(invocation.getArgument(1), invocation.getArgument(2));
              if ((long) invocation.getArgument(2) == 5L) {
                tailCommitted.countDown();
              }
              return CompletableFuture.completedFuture(null);
            });

    final StreamRuntime<String> runtime =
        StreamRuntime.<String>builder()
            .client(client)
            .group("g")
            .instanceId("i")
            .sourceTopic(TOPIC)
            .deserializer(
                (payload, partition, offset) -> new String(payload, StandardCharsets.UTF_8))
            .recordFilter(payload -> new String(payload, StandardCharsets.UTF_8).startsWith("keep"))
            .taskFactory(partition -> processed::add)
            .transactionRunner(Runnable::run)
            .offsetStore(noOpOffsets())
            .partitionQueueCapacity(2)
            .commitInterval(Duration.ZERO)
            .build();

    // when
    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();

    // then — the coalesced filtered tail was parked behind the overflow (offset order held) and
    // the commit position advanced past the filtered-only stretch after the flush
    assertThat(tailCommitted.await(5, TimeUnit.SECONDS)).isTrue();
    verify(consumer, times(1)).pause(List.of(new TopicPartition(TOPIC, 1)));
    runtime.stop();
    loop.join(TimeUnit.SECONDS.toMillis(5));
    assertThat(processed).containsExactly("keep-a", "keep-b", "keep-c");
    assertThat(committedOffsets).containsEntry(1, 5L);
  }

  @Test
  void shouldDropParkedEntriesAndResumeWhenAPausedPartitionIsRevoked() throws Exception {
    // given — p1 overflows and pauses with parked entries, while its task blocks mid-fold
    final List<String> processed = Collections.synchronizedList(new ArrayList<>());
    final CountDownLatch firstRecordFolding = new CountDownLatch(1);
    final CountDownLatch unblocked = new CountDownLatch(1);
    final CountDownLatch pausedOnConsumer = new CountDownLatch(1);
    final CountDownLatch resumedOnConsumer = new CountDownLatch(1);
    final CountDownLatch taskClosed = new CountDownLatch(1);
    final Map<Integer, Long> committedOffsets = new ConcurrentHashMap<>();
    final AtomicReference<RebalanceListener> listener = new AtomicReference<>();

    final Consumer consumer = mock(Consumer.class);
    final EventBridgeClient client = mock(EventBridgeClient.class);
    when(client.subscribe(any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(consumer));
    when(consumer.sendHeartbeat()).thenReturn(CompletableFuture.completedFuture(null));
    doAnswer(
            invocation -> {
              listener.set(invocation.getArgument(0));
              return null;
            })
        .when(consumer)
        .rebalanceListener(any());
    when(consumer.poll(anyInt(), any()))
        .thenReturn(
            List.of(
                event(1, 1L, "a"),
                event(1, 2L, "b"),
                event(1, 3L, "c"),
                event(1, 4L, "d"),
                event(1, 5L, "e")))
        .thenReturn(List.of());
    when(consumer.commitOffset(any(), anyInt(), anyLong()))
        .thenAnswer(
            invocation -> {
              committedOffsets.put(invocation.getArgument(1), invocation.getArgument(2));
              return CompletableFuture.completedFuture(null);
            });
    doAnswer(
            invocation -> {
              pausedOnConsumer.countDown();
              return null;
            })
        .when(consumer)
        .pause(any());
    doAnswer(
            invocation -> {
              resumedOnConsumer.countDown();
              return null;
            })
        .when(consumer)
        .resume(any());

    final Task<String> task =
        new Task<>() {
          @Override
          public void process(final String record) {
            processed.add(record);
            firstRecordFolding.countDown();
            try {
              unblocked.await();
            } catch (final InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          }

          @Override
          public void close() {
            taskClosed.countDown();
          }
        };

    final StreamRuntime<String> runtime =
        StreamRuntime.<String>builder()
            .client(client)
            .group("g")
            .instanceId("i")
            .sourceTopic(TOPIC)
            .deserializer(
                (payload, partition, offset) -> new String(payload, StandardCharsets.UTF_8))
            .taskFactory(partition -> task)
            .transactionRunner(Runnable::run)
            .offsetStore(noOpOffsets())
            .partitionQueueCapacity(2)
            .commitInterval(Duration.ZERO)
            .build();

    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();
    assertThat(pausedOnConsumer.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(firstRecordFolding.await(5, TimeUnit.SECONDS)).isTrue();

    // when — the paused partition is revoked; only after the loop has applied the revoke
    // (observable via the defensive consumer resume) does the fold unblock
    listener.get().onPartitionsRevoked(List.of(new TopicPartition(TOPIC, 1)));
    assertThat(resumedOnConsumer.await(5, TimeUnit.SECONDS)).isTrue();
    verify(consumer, atLeastOnce()).resume(List.of(new TopicPartition(TOPIC, 1)));
    unblocked.countDown();

    // then — the task is released and closed, whatever was still parked when the revoke was
    // applied was dropped, and the fold ends on a legal cut. The exact cut is a race, but the set
    // of legal outcomes is derivable from the pause/park mechanics:
    //
    //   * "a" and "b" were queued before the overflow paused the partition, so the actor's first
    //     drain always contains them and a drained batch always folds whole — every outcome starts
    //     with ["a", "b"].
    //   * Everything else was parked. An entry folds only if a parked-backlog flush moved it into
    //     the queue BEFORE the loop applied the revoke (the revoke drops what is still parked, and
    //     a revoked partition is never flushed again). The flush races both the revoke and the
    //     actor's element-wise drain of the 2-slot queue: depending on how many slots the drain
    //     had freed when each flush pass ran, any prefix of ["c", "d", "e"] may have been flushed
    //     pre-revoke — including none of it, and including all of it (each flushed entry the drain
    //     consumes frees a slot for the next).
    //   * A flushed entry still folds only if the actor gets a fold cycle for it before its stop
    //     lands, so any flushed suffix may also remain unfolded.
    //   * Order and integrity are not up to the race: the queue, the parked deque and the fold
    //     batch all preserve offset order, and a dropped entry is gone for good — so the outcome
    //     is always a gapless, duplicate-free prefix, never a skip, a repeat, or a resurrected
    //     parked entry.
    //
    // Legal outcomes are therefore exactly the prefixes of [a, b, c, d, e] of length >= 2, each
    // committed exactly up to its last folded entry.
    assertThat(taskClosed.await(5, TimeUnit.SECONDS)).isTrue();
    runtime.stop();
    loop.join(TimeUnit.SECONDS.toMillis(5));
    assertThat(processed)
        .isIn(
            List.of("a", "b"),
            List.of("a", "b", "c"),
            List.of("a", "b", "c", "d"),
            List.of("a", "b", "c", "d", "e"));
    assertThat(committedOffsets).containsEntry(1, (long) processed.size());
  }

  private static Event event(final int partition, final long offset, final String value) {
    return new Event(offset, TOPIC, partition, value.getBytes(StandardCharsets.UTF_8));
  }

  private static OffsetStore noOpOffsets() {
    return new OffsetStore() {
      @Override
      public Map<Integer, Long> restore() {
        return Map.of();
      }

      @Override
      public void store(final int partition, final long offset) {}
    };
  }
}
