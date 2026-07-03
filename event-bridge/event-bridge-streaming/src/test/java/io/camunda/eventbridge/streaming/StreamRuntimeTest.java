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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.eventbridge.client.Consumer;
import io.camunda.eventbridge.client.Event;
import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.client.TopicPartition;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class StreamRuntimeTest {

  private static final String TOPIC = "facts";

  @Test
  void shouldProduceBeforeCommitAndPersistStateWithOffsetAtomically() throws Exception {
    // given — a runtime whose collaborators record the exact order of the commit barrier
    final List<String> order = new ArrayList<>();
    final Map<Integer, Long> committedOffsets = new HashMap<>();
    final CountDownLatch offsetCommitted = new CountDownLatch(1);

    final Consumer consumer = mock(Consumer.class);
    final EventBridgeClient client = mock(EventBridgeClient.class);
    when(client.subscribe(any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(consumer));
    when(consumer.sendHeartbeat()).thenReturn(CompletableFuture.completedFuture(null));
    // one batch, then empty forever
    final Event event = new Event(5L, TOPIC, 1, "a".getBytes(StandardCharsets.UTF_8));
    when(consumer.poll(anyInt(), any())).thenReturn(List.of(event)).thenReturn(List.of());
    when(consumer.commitOffset(any(), anyInt(), anyLong()))
        .thenAnswer(
            invocation -> {
              order.add("commitOffset");
              offsetCommitted.countDown();
              return CompletableFuture.completedFuture(null);
            });

    final List<String> processed = new ArrayList<>();
    final Task<String> task =
        new Task<>() {
          @Override
          public void process(final String record) {
            processed.add(record);
          }

          @Override
          public void flush() {
            order.add("task.flush");
          }

          @Override
          public void checkpoint() {
            order.add("task.checkpoint");
          }
        };

    final OffsetStore offsets =
        new OffsetStore() {
          @Override
          public Map<Integer, Long> restore() {
            return Map.of();
          }

          @Override
          public void store(final int partition, final long offset) {
            order.add("offset.store");
            committedOffsets.put(partition, offset);
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
            .transactionRunner(
                operations -> {
                  order.add("txn.begin");
                  operations.run();
                  order.add("txn.end");
                })
            .offsetStore(offsets)
            .preCommitFlush(() -> order.add("preCommitFlush"))
            .commitInterval(Duration.ZERO)
            .build();

    // when — run the loop until the first offset commit, then stop
    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();
    assertThat(offsetCommitted.await(5, TimeUnit.SECONDS)).isTrue();
    runtime.stop();
    loop.join(TimeUnit.SECONDS.toMillis(5));

    // then — the record was processed, and the barrier ran produce-before-commit with the offset
    // and
    // the checkpoint inside one transaction, before the source offset was committed.
    assertThat(processed).containsExactly("a");
    assertThat(committedOffsets).containsEntry(1, 5L);
    assertThat(order)
        .containsSubsequence(
            "task.flush",
            "preCommitFlush",
            "txn.begin",
            "offset.store",
            "task.checkpoint",
            "txn.end",
            "commitOffset");
  }

  @Test
  void shouldSeekToRestoredOffsetPlusOne() throws Exception {
    // given — a durable offset of 10 for partition 1
    final Consumer consumer = mock(Consumer.class);
    final EventBridgeClient client = mock(EventBridgeClient.class);
    when(client.subscribe(any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(consumer));
    when(consumer.sendHeartbeat()).thenReturn(CompletableFuture.completedFuture(null));
    lenient().when(consumer.poll(anyInt(), any())).thenReturn(List.of());

    final CountDownLatch sought = new CountDownLatch(1);
    final Map<TopicPartition, Long> seekArg = new HashMap<>();
    doAnswerSeek(consumer, seekArg, sought);

    final StreamRuntime<String> runtime =
        StreamRuntime.<String>builder()
            .client(client)
            .group("g")
            .instanceId("i")
            .sourceTopic(TOPIC)
            .deserializer(
                (payload, partition, offset) -> new String(payload, StandardCharsets.UTF_8))
            .taskFactory(partition -> mock(Task.class))
            .transactionRunner(Runnable::run)
            .offsetStore(
                new OffsetStore() {
                  @Override
                  public Map<Integer, Long> restore() {
                    return Map.of(1, 10L);
                  }

                  @Override
                  public void store(final int partition, final long offset) {}
                })
            .build();

    // when
    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();

    // then — the consumer is sought to lastProcessed + 1
    assertThat(sought.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(seekArg).containsEntry(new TopicPartition(TOPIC, 1), 11L);
    runtime.stop();
    loop.join(TimeUnit.SECONDS.toMillis(5));
  }

  @Test
  void shouldSkipPoisonRecordAndCommitPastItWhenHandlerSaysSkip() throws Exception {
    // given — a batch with a poison record (offset 5) before a good one (offset 6)
    final List<String> processed = new ArrayList<>();
    final Map<Integer, Long> committedOffsets = new HashMap<>();
    final CountDownLatch offsetCommitted = new CountDownLatch(1);

    final Consumer consumer = mock(Consumer.class);
    final EventBridgeClient client = mock(EventBridgeClient.class);
    when(client.subscribe(any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(consumer));
    when(consumer.sendHeartbeat()).thenReturn(CompletableFuture.completedFuture(null));
    final Event poison = new Event(5L, TOPIC, 1, "bad".getBytes(StandardCharsets.UTF_8));
    final Event good = new Event(6L, TOPIC, 1, "good".getBytes(StandardCharsets.UTF_8));
    when(consumer.poll(anyInt(), any())).thenReturn(List.of(poison, good)).thenReturn(List.of());
    when(consumer.commitOffset(any(), anyInt(), anyLong()))
        .thenAnswer(
            invocation -> {
              committedOffsets.put(invocation.getArgument(1), invocation.getArgument(2));
              offsetCommitted.countDown();
              return CompletableFuture.completedFuture(null);
            });

    final StreamRuntime<String> runtime =
        StreamRuntime.<String>builder()
            .client(client)
            .group("g")
            .instanceId("i")
            .sourceTopic(TOPIC)
            .deserializer(
                (payload, partition, offset) -> {
                  final String value = new String(payload, StandardCharsets.UTF_8);
                  if ("bad".equals(value)) {
                    throw new IllegalStateException("poison");
                  }
                  return value;
                })
            .taskFactory(
                partition ->
                    new Task<>() {
                      @Override
                      public void process(final String record) {
                        processed.add(record);
                      }
                    })
            .transactionRunner(Runnable::run)
            .offsetStore(
                new OffsetStore() {
                  @Override
                  public Map<Integer, Long> restore() {
                    return Map.of();
                  }

                  @Override
                  public void store(final int partition, final long offset) {}
                })
            .recordExceptionHandler(
                (partition, offset, error) -> RecordExceptionHandler.Decision.SKIP)
            .commitInterval(Duration.ZERO)
            .build();

    // when
    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();
    assertThat(offsetCommitted.await(5, TimeUnit.SECONDS)).isTrue();
    runtime.stop();
    loop.join(TimeUnit.SECONDS.toMillis(5));

    // then — the good record was processed, the poison skipped, and the offset advanced past both
    assertThat(processed).containsExactly("good");
    assertThat(committedOffsets).containsEntry(1, 6L);
  }

  @Test
  void shouldForceCommitWhenATaskIsOverCapacity() throws Exception {
    // given — a task that asks to be checkpointed (as a full bounded cache would), and a commit
    // clock so slow only the memory-pressure path can trigger the barrier
    final Map<Integer, Long> committedOffsets = new HashMap<>();
    final CountDownLatch offsetCommitted = new CountDownLatch(1);

    final Consumer consumer = mock(Consumer.class);
    final EventBridgeClient client = mock(EventBridgeClient.class);
    when(client.subscribe(any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(consumer));
    when(consumer.sendHeartbeat()).thenReturn(CompletableFuture.completedFuture(null));
    final Event event = new Event(5L, TOPIC, 1, "a".getBytes(StandardCharsets.UTF_8));
    when(consumer.poll(anyInt(), any())).thenReturn(List.of(event)).thenReturn(List.of());
    when(consumer.commitOffset(any(), anyInt(), anyLong()))
        .thenAnswer(
            invocation -> {
              committedOffsets.put(invocation.getArgument(1), invocation.getArgument(2));
              offsetCommitted.countDown();
              return CompletableFuture.completedFuture(null);
            });

    final Task<String> alwaysFull =
        new Task<>() {
          @Override
          public void process(final String record) {}

          @Override
          public boolean needsCheckpoint() {
            return true;
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
            .taskFactory(partition -> alwaysFull)
            .transactionRunner(Runnable::run)
            .offsetStore(
                new OffsetStore() {
                  @Override
                  public Map<Integer, Long> restore() {
                    return Map.of();
                  }

                  @Override
                  public void store(final int partition, final long offset) {}
                })
            .commitInterval(Duration.ofHours(1))
            .build();

    // when
    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();

    // then — the offset was committed promptly, driven by needsCheckpoint(), not the commit clock
    assertThat(offsetCommitted.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(committedOffsets).containsEntry(1, 5L);
    runtime.stop();
    loop.join(TimeUnit.SECONDS.toMillis(5));
  }

  @Test
  void shouldPunctuateWallClockOnTheTickAfterAPartitionGoesIdle() throws Exception {
    // given — one record materializes the task, then the partition is idle forever
    final CountDownLatch wallClockTicks = new CountDownLatch(2);

    final Consumer consumer = mock(Consumer.class);
    final EventBridgeClient client = mock(EventBridgeClient.class);
    when(client.subscribe(any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(consumer));
    when(consumer.sendHeartbeat()).thenReturn(CompletableFuture.completedFuture(null));
    final Event event = new Event(5L, TOPIC, 1, "a".getBytes(StandardCharsets.UTF_8));
    when(consumer.poll(anyInt(), any())).thenReturn(List.of(event)).thenReturn(List.of());
    when(consumer.commitOffset(any(), anyInt(), anyLong()))
        .thenReturn(CompletableFuture.completedFuture(null));

    final Task<String> task =
        new Task<>() {
          @Override
          public void process(final String record) {}

          @Override
          public void punctuateWallClock(final long wallClockMs) {
            wallClockTicks.countDown();
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
            .offsetStore(
                new OffsetStore() {
                  @Override
                  public Map<Integer, Long> restore() {
                    return Map.of();
                  }

                  @Override
                  public void store(final int partition, final long offset) {}
                })
            .punctuationInterval(Duration.ofMillis(20))
            .commitInterval(Duration.ofHours(1))
            .build();

    // when
    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();

    // then — the wall-clock tick keeps firing on the idle partition's task
    assertThat(wallClockTicks.await(5, TimeUnit.SECONDS)).isTrue();
    runtime.stop();
    loop.join(TimeUnit.SECONDS.toMillis(5));
  }

  @Test
  void shouldCommitEachPartitionInItsOwnTransaction() throws Exception {
    // given — a poll batch spanning two partitions
    final Map<Integer, Long> committedOffsets = new HashMap<>();
    final AtomicInteger transactions = new AtomicInteger();
    final AtomicInteger preCommitFlushes = new AtomicInteger();
    final CountDownLatch bothCommitted = new CountDownLatch(2);

    final Consumer consumer = mock(Consumer.class);
    final EventBridgeClient client = mock(EventBridgeClient.class);
    when(client.subscribe(any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(consumer));
    when(consumer.sendHeartbeat()).thenReturn(CompletableFuture.completedFuture(null));
    final Event onP1 = new Event(5L, TOPIC, 1, "a".getBytes(StandardCharsets.UTF_8));
    final Event onP2 = new Event(7L, TOPIC, 2, "b".getBytes(StandardCharsets.UTF_8));
    when(consumer.poll(anyInt(), any())).thenReturn(List.of(onP1, onP2)).thenReturn(List.of());
    when(consumer.commitOffset(any(), anyInt(), anyLong()))
        .thenAnswer(
            invocation -> {
              committedOffsets.put(invocation.getArgument(1), invocation.getArgument(2));
              bothCommitted.countDown();
              return CompletableFuture.completedFuture(null);
            });

    final Task<String> task =
        new Task<>() {
          @Override
          public void process(final String record) {}

          @Override
          public void preCommitFlush() {
            preCommitFlushes.incrementAndGet();
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
            .transactionRunner(
                operations -> {
                  transactions.incrementAndGet();
                  operations.run();
                })
            .offsetStore(
                new OffsetStore() {
                  @Override
                  public Map<Integer, Long> restore() {
                    return Map.of();
                  }

                  @Override
                  public void store(final int partition, final long offset) {}
                })
            .commitInterval(Duration.ZERO)
            .build();

    // when
    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();
    assertThat(bothCommitted.await(5, TimeUnit.SECONDS)).isTrue();
    runtime.stop();
    loop.join(TimeUnit.SECONDS.toMillis(5));

    // then — each partition advanced independently, in its own transaction and pre-commit flush
    assertThat(committedOffsets).containsEntry(1, 5L).containsEntry(2, 7L);
    assertThat(transactions.get()).isEqualTo(2);
    assertThat(preCommitFlushes.get()).isEqualTo(2);
  }

  @Test
  void shouldDelegateDurabilityToAnOwningTaskAndDedupTheResumeGap() throws Exception {
    // given — a self-owning task restored at offset 4; the batch replays 3,4 and adds 5
    final List<String> processed = new ArrayList<>();
    final List<Long> committedByTask = new ArrayList<>();
    final CountDownLatch committed = new CountDownLatch(1);

    final Consumer consumer = mock(Consumer.class);
    final EventBridgeClient client = mock(EventBridgeClient.class);
    when(client.subscribe(any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(consumer));
    when(consumer.sendHeartbeat()).thenReturn(CompletableFuture.completedFuture(null));
    final Event onThree = new Event(3L, TOPIC, 1, "c".getBytes(StandardCharsets.UTF_8));
    final Event onFour = new Event(4L, TOPIC, 1, "d".getBytes(StandardCharsets.UTF_8));
    final Event onFive = new Event(5L, TOPIC, 1, "e".getBytes(StandardCharsets.UTF_8));
    when(consumer.poll(anyInt(), any()))
        .thenReturn(List.of(onThree, onFour, onFive))
        .thenReturn(List.of());
    when(consumer.commitOffset(any(), anyInt(), anyLong()))
        .thenReturn(CompletableFuture.completedFuture(null));

    final Task<String> owningTask =
        new Task<>() {
          @Override
          public boolean ownsDurability() {
            return true;
          }

          @Override
          public long restore() {
            return 4L;
          }

          @Override
          public void process(final String record) {
            processed.add(record);
          }

          @Override
          public void commit(final long offset) {
            committedByTask.add(offset);
            committed.countDown();
          }
        };

    // no transactionRunner / offsetStore supplied — an owning task provides its own
    final StreamRuntime<String> runtime =
        StreamRuntime.<String>builder()
            .client(client)
            .group("g")
            .instanceId("i")
            .sourceTopic(TOPIC)
            .deserializer(
                (payload, partition, offset) -> new String(payload, StandardCharsets.UTF_8))
            .taskFactory(partition -> owningTask)
            .commitInterval(Duration.ZERO)
            .build();

    // when
    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();
    assertThat(committed.await(5, TimeUnit.SECONDS)).isTrue();
    runtime.stop();
    loop.join(TimeUnit.SECONDS.toMillis(5));

    // then — offsets 3 and 4 were deduped, only 5 processed, and the task made its own commit
    assertThat(processed).containsExactly("e");
    assertThat(committedByTask).contains(5L);
  }

  @SuppressWarnings("unchecked")
  private static void doAnswerSeek(
      final Consumer consumer,
      final Map<TopicPartition, Long> captured,
      final CountDownLatch sought) {
    final AtomicBoolean once = new AtomicBoolean();
    org.mockito.Mockito.doAnswer(
            invocation -> {
              if (once.compareAndSet(false, true)) {
                captured.putAll((Map<TopicPartition, Long>) invocation.getArgument(0));
                sought.countDown();
              }
              return null;
            })
        .when(consumer)
        .seek(any());
  }
}
