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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
 * The commit-cut path: a task keeps folding while its cut ({@link Task#freezeCut}) persists on the
 * IO thread, commits the barrier's offset atomically with the frozen state, runs at most one cut at
 * a time, stalls only on budget exhaustion, merges a failed cut back for retry, and finishes an
 * in-flight cut before the final stop cut, which runs inline on the actor thread.
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
    return runtime(task, journal, Duration.ZERO, null);
  }

  private StreamRuntime<String> runtime(
      final FrozenCutTask task, final List<String> journal, final Duration commitInterval) {
    return runtime(task, journal, commitInterval, null);
  }

  private StreamRuntime<String> runtime(
      final FrozenCutTask task,
      final List<String> journal,
      final Duration commitInterval,
      final MeterRegistry meterRegistry) {
    task.journal = journal;
    return StreamRuntime.<String>builder()
        .client(client)
        .group("g")
        .instanceId("i")
        .sourceTopic(TOPIC)
        .deserializer((payload, partition, offset) -> new String(payload, StandardCharsets.UTF_8))
        .taskFactory(partition -> task)
        .sinkIoThreads(1)
        .commitInterval(commitInterval)
        .meterRegistry(meterRegistry)
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
    // the queue drained, the tail may land in the final inline stop cut
    loop.join(TimeUnit.SECONDS.toMillis(10));
    assertThat(loop.isAlive()).isFalse();
    assertThat(committed).isSorted().last().isEqualTo(2L);
    assertThat(task.completions).allMatch(Boolean::booleanValue);
    assertThat(task.persisted.stream().flatMap(List::stream)).containsExactly("e1", "e2");
    assertThat(task.closed).isTrue();
  }

  @Test
  void shouldRecordFreezeAndPersistTimersForEveryCompletedCut() throws Exception {
    // given — a meter registry and a batch committing through the frozen-cut path
    stubClient();
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final FrozenCutTask task = new FrozenCutTask();
    final AtomicBoolean delivered = new AtomicBoolean();
    when(consumer.poll(anyInt(), any()))
        .thenAnswer(
            invocation -> delivered.getAndSet(true) ? List.of() : List.of(event(1), event(2)));
    final List<String> journal = new CopyOnWriteArrayList<>();
    final List<Long> committed = stubCommittedOffsets(journal);
    final StreamRuntime<String> runtime = runtime(task, journal, Duration.ZERO, registry);
    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();

    // when — the cut commits and the runtime stops (finishing any in-flight cut)
    await().until(() -> committed.contains(2L));
    runtime.stop();
    loop.join(TimeUnit.SECONDS.toMillis(5));

    // then — one freeze sample per frozen cut and one persist sample per completed cut, tagged by
    // partition; no retries, no write stalls
    final Timer freezeTimer =
        registry.get("eb.streaming.cut.freeze.duration").tag("partition", "1").timer();
    final Timer persistTimer =
        registry.get("eb.streaming.cut.persist.duration").tag("partition", "1").timer();
    assertThat(freezeTimer.count()).isPositive().isEqualTo(task.freezes.get());
    assertThat(persistTimer.count())
        .isEqualTo(task.completions.stream().filter(Boolean::booleanValue).count());
    assertThat(retryCount(registry)).isZero();
    assertThat(writeStallCount(registry)).isZero();
  }

  @Test
  void shouldCountARetryWhenAPersistFailsAndTheCutMergesBack() throws Exception {
    // given — the first persist fails after the cut was frozen
    stubClient();
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final FrozenCutTask task = new FrozenCutTask();
    task.persistFailures = 1;
    final AtomicBoolean delivered = new AtomicBoolean();
    when(consumer.poll(anyInt(), any()))
        .thenAnswer(
            invocation -> delivered.getAndSet(true) ? List.of() : List.of(event(1), event(2)));
    final List<String> journal = new CopyOnWriteArrayList<>();
    final List<Long> committed = stubCommittedOffsets(journal);
    final StreamRuntime<String> runtime = runtime(task, journal, Duration.ZERO, registry);
    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();

    // when — the failed cut merges back and the retry commits
    await().until(() -> committed.contains(2L));
    runtime.stop();
    loop.join(TimeUnit.SECONDS.toMillis(5));

    // then — exactly the injected failure is counted, and only successful persists are timed
    assertThat(retryCount(registry)).isEqualTo(1.0);
    assertThat(
            registry.get("eb.streaming.cut.persist.duration").tag("partition", "1").timer().count())
        .isEqualTo(task.completions.stream().filter(Boolean::booleanValue).count());
  }

  @Test
  void shouldCountAWriteStallEntryExactlyOncePerStall() throws Exception {
    // given — a two-record budget: the first batch freezes a cut whose persist hangs; the next
    // batch exhausts the budget again while that cut is still in flight
    stubClient();
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
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
    final StreamRuntime<String> runtime = runtime(task, journal, Duration.ofHours(1), registry);
    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();

    // when — the budget-triggered cut is in flight and the fold exhausts the budget again
    await().until(() -> task.freezes.get() == 1);
    batch2Ready.set(true);
    await().until(() -> task.processed.contains("e4"));

    // then — the stall entry is counted once, not per re-check while stalled
    await().until(() -> writeStallCount(registry) == 1.0);

    // when — the in-flight cut completes and the stalled tail drains through further cuts
    task.persistGate.countDown();
    await().until(() -> committed.contains(5L));

    // then — the counter still holds exactly the one stall entry
    assertThat(writeStallCount(registry)).isEqualTo(1.0);

    runtime.stop();
    loop.join(TimeUnit.SECONDS.toMillis(5));
  }

  @Test
  void shouldMergeACutBackWhenTheSourceOffsetCommitFails() throws Exception {
    // given — the transaction commits, but the first (advisory) source-offset commit fails
    stubClient();
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final FrozenCutTask task = new FrozenCutTask();
    final List<String> journal = new CopyOnWriteArrayList<>();
    final List<Long> committed = new CopyOnWriteArrayList<>();
    final AtomicInteger attempts = new AtomicInteger();
    when(consumer.commitOffset(any(), anyInt(), anyLong()))
        .thenAnswer(
            invocation -> {
              final long offset = invocation.getArgument(2);
              if (attempts.getAndIncrement() == 0) {
                journal.add("commitOffset-failed:" + offset);
                return CompletableFuture.failedFuture(
                    new IllegalStateException("injected offset-commit failure"));
              }
              journal.add("commitOffset:" + offset);
              committed.add(offset);
              return CompletableFuture.completedFuture(null);
            });
    final AtomicBoolean delivered = new AtomicBoolean();
    when(consumer.poll(anyInt(), any()))
        .thenAnswer(
            invocation -> delivered.getAndSet(true) ? List.of() : List.of(event(1), event(2)));
    final StreamRuntime<String> runtime = runtime(task, journal, Duration.ZERO, registry);
    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();

    // then — the failed offset commit funnels into the same merge-back retry path as a failed
    // persist: the cut completes unsuccessfully, is re-frozen, and re-persisting the
    // already-committed transaction is idempotent (the same delta lands under the same keys)
    await().until(() -> committed.contains(2L));
    assertThat(task.completions).startsWith(false).endsWith(true);
    assertThat(task.persisted.stream().flatMap(List::stream))
        .containsExactly("e1", "e2", "e1", "e2");
    assertThat(retryCount(registry)).isEqualTo(1.0);
    assertThat(journal)
        .containsSubsequence(
            "tx-end", "commitOffset-failed:2", "tx-begin", "tx-end", "commitOffset:2");

    runtime.stop();
    loop.join(TimeUnit.SECONDS.toMillis(5));
  }

  @Test
  void shouldChainTheNextFreezeBehindTheSourceOffsetAck() throws Exception {
    // given — the first cut's source-offset ack is held back after its transaction committed
    stubClient();
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final FrozenCutTask task = new FrozenCutTask();
    final List<String> journal = new CopyOnWriteArrayList<>();
    final List<Long> committed = new CopyOnWriteArrayList<>();
    final CompletableFuture<Void> firstAck = new CompletableFuture<>();
    final AtomicInteger attempts = new AtomicInteger();
    when(consumer.commitOffset(any(), anyInt(), anyLong()))
        .thenAnswer(
            invocation -> {
              final long offset = invocation.getArgument(2);
              journal.add("commitOffset:" + offset);
              if (attempts.getAndIncrement() == 0) {
                return firstAck.whenComplete((ignored, error) -> committed.add(offset));
              }
              committed.add(offset);
              return CompletableFuture.completedFuture(null);
            });
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
    final StreamRuntime<String> runtime = runtime(task, journal, Duration.ZERO, registry);
    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();

    // when — the offset advance was sent (without joining) right after the transaction committed
    await().until(() -> journal.contains("commitOffset:2"));
    assertThat(journal).containsSubsequence("tx-end", "commitOffset:2");
    batch2Ready.set(true);

    // then — the actor keeps folding, but no second freeze starts and the persist timer has no
    // sample yet: the cut completes (and single-flight releases) only once the ack lands
    await().until(() -> task.processed.contains("e3"));
    assertThat(task.freezes).hasValue(1);
    assertThat(persistTimerCount(registry)).isZero();

    // when — the ack lands
    firstAck.complete(null);

    // then — the chained completion retires the cut, records its timer, and the next cut runs
    await().until(() -> committed.contains(3L));
    assertThat(task.completions).allMatch(Boolean::booleanValue);
    assertThat(persistTimerCount(registry)).isEqualTo(task.completions.size());

    runtime.stop();
    loop.join(TimeUnit.SECONDS.toMillis(5));
  }

  @Test
  void shouldJoinTheSourceOffsetCommitSynchronouslyOnTheStopPath() throws Exception {
    // given — no periodic cut (huge interval); the only commit is the final stop commit, whose
    // source-offset ack is held back
    stubClient();
    final FrozenCutTask task = new FrozenCutTask();
    final List<String> journal = new CopyOnWriteArrayList<>();
    final List<Long> committed = new CopyOnWriteArrayList<>();
    final CompletableFuture<Void> finalAck = new CompletableFuture<>();
    when(consumer.commitOffset(any(), anyInt(), anyLong()))
        .thenAnswer(
            invocation -> {
              final long offset = invocation.getArgument(2);
              journal.add("commitOffset:" + offset);
              return finalAck.whenComplete((ignored, error) -> committed.add(offset));
            });
    final AtomicBoolean delivered = new AtomicBoolean();
    when(consumer.poll(anyInt(), any()))
        .thenAnswer(
            invocation -> delivered.getAndSet(true) ? List.of() : List.of(event(1), event(2)));
    final StreamRuntime<String> runtime = runtime(task, journal, Duration.ofHours(1));
    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();
    await().until(() -> task.processed.contains("e2"));

    // when — stopping while the final commit's ack is pending
    runtime.stop();

    // then — the stop path stays fully synchronous: it joins the offset commit, so the runtime
    // does not finish shutting down (and the task stays open) until the ack lands
    await().until(() -> journal.contains("commitOffset:2"));
    loop.join(200);
    assertThat(loop.isAlive()).isTrue();
    assertThat(task.closed).isFalse();

    // when — the ack lands
    finalAck.complete(null);

    // then — the final synchronous commit completes and the runtime shuts down cleanly
    loop.join(TimeUnit.SECONDS.toMillis(10));
    assertThat(loop.isAlive()).isFalse();
    assertThat(committed).containsExactly(2L);
    assertThat(task.persisted.stream().flatMap(List::stream)).containsExactly("e1", "e2");
    assertThat(task.closed).isTrue();
  }

  @Test
  void shouldExecuteTheFinalStopCutInlineOnTheActorThread() throws Exception {
    // given — no periodic cut (huge interval); the only commit is the final stop cut
    stubClient();
    final FrozenCutTask task = new FrozenCutTask();
    final List<String> journal = new CopyOnWriteArrayList<>();
    final List<Long> committed = stubCommittedOffsets(journal);
    final AtomicBoolean delivered = new AtomicBoolean();
    when(consumer.poll(anyInt(), any()))
        .thenAnswer(
            invocation -> delivered.getAndSet(true) ? List.of() : List.of(event(1), event(2)));
    final StreamRuntime<String> runtime = runtime(task, journal, Duration.ofHours(1));
    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();
    await().until(() -> task.processed.contains("e2"));

    // when — stopping
    runtime.stop();
    loop.join(TimeUnit.SECONDS.toMillis(10));

    // then — exactly one cut ran (the stop cut), frozen and persisted on the SAME thread — the
    // partition's actor thread, never the sink IO pool — and it committed the offset before close
    assertThat(loop.isAlive()).isFalse();
    assertThat(task.freezes).hasValue(1);
    assertThat(task.persistThreads).hasSize(1);
    assertThat(task.persistThreads.get(0)).isSameAs(task.freezeThreads.get(0));
    assertThat(task.persistThreads.get(0).getName()).doesNotStartWith("eb-sink-");
    assertThat(committed).containsExactly(2L);
    assertThat(task.persisted.stream().flatMap(List::stream)).containsExactly("e1", "e2");
    assertThat(task.completions).containsExactly(true);
    assertThat(task.closed).isTrue();
  }

  @Test
  void shouldCloseCleanlyWhenTheFinalStopCutFails() throws Exception {
    // given — the only cut is the final stop cut, and its persist fails
    stubClient();
    final FrozenCutTask task = new FrozenCutTask();
    task.persistFailures = 1;
    final List<String> journal = new CopyOnWriteArrayList<>();
    final List<Long> committed = stubCommittedOffsets(journal);
    final AtomicBoolean delivered = new AtomicBoolean();
    when(consumer.poll(anyInt(), any()))
        .thenAnswer(
            invocation -> delivered.getAndSet(true) ? List.of() : List.of(event(1), event(2)));
    final StreamRuntime<String> runtime = runtime(task, journal, Duration.ofHours(1));
    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();
    await().until(() -> task.processed.contains("e2"));

    // when — stopping while the final cut is doomed to fail
    runtime.stop();
    loop.join(TimeUnit.SECONDS.toMillis(10));

    // then — the cut completed unsuccessfully (merged back) and the shard still closed cleanly;
    // nothing became durable, so a restart replays from the last durable cut
    assertThat(loop.isAlive()).isFalse();
    assertThat(task.completions).containsExactly(false);
    assertThat(task.closed).isTrue();
    assertThat(committed).isEmpty();
    assertThat(task.persisted).isEmpty();
  }

  private static long persistTimerCount(final SimpleMeterRegistry registry) {
    return registry.get("eb.streaming.cut.persist.duration").tag("partition", "1").timer().count();
  }

  private static double retryCount(final SimpleMeterRegistry registry) {
    return registry.get("eb.streaming.cut.retries").tag("partition", "1").counter().count();
  }

  private static double writeStallCount(final SimpleMeterRegistry registry) {
    return registry.get("eb.streaming.write.stalls").tag("partition", "1").counter().count();
  }

  /**
   * A self-contained shard: {@code live} is the actor-thread working set (records since the last
   * freeze), a freeze steals it into an immutable cut whose persist writes state and the barrier's
   * offset in the task's own journaled transaction (optionally blocking or failing first), and a
   * failed cut merges back underneath newer records.
   */
  private static final class FrozenCutTask implements Task<String> {

    private final List<String> live = new ArrayList<>();
    private final List<List<String>> persisted = new CopyOnWriteArrayList<>();
    private final List<Boolean> completions = new CopyOnWriteArrayList<>();
    private final List<String> processed = new CopyOnWriteArrayList<>();
    private final Map<String, Boolean> gateOpenWhenProcessed = new ConcurrentHashMap<>();
    private final AtomicInteger freezes = new AtomicInteger();
    private final AtomicBoolean gateOpened = new AtomicBoolean();
    private final List<Thread> freezeThreads = new CopyOnWriteArrayList<>();
    private final List<Thread> persistThreads = new CopyOnWriteArrayList<>();
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
    public long restore() {
      return 0L; // a real baseline, so materialization resumes rather than rebuilds
    }

    @Override
    public boolean needsCheckpoint() {
      return live.size() >= needsCheckpointAt;
    }

    @Override
    public CommitCut freezeCut(final long offset) {
      freezes.incrementAndGet();
      freezeThreads.add(Thread.currentThread());
      final List<String> cut = List.copyOf(live);
      live.clear();
      return new CommitCut() {
        @Override
        public void persist() {
          persistThreads.add(Thread.currentThread());
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
          // The shard's own atomic cut: the frozen delta and the barrier's offset in one
          // transaction the task owns.
          journal.add("tx-begin");
          journal.add("offset:" + offset);
          journal.add("persist");
          journal.add("tx-end");
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
    public void close() {
      closed = true;
    }
  }
}
