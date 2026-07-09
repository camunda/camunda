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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;

/**
 * The runtime-managed durability path through the dedicated cut persister: cuts, legacy suspended
 * commits and final stop commits of tasks that defer durability are all made durable by the one
 * writer thread — the single writer of the shared durable resources. Several queued cuts coalesce
 * into one shared transaction while completing individually, a legacy commit queued between cuts
 * runs alone with its own transaction (never coalesced), a failed coalesced transaction merges
 * every cut of the batch back for individual retry, and shutdown drains queued work before the
 * persister stops.
 *
 * <p>The tests inject a same-thread sink executor, so a frozen cut is enqueued on the persister
 * <em>inside</em> the actor's commit barrier — any record the task processes after the barrier is
 * therefore a reliable signal that its cut is already queued, which is what makes the coalescing
 * choreography deterministic.
 */
final class StreamRuntimeCutPersisterTest {

  private static final String TOPIC = "facts";

  private Consumer consumer;
  private EventBridgeClient client;

  private void stubClient() {
    consumer = mock(Consumer.class);
    client = mock(EventBridgeClient.class);
    when(client.subscribe(any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(consumer));
    when(consumer.sendHeartbeat()).thenReturn(CompletableFuture.completedFuture(null));
  }

  private List<String> stubCommittedOffsets(final List<String> journal) {
    final List<String> committed = new CopyOnWriteArrayList<>();
    when(consumer.commitOffset(any(), anyInt(), anyLong()))
        .thenAnswer(
            invocation -> {
              final int partition = invocation.getArgument(1);
              final long offset = invocation.getArgument(2);
              committed.add(partition + ":" + offset);
              journal.add("commitOffset:" + partition + ":" + offset);
              return CompletableFuture.completedFuture(null);
            });
    return committed;
  }

  private StreamRuntime<String> runtime(
      final IntFunction<Task<String>> taskFactory,
      final List<String> journal,
      final MeterRegistry meterRegistry,
      final AtomicBoolean failNextTransaction) {
    return runtime(taskFactory, journal, meterRegistry, failNextTransaction, null);
  }

  private StreamRuntime<String> runtime(
      final IntFunction<Task<String>> taskFactory,
      final List<String> journal,
      final MeterRegistry meterRegistry,
      final AtomicBoolean failNextTransaction,
      final List<String> transactionThreads) {
    return StreamRuntime.<String>builder()
        .client(client)
        .group("g")
        .instanceId("i")
        .sourceTopic(TOPIC)
        .deserializer((payload, partition, offset) -> new String(payload, StandardCharsets.UTF_8))
        .taskFactory(taskFactory)
        .transactionRunner(
            operations -> {
              if (transactionThreads != null) {
                transactionThreads.add(Thread.currentThread().getName());
              }
              journal.add("tx-begin");
              if (failNextTransaction != null && failNextTransaction.getAndSet(false)) {
                journal.add("tx-failed");
                throw new IllegalStateException("injected transaction failure");
              }
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
                journal.add("offset:" + partition + ":" + offset);
              }
            })
        // Same-thread sink executor: the enqueue on the persister happens inside the actor's
        // commit barrier, giving the tests a deterministic "cut is queued" signal (see class doc).
        .sinkExecutor(new SameThreadExecutorService())
        // Only the tasks' budget (needsCheckpoint) triggers cuts, so cut boundaries are exact.
        .commitInterval(Duration.ofHours(1))
        .meterRegistry(meterRegistry)
        .build();
  }

