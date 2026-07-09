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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.eventbridge.client.Consumer;
import io.camunda.eventbridge.client.Event;
import io.camunda.eventbridge.client.EventBridgeClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;

final class StreamRuntimeTest {

  private static final String TOPIC = "facts";

  @Test
  void shouldRunBlockingCommitsOnTheConfiguredSinkExecutor() throws Exception {
    // given — a runtime with a custom sink thread factory and one record; the shard's blocking
    // commit runs on the sink IO executor, never on the actor/source thread
    final Consumer consumer = mock(Consumer.class);
    final EventBridgeClient client = mock(EventBridgeClient.class);
    when(client.subscribe(any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(consumer));
    when(consumer.sendHeartbeat()).thenReturn(CompletableFuture.completedFuture(null));
    when(consumer.poll(anyInt(), any()))
        .thenReturn(List.of(new Event(5L, TOPIC, 1, "a".getBytes(StandardCharsets.UTF_8))))
        .thenReturn(List.of());
    when(consumer.commitOffset(any(), anyInt(), anyLong()))
        .thenReturn(CompletableFuture.completedFuture(null));

    final AtomicReference<String> commitThreadName = new AtomicReference<>();
    final CountDownLatch committed = new CountDownLatch(1);
    final ThreadFactory sinkFactory =
        runnable -> {
          final Thread thread = new Thread(runnable, "custom-sink");
          thread.setDaemon(true);
          return thread;
        };
    final Task<String> task =
        new Task<>() {
          @Override
          public void process(final String record) {}

          @Override
          public long restore() {
            // A restored baseline below the record's offset, so materialization does not take the
            // rebuild-from-source-start path (which would discard this poll's record).
            return 0L;
          }

          @Override
          public void commit(final long offset) {
            // The commit runs on the sink IO executor — capture the thread it ran on.
            commitThreadName.set(Thread.currentThread().getName());
            committed.countDown();
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
            .sinkIoThreads(1)
            .sinkThreadFactory(sinkFactory)
            .commitInterval(Duration.ZERO)
            .build();

    // when
    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();

    // then — the blocking commit ran on the configured sink executor, not the actor/source thread
    assertThat(committed.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(commitThreadName.get()).isEqualTo("custom-sink");

    runtime.stop();
    loop.join(TimeUnit.SECONDS.toMillis(5));
  }

  @Test
  void shouldProduceBeforeCommitAndPersistStateWithOffsetAtomically() throws Exception {
    // given — a task journaling the exact order of its commit barrier
    final List<String> order = Collections.synchronizedList(new ArrayList<>());
    final Map<Integer, Long> committedOffsets = new ConcurrentHashMap<>();
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
          public long restore() {
            return 0L; // a real baseline, so materialization resumes rather than rebuilds
          }

          @Override
          public void flush() {
            order.add("task.flush");
          }

          @Override
          public void commit(final long offset) {
            // The shard's own atomic cut: state and offset in one transaction the task owns.
            order.add("txn.begin");
            order.add("offset.store");
            committedOffsets.put(1, offset);
            order.add("task.checkpoint");
            order.add("txn.end");
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
            .commitInterval(Duration.ZERO)
            .build();

    // when — run the loop until the first offset commit, then stop
    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();
    assertThat(offsetCommitted.await(5, TimeUnit.SECONDS)).isTrue();
    runtime.stop();
    loop.join(TimeUnit.SECONDS.toMillis(5));

    // then — the record was processed, and the barrier ran produce-before-commit with the offset
    // and the state inside the task's one transaction, before the source offset was committed.
    assertThat(processed).containsExactly("a");
    assertThat(committedOffsets).containsEntry(1, 5L);
    assertThat(order)
        .containsSubsequence(
            "task.flush",
            "txn.begin",
            "offset.store",
            "task.checkpoint",
            "txn.end",
            "commitOffset");
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
              // The source and processor are decoupled, so the skipped record (offset 5) may commit
              // in its own cut before offset 6; wait for the terminal commit past the good record.
              if ((long) invocation.getArgument(2) == 6L) {
                offsetCommitted.countDown();
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

                      @Override
                      public long restore() {
                        return 0L; // a real baseline, so materialization resumes, not rebuilds
                      }
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
  void shouldAdvanceCommitAndStreamTimePastAFilteredTail() throws Exception {
    // given — one accepted record followed by a filtered-only tail carrying newer event times
    final List<String> processed = new ArrayList<>();
    final Map<Integer, Long> committedOffsets = new ConcurrentHashMap<>();
    final CountDownLatch tailCommitted = new CountDownLatch(1);
    final CountDownLatch tailTimeSeen = new CountDownLatch(1);

    final Consumer consumer = mock(Consumer.class);
    final EventBridgeClient client = mock(EventBridgeClient.class);
    when(client.subscribe(any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(consumer));
    when(consumer.sendHeartbeat()).thenReturn(CompletableFuture.completedFuture(null));
    when(consumer.poll(anyInt(), any()))
        .thenReturn(
            List.of(
                new Event(5L, TOPIC, 1, "keep:100".getBytes(StandardCharsets.UTF_8)),
                new Event(6L, TOPIC, 1, "skip:200".getBytes(StandardCharsets.UTF_8)),
                new Event(7L, TOPIC, 1, "skip:300".getBytes(StandardCharsets.UTF_8))))
        .thenReturn(List.of());
    when(consumer.commitOffset(any(), anyInt(), anyLong()))
        .thenAnswer(
            invocation -> {
              committedOffsets.put(invocation.getArgument(1), invocation.getArgument(2));
              if ((long) invocation.getArgument(2) == 7L) {
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
            .payloadTimestamps(
                payload -> Long.parseLong(new String(payload, StandardCharsets.UTF_8).substring(5)))
            .timestampExtractor(record -> Long.parseLong(record.substring(5)))
            .taskFactory(
                partition ->
                    new Task<>() {
                      @Override
                      public void process(final String record) {
                        processed.add(record);
                      }

                      @Override
                      public long restore() {
                        return 0L; // a real baseline, so materialization resumes, not rebuilds
                      }

                      @Override
                      public void advanceStreamTime(final long streamTimeMs) {
                        if (streamTimeMs >= 300L) {
                          tailTimeSeen.countDown();
                        }
                      }
                    })
            .commitInterval(Duration.ZERO)
            .punctuationInterval(Duration.ofMillis(10))
            .build();

    // when
    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();

    // then — the commit position and stream time both advanced past the filtered run, even though
    // nothing after offset 5 was folded
    assertThat(tailCommitted.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(tailTimeSeen.await(5, TimeUnit.SECONDS)).isTrue();
    runtime.stop();
    loop.join(TimeUnit.SECONDS.toMillis(5));
    assertThat(processed).containsExactly("keep:100");
    assertThat(committedOffsets).containsEntry(1, 7L);
  }

  @Test
  void shouldFoldTheWholeBatchWhenAMemoryPressureCommitInterruptsIt() throws Exception {
    // given — three records arriving in ONE poll batch, and a task that demands a checkpoint
    // right after the first record is folded (as a full bounded cache would)
    final List<String> processed = Collections.synchronizedList(new ArrayList<>());
    final Map<Integer, Long> committedOffsets = new ConcurrentHashMap<>();
    final CountDownLatch allCommitted = new CountDownLatch(1);

    final Consumer consumer = mock(Consumer.class);
    final EventBridgeClient client = mock(EventBridgeClient.class);
    when(client.subscribe(any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(consumer));
    when(consumer.sendHeartbeat()).thenReturn(CompletableFuture.completedFuture(null));
    when(consumer.poll(anyInt(), any()))
        .thenReturn(
            List.of(
                new Event(5L, TOPIC, 1, "a".getBytes(StandardCharsets.UTF_8)),
                new Event(6L, TOPIC, 1, "b".getBytes(StandardCharsets.UTF_8)),
                new Event(7L, TOPIC, 1, "c".getBytes(StandardCharsets.UTF_8))))
        .thenReturn(List.of());
    when(consumer.commitOffset(any(), anyInt(), anyLong()))
        .thenAnswer(
            invocation -> {
              committedOffsets.put(invocation.getArgument(1), invocation.getArgument(2));
              if ((long) invocation.getArgument(2) == 7L) {
                allCommitted.countDown();
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
            .taskFactory(
                partition ->
                    new Task<>() {
                      @Override
                      public void process(final String record) {
                        processed.add(record);
                      }

                      @Override
                      public long restore() {
                        return 0L; // a real baseline, so materialization resumes, not rebuilds
                      }

                      @Override
                      public boolean needsCheckpoint() {
                        // Memory pressure exactly once: after the first record of the batch.
                        return processed.size() == 1;
                      }
                    })
            .commitInterval(Duration.ZERO)
            .build();

    // when
    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();

    // then — the batch tail folded after the forced mid-batch commit; nothing was dropped
    assertThat(allCommitted.await(5, TimeUnit.SECONDS)).isTrue();
    runtime.stop();
    loop.join(TimeUnit.SECONDS.toMillis(5));
    assertThat(processed).containsExactly("a", "b", "c");
    assertThat(committedOffsets).containsEntry(1, 7L);
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
          public long restore() {
            return 0L; // a real baseline, so materialization resumes rather than rebuilds
          }

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
          public long restore() {
            return 0L; // a real baseline, so materialization resumes rather than rebuilds
          }

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
    // given — a poll batch spanning two partitions (committed concurrently by two shards)
    final Map<Integer, Long> committedOffsets = new ConcurrentHashMap<>();
    final Map<Integer, Long> committedByTask = new ConcurrentHashMap<>();
    final AtomicInteger transactions = new AtomicInteger();
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

    // One task instance per partition, each committing its own shard's transaction.
    final IntFunction<Task<String>> taskFactory =
        partitionId ->
            new Task<>() {
              @Override
              public void process(final String record) {}

              @Override
              public long restore() {
                return 0L; // a real baseline, so materialization resumes rather than rebuilds
              }

              @Override
              public void commit(final long offset) {
                transactions.incrementAndGet();
                committedByTask.put(partitionId, offset);
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
            .taskFactory(taskFactory)
            .commitInterval(Duration.ZERO)
            .build();

    // when
    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();
    assertThat(bothCommitted.await(5, TimeUnit.SECONDS)).isTrue();
    runtime.stop();
    loop.join(TimeUnit.SECONDS.toMillis(5));

    // then — each partition advanced independently, its own shard cutting its own transaction
    assertThat(committedOffsets).containsEntry(1, 5L).containsEntry(2, 7L);
    assertThat(committedByTask).containsEntry(1, 5L).containsEntry(2, 7L);
    assertThat(transactions.get()).isEqualTo(2);
  }

  @Test
  void shouldRestoreTheBaselineFromTheTaskAndDedupTheResumeGap() throws Exception {
    // given — a task restored at offset 4; the batch replays 3,4 and adds 5
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

    final Task<String> task =
        new Task<>() {
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

    final StreamRuntime<String> runtime =
        StreamRuntime.<String>builder()
            .client(client)
            .group("g")
            .instanceId("i")
            .sourceTopic(TOPIC)
            .deserializer(
                (payload, partition, offset) -> new String(payload, StandardCharsets.UTF_8))
            .taskFactory(partition -> task)
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

  @Test
  void shouldPreservePerPartitionOrderAcrossParallelProcessing() throws Exception {
    // given — one poll batch interleaving two partitions: p1@1, p2@1, p1@2, p2@2
    final List<String> processed = Collections.synchronizedList(new ArrayList<>());
    final CountDownLatch allProcessed = new CountDownLatch(4);

    final Consumer consumer = mock(Consumer.class);
    final EventBridgeClient client = mock(EventBridgeClient.class);
    when(client.subscribe(any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(consumer));
    when(consumer.sendHeartbeat()).thenReturn(CompletableFuture.completedFuture(null));
    when(consumer.poll(anyInt(), any()))
        .thenReturn(
            List.of(
                new Event(1L, TOPIC, 1, "a".getBytes(StandardCharsets.UTF_8)),
                new Event(1L, TOPIC, 2, "x".getBytes(StandardCharsets.UTF_8)),
                new Event(2L, TOPIC, 1, "b".getBytes(StandardCharsets.UTF_8)),
                new Event(2L, TOPIC, 2, "y".getBytes(StandardCharsets.UTF_8))))
        .thenReturn(List.of());
    when(consumer.commitOffset(any(), anyInt(), anyLong()))
        .thenReturn(CompletableFuture.completedFuture(null));

    final Task<String> task =
        new Task<>() {
          @Override
          public void process(final String record) {
            processed.add(record);
            allProcessed.countDown();
          }

          @Override
          public long restore() {
            return 0L; // a real baseline, so materialization resumes rather than rebuilds
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
            .build();

    // when
    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();
    assertThat(allProcessed.await(5, TimeUnit.SECONDS)).isTrue();
    runtime.stop();
    loop.join(TimeUnit.SECONDS.toMillis(5));

    // then — all four ran, and each partition kept its own offset order (cross-partition order is
    // irrelevant: partitions are independent shards processed in parallel).
    assertThat(processed).containsExactlyInAnyOrder("a", "b", "x", "y");
    assertThat(processed).containsSubsequence("a", "b");
    assertThat(processed).containsSubsequence("x", "y");
  }
}
