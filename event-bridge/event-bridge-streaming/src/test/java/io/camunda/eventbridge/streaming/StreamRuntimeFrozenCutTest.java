/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * The frozen-cut commit path: a task that supports {@link Task#freezeCut} keeps folding while its
 * cut persists on the IO thread, commits the barrier's offset atomically with the frozen state,
 * runs at most one cut at a time, stalls only on budget exhaustion, merges a failed cut back for
 * retry, and finishes an in-flight cut before the final stop commit.
 */
final class StreamRuntimeFrozenCutTest {

  private static final String TOPIC = "facts";
  private static final int PARTITION = 1;

  private Consumer consumer;
  private EventBridgeClient client;

  private void stubClient() {
    consumer = mock(Consumer.class);
    client = mock(EventBridgeClient.class);
    when(client.subscribe(any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(consumer));
    when(consumer.sendHeartbeat()).thenReturn(CompletableFuture.completedFuture(null));
  }

  private List<Long> stubCommittedOffsets(final List<String> journal) {
    final List<Long> committed = new CopyOnWriteArrayList<>();
    when(consumer.commitOffset(any(), anyInt(), anyLong()))
        .thenAnswer(
            invocation -> {
              final long offset = invocation.getArgument(2);
              committed.add(offset);
              journal.add("commitOffset:" + offset);
              return CompletableFuture.completedFuture(null);
            });
    return committed;
  }

  private StreamRuntime<String> runtime(final FrozenCutTask task, final List<String> journal) {
    return runtime(task, journal, Duration.ZERO);
  }

  private StreamRuntime<String> runtime(
      final FrozenCutTask task, final List<String> journal, final Duration commitInterval) {
    task.journal = journal;
    return StreamRuntime.<String>builder()
        .client(client)
        .group("g")
        .instanceId("i")
        .sourceTopic(TOPIC)
        .deserializer((payload, partition, offset) -> new String(payload, StandardCharsets.UTF_8))
        .taskFactory(partition -> task)
        .transactionRunner(
            operations -> {
              journal.add("tx-begin");
              operations.run();
              journal.add("tx-end");
            })
        .offsetStore(
            new OffsetStore() {
              @Override
              public Map<Integer, Long> restore() {
                return Map.of();
              }

              @Override
              public void store(final int partition, final long offset) {
                journal.add("offset:" + offset);
              }
            })
        .sinkIoThreads(1)
        .commitInterval(commitInterval)
        .build();
  }

  private static Event event(final long offset) {
    return new Event(offset, TOPIC, PARTITION, ("e" + offset).getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void shouldKeepFoldingWhileAFrozenCutPersistsAndCommitTheBarrierOffsetAtomically()
      throws Exception {
    // given — persist blocks until released; a second batch is gated behind the first freeze
    stubClient();
    final FrozenCutTask task = new FrozenCutTask();
    task.persistGate = new CountDownLatch(1);
    final AtomicBoolean batch1Delivered = new AtomicBoolean();
    final AtomicBoolean batch2Ready = new AtomicBoolean();
    final AtomicBoolean batch2Delivered = new AtomicBoolean();
    when(consumer.poll(anyInt(), any()))
        .thenAnswer(
            invocation -> {
              if (!batch1Delivered.getAndSet(true)) {
                return List.of(event(1), event(2));
              }
              if (batch2Ready.get() && !batch2Delivered.getAndSet(true)) {
                return List.of(event(3));
              }
              return List.of();
            });
    final List<String> journal = new CopyOnWriteArrayList<>();
    final List<Long> committed = stubCommittedOffsets(journal);
    final StreamRuntime<String> runtime = runtime(task, journal);
    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();

    // when — the first cut freezes and its persist hangs; only then does the next record arrive
    await().until(() -> task.freezes.get() == 1);
    batch2Ready.set(true);

    // then — the record is folded while the cut is still persisting, and no second cut starts
    await().until(() -> task.processed.contains("e3"));
    assertThat(task.persisted).isEmpty();
    assertThat(task.freezes).hasValue(1);

    // when — the persist is released
    task.persistGate.countDown();

    // then — the first cut lands with the barrier's offset, state and offset in one transaction,
    // the source offset advancing only afterwards; the post-freeze record commits in a later cut
    await().until(() -> committed.size() >= 2);
    final long barrierOffset = committed.get(0);
    assertThat(journal.subList(0, 5))
        .containsExactly(
            "tx-begin",
            "offset:" + barrierOffset,
            "persist",
            "tx-end",
            "commitOffset:" + barrierOffset);
    assertThat(committed).isSorted().last().isEqualTo(3L);
    assertThat(task.persisted.stream().flatMap(List::stream))
        .containsExactly("e1", "e2", "e3"); // every record persisted exactly once, in order
    assertThat(task.completions).allMatch(Boolean::booleanValue);

    runtime.stop();
    loop.join(TimeUnit.SECONDS.toMillis(5));
  }

  @Test
  void shouldMergeAFailedCutBackAndRetryItInTheNextCut() throws Exception {
    // given — the first persist fails after the cut was frozen
    stubClient();
    final FrozenCutTask task = new FrozenCutTask();
    task.persistFailures = 1;
    final AtomicBoolean delivered = new AtomicBoolean();
    when(consumer.poll(anyInt(), any()))
        .thenAnswer(
            invocation -> delivered.getAndSet(true) ? List.of() : List.of(event(1), event(2)));
    final List<String> journal = new CopyOnWriteArrayList<>();
    final List<Long> committed = stubCommittedOffsets(journal);
    final StreamRuntime<String> runtime = runtime(task, journal);
    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();

    // then — the failed cut merges back and the retry persists the same records exactly once
    await().until(() -> committed.contains(2L));
    assertThat(task.completions).startsWith(false).endsWith(true);
    assertThat(task.persisted.stream().flatMap(List::stream)).containsExactly("e1", "e2");
    assertThat(task.freezes.get()).isGreaterThanOrEqualTo(2);

    runtime.stop();
    loop.join(TimeUnit.SECONDS.toMillis(5));
  }

  @Test
  void shouldStallFoldingOnBudgetExhaustionOnlyWhileACutIsInFlight() throws Exception {
    // given — a two-record budget: the first batch freezes a cut whose persist hangs; the next
    // batch exhausts the budget again while that cut is still in flight
    stubClient();
    final FrozenCutTask task = new FrozenCutTask();
    task.needsCheckpointAt = 2;
    task.persistGate = new CountDownLatch(1);
    final AtomicBoolean batch1Delivered = new AtomicBoolean();
    final AtomicBoolean batch2Ready = new AtomicBoolean();
    final AtomicBoolean batch2Delivered = new AtomicBoolean();
    when(consumer.poll(anyInt(), any()))
        .thenAnswer(
            invocation -> {
              if (!batch1Delivered.getAndSet(true)) {
                return List.of(event(1), event(2));
              }
              if (batch2Ready.get() && !batch2Delivered.getAndSet(true)) {
                return List.of(event(3), event(4), event(5));
              }
              return List.of();
            });
    final List<String> journal = new CopyOnWriteArrayList<>();
    final List<Long> committed = stubCommittedOffsets(journal);
    // A commit interval far beyond the test's runtime: only the budget triggers cuts, so the cut
    // boundaries are deterministic regardless of how the queue drains.
    final StreamRuntime<String> runtime = runtime(task, journal, Duration.ofHours(1));
    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();

    // when — the budget-triggered cut is in flight and three more records arrive
    await().until(() -> task.freezes.get() == 1);
    batch2Ready.set(true);

    // then — folding continues up to the budget (two more records) and stalls on the third
    await().until(() -> task.processed.contains("e4"));
    assertThat(task.processed).doesNotContain("e5");

    // when — the in-flight cut completes
    task.gateOpened.set(true);
    task.persistGate.countDown();

    // then — the stalled tail resumes only after the cut completed, and everything commits
    await().until(() -> committed.contains(5L));
    assertThat(task.gateOpenWhenProcessed).containsEntry("e5", true).containsEntry("e4", false);
    assertThat(task.persisted.stream().flatMap(List::stream))
        .containsExactly("e1", "e2", "e3", "e4", "e5");

    runtime.stop();
    loop.join(TimeUnit.SECONDS.toMillis(5));
  }

  @Test
  void shouldFinishAnInFlightCutBeforeStopping() throws Exception {
    // given — a stop request arrives while a cut is persisting
    stubClient();
    final FrozenCutTask task = new FrozenCutTask();
    task.persistGate = new CountDownLatch(1);
    final AtomicBoolean delivered = new AtomicBoolean();
    when(consumer.poll(anyInt(), any()))
        .thenAnswer(
            invocation -> delivered.getAndSet(true) ? List.of() : List.of(event(1), event(2)));
    final List<String> journal = new CopyOnWriteArrayList<>();
    final List<Long> committed = stubCommittedOffsets(journal);
    final StreamRuntime<String> runtime = runtime(task, journal);
    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();
    await().until(() -> task.freezes.get() == 1);

    // when — stop while the persist hangs, then release it
    runtime.stop();
    task.persistGate.countDown();

    // then — the runtime waits for the cut, retires it, and shuts down cleanly; depending on how
    // the queue drained, the tail may land in the final synchronous stop commit
    loop.join(TimeUnit.SECONDS.toMillis(10));
    assertThat(loop.isAlive()).isFalse();
    assertThat(committed).isSorted().last().isEqualTo(2L);
    assertThat(task.completions).allMatch(Boolean::booleanValue);
    assertThat(task.persisted.stream().flatMap(List::stream)).containsExactly("e1", "e2");
    assertThat(task.closed).isTrue();
  }

  /**
   * A task with frozen-cut support: {@code live} is the actor-thread working set (records since the
   * last freeze), a freeze steals it into an immutable cut, persist optionally blocks or fails, and
   * a failed cut merges back underneath newer records.
   */
  private static final class FrozenCutTask implements Task<String> {

    private final List<String> live = new ArrayList<>();
    private final List<List<String>> persisted = new CopyOnWriteArrayList<>();
    private final List<Boolean> completions = new CopyOnWriteArrayList<>();
    private final List<String> processed = new CopyOnWriteArrayList<>();
    private final Map<String, Boolean> gateOpenWhenProcessed = new ConcurrentHashMap<>();
    private final AtomicInteger freezes = new AtomicInteger();
    private final AtomicBoolean gateOpened = new AtomicBoolean();
    private volatile List<String> journal = new CopyOnWriteArrayList<>();
    private volatile CountDownLatch persistGate;
    private volatile int persistFailures;
    private volatile int needsCheckpointAt = Integer.MAX_VALUE;
    private volatile boolean closed;

    @Override
    public void process(final String record) {
      live.add(record);
      processed.add(record);
      gateOpenWhenProcessed.put(record, gateOpened.get());
    }

    @Override
    public boolean needsCheckpoint() {
      return live.size() >= needsCheckpointAt;
    }

    @Override
    public CommitCut freezeCut(final long offset) {
      freezes.incrementAndGet();
      final List<String> cut = List.copyOf(live);
      live.clear();
      return new CommitCut() {
        @Override
        public void persist() {
          final CountDownLatch gate = persistGate;
          if (gate != null) {
            try {
              if (!gate.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("persist gate never opened");
              }
            } catch (final InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new IllegalStateException(e);
            }
          }
          if (persistFailures > 0) {
            persistFailures--;
            throw new IllegalStateException("injected persist failure");
          }
          journal.add("persist");
          persisted.add(cut);
        }

        @Override
        public void complete(final boolean success) {
          completions.add(success);
          if (!success) {
            live.addAll(0, cut); // merge back underneath records folded since the freeze
          }
        }
      };
    }

    @Override
    public void checkpoint() {
      // The legacy synchronous path (the final stop commit): persist the live working set.
      if (!live.isEmpty()) {
        persisted.add(List.copyOf(live));
        live.clear();
      }
    }

    @Override
    public void close() {
      closed = true;
    }
  }
}