  private static Event event(final int partition, final long offset) {
    return new Event(offset, TOPIC, partition, ("e" + offset).getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void shouldCoalesceQueuedCutsOfDifferentPartitionsIntoOneTransaction() throws Exception {
    // given — partition 1's cut blocks the persister mid-batch while partitions 2 and 3 each
    // freeze a cut behind it; every task cuts after one record and marks enqueueing with a second
    stubClient();
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final List<String> journal = new CopyOnWriteArrayList<>();
    final ManagedCutTask task1 = new ManagedCutTask(1, journal);
    final ManagedCutTask task2 = new ManagedCutTask(2, journal);
    final ManagedCutTask task3 = new ManagedCutTask(3, journal);
    task1.needsCheckpointAt = 1;
    task1.persistGate = new CountDownLatch(1);
    task2.needsCheckpointAt = 1;
    task3.needsCheckpointAt = 1;
    final Map<Integer, ManagedCutTask> tasks = Map.of(1, task1, 2, task2, 3, task3);
    final AtomicBoolean gateDelivered = new AtomicBoolean();
    final AtomicBoolean othersReady = new AtomicBoolean();
    final AtomicBoolean othersDelivered = new AtomicBoolean();
    when(consumer.poll(anyInt(), any()))
        .thenAnswer(
            invocation -> {
              if (!gateDelivered.getAndSet(true)) {
                return List.of(event(1, 1));
              }
              if (othersReady.get() && !othersDelivered.getAndSet(true)) {
                return List.of(event(2, 1), event(2, 2), event(3, 1), event(3, 2));
              }
              return List.of();
            });
    final List<String> committed = stubCommittedOffsets(journal);
    final StreamRuntime<String> runtime = runtime(tasks::get, journal, registry, null);
    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();

    // when — the persister is pinned inside partition 1's transaction, then partitions 2 and 3
    // process their marker records, proving their cuts are queued behind it
    await().untilTrue(task1.persistEntered);
    othersReady.set(true);
    await().until(() -> task2.processed.contains("e2") && task3.processed.contains("e2"));
    task1.persistGate.countDown();

    // then — the persister drains both queued cuts as ONE transaction (both offsets and both
    // state deltas between one tx-begin/tx-end), publishes running before it in enqueue order
    await().until(() -> committed.contains("2:1") && committed.contains("3:1"));
    assertThat(journal.stream().filter("tx-begin"::equals)).hasSize(2);
    assertThat(transaction(journal, 1)).containsExactly("offset:1:1", "persist:1");
    assertThat(transaction(journal, 2))
        .containsExactlyInAnyOrder("offset:2:1", "persist:2", "offset:3:1", "persist:3");
    final int coalescedTxBegin = journal.lastIndexOf("tx-begin");
    assertThat(journal.indexOf("publish:2")).isLessThan(coalescedTxBegin);
    assertThat(journal.indexOf("publish:3")).isLessThan(coalescedTxBegin);

    // then — each cut still completed individually and successfully
    await()
        .until(
            () ->
                !task1.completions.isEmpty()
                    && !task2.completions.isEmpty()
                    && !task3.completions.isEmpty());
    assertThat(task1.completions).containsExactly(true);
    assertThat(task2.completions).containsExactly(true);
    assertThat(task3.completions).containsExactly(true);

    // then — only the coalesced cuts are counted, once each
    assertThat(coalescedCount(registry, 1)).isZero();
    assertThat(coalescedCount(registry, 2)).isEqualTo(1.0);
    assertThat(coalescedCount(registry, 3)).isEqualTo(1.0);

    runtime.stop();
    loop.join(TimeUnit.SECONDS.toMillis(5));
    assertThat(loop.isAlive()).isFalse();
  }

  @Test
  void shouldFailEveryCutOfACoalescedBatchAndRetryEachIndividually() throws Exception {
    // given — partitions 2 and 3 coalesce behind partition 1's gated cut, and the shared
    // transaction of their batch is injected to fail
    stubClient();
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final List<String> journal = new CopyOnWriteArrayList<>();
    final ManagedCutTask task1 = new ManagedCutTask(1, journal);
    final ManagedCutTask task2 = new ManagedCutTask(2, journal);
    final ManagedCutTask task3 = new ManagedCutTask(3, journal);
    task1.needsCheckpointAt = 1;
    task1.persistGate = new CountDownLatch(1);
    task2.needsCheckpointAt = 1;
    task3.needsCheckpointAt = 1;
    final Map<Integer, ManagedCutTask> tasks = Map.of(1, task1, 2, task2, 3, task3);
    final AtomicBoolean gateDelivered = new AtomicBoolean();
    final AtomicBoolean othersReady = new AtomicBoolean();
    final AtomicBoolean othersDelivered = new AtomicBoolean();
    final AtomicBoolean retryReady = new AtomicBoolean();
    final AtomicBoolean retryDelivered = new AtomicBoolean();
    when(consumer.poll(anyInt(), any()))
        .thenAnswer(
            invocation -> {
              if (!gateDelivered.getAndSet(true)) {
                return List.of(event(1, 1));
              }
              if (othersReady.get() && !othersDelivered.getAndSet(true)) {
                return List.of(event(2, 1), event(2, 2), event(3, 1), event(3, 2));
              }
              if (retryReady.get() && !retryDelivered.getAndSet(true)) {
                return List.of(event(2, 3), event(3, 3));
              }
              return List.of();
            });
    final List<String> committed = stubCommittedOffsets(journal);
    final AtomicBoolean failNextTransaction = new AtomicBoolean();
    final StreamRuntime<String> runtime =
        runtime(tasks::get, journal, registry, failNextTransaction);
    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();

    // when — both cuts are queued behind the gate, and the batch transaction is set to fail
    // (partition 1's transaction is already past the failure check, blocked inside its persist)
    await().untilTrue(task1.persistEntered);
    othersReady.set(true);
    await().until(() -> task2.processed.contains("e2") && task3.processed.contains("e2"));
    failNextTransaction.set(true);
    task1.persistGate.countDown();

    // then — one transaction, one fate: both cuts of the failed batch merge back on their actors
    await()
        .until(
            () ->
                task1.completions.contains(true)
                    && task2.completions.contains(false)
                    && task3.completions.contains(false));
    assertThat(journal).contains("tx-failed");
    assertThat(task1.completions).containsExactly(true);

    // when — the next record re-triggers each partition's budget barrier
    retryReady.set(true);

    // then — both retry individually, and every record lands durably exactly once
    await().until(() -> committed.contains("2:3") && committed.contains("3:3"));
    assertThat(task2.completions).containsExactly(false, true);
    assertThat(task3.completions).containsExactly(false, true);
    assertThat(task2.persisted.stream().flatMap(List::stream)).containsExactly("e1", "e2", "e3");
    assertThat(task3.persisted.stream().flatMap(List::stream)).containsExactly("e1", "e2", "e3");
    assertThat(retryCount(registry, 2)).isEqualTo(1.0);
    assertThat(retryCount(registry, 3)).isEqualTo(1.0);

    runtime.stop();
    loop.join(TimeUnit.SECONDS.toMillis(5));
    assertThat(loop.isAlive()).isFalse();
  }

  @Test
  void shouldDrainQueuedCutsOnShutdown() throws Exception {
    // given — partition 2's cut is queued behind partition 1's gated cut when the stop arrives
    stubClient();
    final List<String> journal = new CopyOnWriteArrayList<>();
    final ManagedCutTask task1 = new ManagedCutTask(1, journal);
    final ManagedCutTask task2 = new ManagedCutTask(2, journal);
    task1.needsCheckpointAt = 1;
    task1.persistGate = new CountDownLatch(1);
    task2.needsCheckpointAt = 1;
    final Map<Integer, ManagedCutTask> tasks = Map.of(1, task1, 2, task2);
    final AtomicBoolean gateDelivered = new AtomicBoolean();
    final AtomicBoolean othersReady = new AtomicBoolean();
    final AtomicBoolean othersDelivered = new AtomicBoolean();
    when(consumer.poll(anyInt(), any()))
        .thenAnswer(
            invocation -> {
              if (!gateDelivered.getAndSet(true)) {
                return List.of(event(1, 1));
              }
              if (othersReady.get() && !othersDelivered.getAndSet(true)) {
                return List.of(event(2, 1), event(2, 2));
              }
              return List.of();
            });
    final List<String> committed = stubCommittedOffsets(journal);
    final StreamRuntime<String> runtime = runtime(tasks::get, journal, null, null);
    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();
    await().untilTrue(task1.persistEntered);
    othersReady.set(true);
    await().until(() -> task2.processed.contains("e2"));

    // when — stop while one cut is being persisted and another is queued, then release the gate
    runtime.stop();
    task1.persistGate.countDown();

    // then — the queued cut is drained and completes, both actors finish their final stop
    // commit (partition 2's runs as a joined job on the persister), and the runtime shuts down
    loop.join(TimeUnit.SECONDS.toMillis(10));
    assertThat(loop.isAlive()).isFalse();
    assertThat(task1.completions).containsExactly(true);
    assertThat(task2.completions).containsExactly(true);
    // partition 2: the queued cut carried e1; the marker e2 landed in the final stop commit
    assertThat(task2.persisted.stream().flatMap(List::stream)).containsExactly("e1", "e2");
    assertThat(journal).containsSubsequence("persist:2", "checkpoint:2");
    assertThat(committed).contains("1:1", "2:1", "2:2");
    assertThat(task1.closed).isTrue();
    assertThat(task2.closed).isTrue();
  }

  @Test
  void shouldRunALegacyManagedCommitAndAFrozenBatchAsDisjointTransactionsInEnqueueOrder()
      throws Exception {
    // given — partition 4's task has no frozen-cut support, so its commit suspends the actor and
    // runs the full legacy sequence as an exclusive job on the persister thread; the job is gated
    // inside its own transaction (at checkpoint) while partition 2 freezes a cut behind it
    stubClient();
    final List<String> journal = new CopyOnWriteArrayList<>();
    final LegacyManagedTask task4 = new LegacyManagedTask(4, journal);
    final ManagedCutTask task2 = new ManagedCutTask(2, journal);
    task4.needsCheckpointAt = 1;
    task4.checkpointGate = new CountDownLatch(1);
    task2.needsCheckpointAt = 1;
    final Map<Integer, Task<String>> tasks = Map.of(4, task4, 2, task2);
    final AtomicBoolean gateDelivered = new AtomicBoolean();
    final AtomicBoolean othersReady = new AtomicBoolean();
    final AtomicBoolean othersDelivered = new AtomicBoolean();
    when(consumer.poll(anyInt(), any()))
        .thenAnswer(
            invocation -> {
              if (!gateDelivered.getAndSet(true)) {
                return List.of(event(4, 1));
              }
              if (othersReady.get() && !othersDelivered.getAndSet(true)) {
                return List.of(event(2, 1), event(2, 2));
              }
              return List.of();
            });
    final List<String> committed = stubCommittedOffsets(journal);
    final StreamRuntime<String> runtime = runtime(tasks::get, journal, null, null);
    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();

    // when — the persister is pinned inside the legacy job's transaction, partition 2's cut is
    // queued behind it (proven by its marker record), then the gate opens
    await().untilTrue(task4.checkpointEntered);
    othersReady.set(true);
    await().until(() -> task2.processed.contains("e2"));
    task4.checkpointGate.countDown();

    // then — two disjoint transactions in enqueue order: the legacy job's own transaction first
    // (never coalesced into a frozen batch's), the frozen cut's shared transaction second, with
    // the legacy job fully complete (including its joined source-offset commit) before the batch
    // even publishes
    await().until(() -> committed.contains("4:1") && committed.contains("2:1"));
    assertThat(transaction(journal, 1)).containsExactly("offset:4:1", "checkpoint:4");
    assertThat(transaction(journal, 2)).containsExactly("offset:2:1", "persist:2");
    assertThat(journal).containsSubsequence("commitOffset:4:1", "publish:2");

    // then — the frozen cut completed normally behind the legacy job
    await().until(() -> !task2.completions.isEmpty());
    assertThat(task2.completions).containsExactly(true);

    runtime.stop();
    loop.join(TimeUnit.SECONDS.toMillis(5));
    assertThat(loop.isAlive()).isFalse();
  }

  @Test
  void shouldRunFinalStopCommitsOnThePersisterThreadAfterQueuedCuts() throws Exception {
    // given — partition 2's cut is queued behind partition 1's gated cut when the stop arrives,
    // and partition 2 has folded past its cut's barrier (so its stop leaves pending work)
    stubClient();
    final List<String> journal = new CopyOnWriteArrayList<>();
    final List<String> transactionThreads = new CopyOnWriteArrayList<>();
    final ManagedCutTask task1 = new ManagedCutTask(1, journal);
    final ManagedCutTask task2 = new ManagedCutTask(2, journal);
    task1.needsCheckpointAt = 1;
    task1.persistGate = new CountDownLatch(1);
    task2.needsCheckpointAt = 1;
    final Map<Integer, ManagedCutTask> tasks = Map.of(1, task1, 2, task2);
    final AtomicBoolean gateDelivered = new AtomicBoolean();
    final AtomicBoolean othersReady = new AtomicBoolean();
    final AtomicBoolean othersDelivered = new AtomicBoolean();
    when(consumer.poll(anyInt(), any()))
        .thenAnswer(
            invocation -> {
              if (!gateDelivered.getAndSet(true)) {
                return List.of(event(1, 1));
              }
              if (othersReady.get() && !othersDelivered.getAndSet(true)) {
                return List.of(event(2, 1), event(2, 2));
              }
              return List.of();
            });
    final List<String> committed = stubCommittedOffsets(journal);
    final StreamRuntime<String> runtime =
        runtime(tasks::get, journal, null, null, transactionThreads);
    final Thread loop = new Thread(runtime::run, "runtime-under-test");
    loop.start();
    await().untilTrue(task1.persistEntered);
    othersReady.set(true);
    await().until(() -> task2.processed.contains("e2"));

    // when — stop while one cut is being persisted and another is queued, then release the gate
    runtime.stop();
    task1.persistGate.countDown();

    // then — partition 2's stop commit runs as a job AFTER its queued cut (enqueue order held:
    // the stop job is only submitted once the cut completed), and everything completes
    loop.join(TimeUnit.SECONDS.toMillis(10));
    assertThat(loop.isAlive()).isFalse();
    assertThat(journal).containsSubsequence("persist:1", "persist:2", "checkpoint:2");
    assertThat(task1.completions).containsExactly(true);
    assertThat(task2.completions).containsExactly(true);
    assertThat(committed).contains("1:1", "2:1", "2:2");
    assertThat(task1.closed).isTrue();
    assertThat(task2.closed).isTrue();

    // then — every shared transaction (both cuts and the stop commit) ran on the persister
    // thread: the single writer of the shared durable resources, with no lock anywhere
    assertThat(transactionThreads).hasSize(3);
    assertThat(transactionThreads).allMatch(("eb-cut-persister-" + TOPIC)::equals);
  }

  /** The operations journaled between the {@code n}-th tx-begin and its matching tx-end. */
  private static List<String> transaction(final List<String> journal, final int n) {
    final List<String> snapshot = List.copyOf(journal);
    int seen = 0;
    for (int i = 0; i < snapshot.size(); i++) {
      if ("tx-begin".equals(snapshot.get(i)) && ++seen == n) {
        final List<String> tail = snapshot.subList(i + 1, snapshot.size());
        return tail.subList(0, tail.indexOf("tx-end"));
      }
    }
    throw new AssertionError("no transaction " + n + " in " + snapshot);
  }

  private static double coalescedCount(final SimpleMeterRegistry registry, final int partition) {
    return registry
        .get("eb.streaming.cut.coalesced")
        .tag("partition", Integer.toString(partition))
        .counter()
        .count();
  }

  private static double retryCount(final SimpleMeterRegistry registry, final int partition) {
    return registry
        .get("eb.streaming.cut.retries")
        .tag("partition", Integer.toString(partition))
        .counter()
        .count();
  }

  /**
   * A per-partition task deferring durability to the runtime: a freeze steals the live working set
   * into an immutable cut whose publish/persist write a partition-tagged journal, persist
   * optionally blocks on a gate, and a failed cut merges back underneath newer records.
   */
  private static final class ManagedCutTask implements Task<String> {

    private final int partitionId;
    private final List<String> journal;
    private final List<String> live = new ArrayList<>();
    private final List<List<String>> persisted = new CopyOnWriteArrayList<>();
    private final List<Boolean> completions = new CopyOnWriteArrayList<>();
    private final List<String> processed = new CopyOnWriteArrayList<>();
    private final AtomicInteger freezes = new AtomicInteger();
    private final AtomicBoolean persistEntered = new AtomicBoolean();
    private volatile CountDownLatch persistGate;
    private volatile int needsCheckpointAt = Integer.MAX_VALUE;
    private volatile boolean closed;

    private ManagedCutTask(final int partitionId, final List<String> journal) {
      this.partitionId = partitionId;
      this.journal = journal;
    }

    @Override
    public void process(final String record) {
      live.add(record);
      processed.add(record);
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
        public void publish() {
          journal.add("publish:" + partitionId);
        }

        @Override
        public void persist() {
          persistEntered.set(true);
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
          journal.add("persist:" + partitionId);
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
      // The final synchronous stop commit on the direct path: persist the live working set.
      if (!live.isEmpty()) {
        journal.add("checkpoint:" + partitionId);
        persisted.add(List.copyOf(live));
        live.clear();
      }
    }

    @Override
    public void close() {
      closed = true;
    }
  }

  /**
   * A per-partition task deferring durability to the runtime <em>without</em> frozen-cut support:
   * its commits suspend the actor and run the full legacy sequence as an exclusive job on the
   * persister thread. The checkpoint journals with a partition tag and optionally blocks on a gate
   * — pinning the persister <em>inside</em> the legacy job's own transaction.
   */
  private static final class LegacyManagedTask implements Task<String> {

    private final int partitionId;
    private final List<String> journal;
    private final List<String> live = new ArrayList<>();
    private final List<String> processed = new CopyOnWriteArrayList<>();
    private final AtomicBoolean checkpointEntered = new AtomicBoolean();
    private volatile CountDownLatch checkpointGate;
    private volatile int needsCheckpointAt = Integer.MAX_VALUE;

    private LegacyManagedTask(final int partitionId, final List<String> journal) {
      this.partitionId = partitionId;
      this.journal = journal;
    }

    @Override
    public void process(final String record) {
      live.add(record);
      processed.add(record);
    }

    @Override
    public boolean needsCheckpoint() {
      return live.size() >= needsCheckpointAt;
    }

    @Override
    public void checkpoint() {
      checkpointEntered.set(true);
      final CountDownLatch gate = checkpointGate;
      if (gate != null) {
        try {
          if (!gate.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("checkpoint gate never opened");
          }
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(e);
        }
      }
      if (!live.isEmpty()) {
        journal.add("checkpoint:" + partitionId);
        live.clear();
      }
    }
  }

  /**
   * Runs every submitted job on the caller's thread. Injected as the sink executor so a frozen
   * cut's hand-off to the persister happens inside the actor's commit barrier — cheap and
   * non-blocking there by design — making the tests' enqueue-order choreography deterministic.
   */
  private static final class SameThreadExecutorService extends AbstractExecutorService {

    private volatile boolean shutdown;

    @Override
    public void execute(final Runnable command) {
      command.run();
    }

    @Override
    public void shutdown() {
      shutdown = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
      shutdown = true;
      return List.of();
    }

    @Override
    public boolean isShutdown() {
      return shutdown;
    }

    @Override
    public boolean isTerminated() {
      return shutdown;
    }

    @Override
    public boolean awaitTermination(final long timeout, final TimeUnit unit) {
      return shutdown;
    }
  }
}
