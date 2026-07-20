/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.changelog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.eventbridge.client.Consumer;
import io.camunda.eventbridge.client.ConsumerNotRegisteredException;
import io.camunda.eventbridge.client.Event;
import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.client.RebalanceListener;
import io.camunda.eventbridge.client.TopicPartition;
import io.camunda.eventbridge.streaming.CommitCut;
import io.camunda.eventbridge.streaming.StreamRuntime;
import io.camunda.eventbridge.streaming.Task;
import io.camunda.eventbridge.streaming.state.TestColumnFamilies;
import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.state.api.StateStoreProvider;
import io.camunda.eventbridge.streaming.state.rocksdb.RocksDbStateStoreProvider;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The failover milestone's proof: TWO {@link StreamRuntime} instances (same JVM, one thread each,
 * mocked {@link Consumer}/{@link EventBridgeClient} — the same harness style as {@link
 * io.camunda.eventbridge.streaming.StreamRuntimeRebalanceTest}) sharing a fake source topic and a
 * real {@link FakeChangelogBroker}, exercising the role-aware dispatch wired into {@link
 * io.camunda.eventbridge.streaming.internals.SourceLoop} end to end: a member reacts to {@link
 * RebalanceListener#onStandbyPartitionsAssigned}/{@link RebalanceListener#onPartitionsAssigned} by
 * opening (or promoting) its {@link PartitionRoleController}, never by tearing down and rebuilding
 * a task from scratch.
 *
 * <p>Ownership is driven directly through each member's captured {@link RebalanceListener} — this
 * test IS the coordinator for the purposes of the assignment signal, exactly as {@code
 * StreamRuntimeRebalanceTest} drives a single member's listener directly. Fencing (streaming ADR
 * 0009 §4) is modelled by {@link FakeSourceBroker#fence()} plus each member's mocked {@code
 * commitOffset} rejecting once its captured epoch is behind.
 */
final class StreamRuntimeStandbyFailoverIT {

  private static final String SOURCE_TOPIC = "source";
  private static final String CHANGELOG_TOPIC = "changelog";
  private static final int PARTITION = 0;
  private static final TopicPartition TP = new TopicPartition(SOURCE_TOPIC, PARTITION);

  @TempDir Path tempDir;

  private final FakeSourceBroker sourceBroker = new FakeSourceBroker();
  private final FakeChangelogBroker changelogBroker = new FakeChangelogBroker();
  private final List<Member> members = new ArrayList<>();

  @AfterEach
  void tearDown() {
    members.forEach(Member::stop);
  }

  @Test
  void shouldPromoteAWarmedStandbyAndResumeFromTheLastMarkerWithoutReplayingFromZero()
      throws Exception {
    // given — A is active and commits three records
    final Member a = member("a");
    final Member b = member("b");
    a.start();
    b.start();
    a.assignActive();
    feed(3);
    await().atMost(Duration.ofSeconds(10)).until(() -> a.processedCount() == 3);
    await().atMost(Duration.ofSeconds(10)).until(() -> a.durableOffset() == 2L);

    // and — B warms as a standby while A keeps working, reaching readiness
    b.assignStandby();
    await().atMost(Duration.ofSeconds(10)).until(() -> b.standbyReadiness() == 0L);
    assertThat(b.role()).isEqualTo(PartitionRoleController.Role.STANDBY);
    // B's store already matches A's at this point — the byte-equivalence property (ADR 0009
    // decision 6) — captured now as the reference for "what A had at the marker"
    final Map<String, String> aStoreAtMarker = a.cellsSnapshot();
    assertThat(b.cellsSnapshot()).isEqualTo(aStoreAtMarker);
    // snapshot how many fetches happened before promotion, so later assertions can look only at
    // what happens FROM HERE ON — a fresh join legitimately requests position 0 once, but nothing
    // after this point should ever request it again (that would mean a spurious cold restart)
    final int fetchesBeforePromotion = changelogBroker.fetchPositionsRequested().size();

    // when — A "dies" (never revoked) and B is promoted
    sourceBroker.fence();
    b.captureEpoch(sourceBroker.ownerEpoch());
    b.assignActivePromoting();

    // then — B resumes the source at A's last marker + 1: two new records land, folded exactly
    // once, never replaying the three A already committed
    feed(2);
    await().atMost(Duration.ofSeconds(10)).until(() -> b.processedCount() == 2);
    // give any accidental replay a chance to show up before asserting the ceiling
    await()
        .during(Duration.ofMillis(300))
        .atMost(Duration.ofSeconds(10))
        .until(() -> b.processedCount() == 2);
    await().atMost(Duration.ofSeconds(10)).until(() -> b.durableOffset() == 4L);

    // and — no changelog fetch since the promotion ever re-requested position 0 (no replay from the
    // start): the applier resumed exactly where its own warming left off
    final List<Long> positionsSincePromotion =
        changelogBroker
            .fetchPositionsRequested()
            .subList(fetchesBeforePromotion, changelogBroker.fetchPositionsRequested().size());
    assertThat(positionsSincePromotion).doesNotContain(0L);

    // and — B's store content is exactly what A had at the marker, plus exactly the two new rows
    // (never touching A's own copy again: A is left running, un-revoked, so this compares against
    // the snapshot captured BEFORE promotion rather than A's current state, which a live zombie may
    // keep changing independently)
    final Map<String, String> bAfterPromotion = b.cellsSnapshot();
    assertThat(bAfterPromotion).containsAllEntriesOf(aStoreAtMarker);
    assertThat(bAfterPromotion).hasSize(aStoreAtMarker.size() + 2);
  }

  @Test
  void shouldWarmAnEmptyStandbyFromTheChangelogStartWithoutDisturbingTheActive() throws Exception {
    // given — A is active and has already committed a few cuts before B ever joins
    final Member a = member("a");
    a.start();
    a.assignActive();
    feed(4);
    await().atMost(Duration.ofSeconds(10)).until(() -> a.processedCount() == 4);
    final long aOffsetBeforeWarming = a.durableOffset();

    // when — an empty B joins as a standby
    final Member b = member("b");
    b.start();
    b.assignStandby();

    // then — B reaches readiness by replaying the changelog from its start
    await().atMost(Duration.ofSeconds(10)).until(() -> b.standbyReadiness() == 0L);
    assertThat(b.cellsSnapshot()).isEqualTo(a.cellsSnapshot());

    // and — A was not disturbed: it keeps committing new records the whole time
    feed(2);
    await().atMost(Duration.ofSeconds(10)).until(() -> a.processedCount() == 6);
    assertThat(a.durableOffset()).isGreaterThan(aOffsetBeforeWarming);

    // and — a subsequent handover to the now-warm B succeeds
    await().atMost(Duration.ofSeconds(10)).until(() -> b.standbyReadiness() == 0L);
    sourceBroker.fence();
    b.captureEpoch(sourceBroker.ownerEpoch());
    b.assignActivePromoting();
    feed(1);
    await().atMost(Duration.ofSeconds(10)).until(() -> b.processedCount() >= 1);
  }

  @Test
  void shouldLeaveThePartitionUnpromotedWhileTheStandbyIsNotYetReady() throws Exception {
    // given — A commits a sizeable backlog before B ever joins, so B's warm-up is not instantaneous
    final Member a = member("a");
    a.start();
    a.assignActive();
    feed(20);
    await().atMost(Duration.ofSeconds(10)).until(() -> a.processedCount() == 20);

    final Member b = member("b");
    b.start();
    b.assignStandby();

    // when — A dies (its process terminates outright; unlike the other scenarios' zombie window,
    // there is nothing left to hand ownership to B from)
    a.stop();

    // then — as long as this test (standing in for the coordinator's ready-only promotion policy —
    // BalancedStickyAssignorTest already covers that policy at the assignor layer; this IT covers
    // only the runtime's half of the contract) never sends B an ACTIVE assignment, the runtime
    // never
    // promotes on its own: B stays STANDBY and folds nothing, even once it is fully caught up
    await().atMost(Duration.ofSeconds(10)).until(() -> b.standbyReadiness() == 0L);
    assertThat(b.role()).isEqualTo(PartitionRoleController.Role.STANDBY);
    assertThat(b.processedCount()).isZero();

    // when the harness (the assignor's decision) finally promotes the now-ready B
    sourceBroker.fence();
    b.captureEpoch(sourceBroker.ownerEpoch());
    b.assignActivePromoting();
    feed(1);

    // then the handover succeeds and processing resumes
    await().atMost(Duration.ofSeconds(10)).until(() -> b.processedCount() >= 1);
  }

  @Test
  void shouldHaltAZombieActiveOnceItsNextOffsetCommitIsFencedAfterHandover() throws Exception {
    // given — A is active and B is a caught-up warm standby
    final Member a = member("a");
    final Member b = member("b");
    a.start();
    b.start();
    a.assignActive();
    feed(1);
    await().atMost(Duration.ofSeconds(10)).until(() -> a.processedCount() == 1);
    await().atMost(Duration.ofSeconds(10)).until(() -> a.durableOffset() == 0L);
    b.assignStandby();
    await().atMost(Duration.ofSeconds(10)).until(() -> b.standbyReadiness() == 0L);

    // From here on, treat any further commit attempt from A as fenced — deterministically, not by
    // racing this test thread against A's own commit timer (which could otherwise durably commit
    // the next record before this test ever gets to call fence() below; see the method's javadoc)
    a.forceFutureCommitsToFail();

    // and — A folds one more record it will now never manage to commit
    feed(1);
    await().atMost(Duration.ofSeconds(10)).until(() -> a.processedCount() == 2);

    // when — B is promoted (A is never told it is revoked: this is the "zombie" window the ADR
    // accepts) and the fence takes effect
    sourceBroker.fence();
    b.captureEpoch(sourceBroker.ownerEpoch());
    b.assignActivePromoting();

    // then — A's still-running shard discovers the fence on its next offset commit and halts (its
    // task closes even though this test never revoked it — the only path that can do that)
    assertThat(a.awaitClosed(10, TimeUnit.SECONDS))
        .as("A's shard halted and closed after the fenced commit rejection")
        .isTrue();
  }

  // -------------------------------------------------------------------------------------------

  private void feed(final int count) {
    for (int i = 0; i < count; i++) {
      sourceBroker.append(("r" + sourceBroker.size()).getBytes(StandardCharsets.UTF_8));
    }
  }

  private Member member(final String id) {
    final Member member = new Member(id);
    members.add(member);
    return member;
  }

  /** One member: its own {@link StreamRuntime}, mocked client/consumer, and observability hooks. */
  private final class Member {

    private final String id;
    private final AtomicInteger processedCount = new AtomicInteger();
    private final AtomicLong myEpoch = new AtomicLong(0);
    private final AtomicLong pollCursor = new AtomicLong(0);
    // Whether THIS member's own consumer believes it owns the partition as ACTIVE — set only by
    // this member's own onPartitionsAssigned, exactly like a real Consumer's local membership
    // state. Deliberately never cleared by a peer's promotion: a zombie's own poll() keeps
    // delivering records after a handover it was never told about (only its commitOffset is
    // membership-fenced) — the behavior streaming ADR 0009 §4's halt discipline exists for.
    private final AtomicBoolean ownsActive = new AtomicBoolean(false);
    // Flipped by forceFutureCommitsToFail() — a plain field read inside the (one-time) commitOffset
    // stub, never a Mockito re-stub: re-stubbing a mock while the runtime's own threads are
    // actively
    // calling it concurrently is a genuine Mockito hazard (WrongTypeOfReturnValue), not just a
    // style
    // preference — see that method's javadoc.
    private final AtomicBoolean forceCommitsToFail = new AtomicBoolean(false);
    private final AtomicReference<RebalanceListener> listener = new AtomicReference<>();
    private final Map<Integer, PartitionRoleController<?, TestRecord>> controllers =
        new HashMap<>();
    private final CountDownLatch closed = new CountDownLatch(1);
    private final Consumer consumer = mock(Consumer.class);
    private StateStoreProvider<TestColumnFamilies> provider;
    private Thread thread;
    private volatile StreamRuntime<TestRecord> runtime;

    private Member(final String id) {
      this.id = id;
    }

    void start() throws InterruptedException {
      final EventBridgeClient client = mock(EventBridgeClient.class);
      when(client.subscribe(any(), any(), any()))
          .thenReturn(CompletableFuture.completedFuture(consumer));
      when(consumer.sendHeartbeat()).thenReturn(CompletableFuture.completedFuture(null));
      when(consumer.memberEpoch()).thenAnswer(inv -> myEpoch.get());
      doAnswer(
              inv -> {
                listener.set(inv.getArgument(0));
                return null;
              })
          .when(consumer)
          .rebalanceListener(any());
      doAnswer(
              inv -> {
                pollCursor.set(0);
                return null;
              })
          .when(consumer)
          .seekToBeginning(any());
      when(consumer.poll(anyInt(), any()))
          .thenAnswer(
              inv -> {
                if (!ownsActive.get()) {
                  return List.<Event>of();
                }
                final int max = inv.getArgument(0);
                final long from = pollCursor.get();
                final List<byte[]> raw = sourceBroker.from(from, max);
                final List<Event> events = new ArrayList<>();
                for (int i = 0; i < raw.size(); i++) {
                  events.add(new Event(from + i, SOURCE_TOPIC, PARTITION, raw.get(i)));
                }
                pollCursor.addAndGet(raw.size());
                return events;
              });
      when(consumer.commitOffset(eq(SOURCE_TOPIC), eq(PARTITION), anyLong()))
          .thenAnswer(
              inv -> {
                if (forceCommitsToFail.get() || myEpoch.get() != sourceBroker.ownerEpoch()) {
                  return CompletableFuture.failedFuture(
                      new ConsumerNotRegisteredException("g", id));
                }
                return CompletableFuture.completedFuture(null);
              });

      provider =
          RocksDbStateStoreProvider.<TestColumnFamilies>open(
              new File(tempDir.toFile(), id), new SimpleMeterRegistry());

      runtime =
          StreamRuntime.<TestRecord>builder()
              .client(client)
              .group("g")
              .instanceId(id)
              .sourceTopic(SOURCE_TOPIC)
              .deserializer((payload, partition, offset) -> new TestRecord(offset, payload))
              .taskFactory(
                  (partition, epoch) -> {
                    throw new AssertionError(
                        "taskFactory must not be used once a roleControllerFactory is configured");
                  })
              .roleControllerFactory(this::startAsStandby)
              .maxPoll(100)
              .pollTimeout(Duration.ofMillis(30))
              .commitInterval(Duration.ofMillis(50))
              .punctuationInterval(Duration.ofMillis(20))
              .errorBackoff(Duration.ofMillis(50))
              .processorThreads(1)
              .sinkIoThreads(1)
              .build();
      thread = new Thread(runtime::run, "runtime-" + id);
      thread.start();
      await().atMost(Duration.ofSeconds(5)).until(() -> listener.get() != null);
    }

    void stop() {
      if (runtime != null) {
        runtime.stop();
      }
      if (thread != null) {
        try {
          thread.join(TimeUnit.SECONDS.toMillis(5));
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    }

    void assignActive() {
      ownsActive.set(true);
      listener.get().onPartitionsAssigned(List.of(TP));
    }

    /** A promotion arrives over the exact same signal as a cold active assignment. */
    void assignActivePromoting() {
      ownsActive.set(true);
      listener.get().onPartitionsAssigned(List.of(TP));
    }

    void assignStandby() {
      listener.get().onStandbyPartitionsAssigned(List.of(TP));
    }

    void captureEpoch(final long epoch) {
      myEpoch.set(epoch);
    }

    /**
     * Deterministically makes this member's {@code commitOffset} reject as fenced from this point
     * on, standing in for "a successor has taken over and the coordinator will reject this member's
     * next commit" — a plain flag flip read inside the (one-time) mock stub, not a Mockito re-stub:
     * re-stubbing a mock while the runtime's own threads are actively invoking it concurrently is a
     * genuine Mockito hazard, not just unnecessary. {@link #captureEpoch} plus {@link
     * FakeSourceBroker#fence()} alone cannot guarantee this test calls them before the racing actor
     * thread's own next commit attempt (a commit already in flight when fencing happens is a real,
     * unavoidable race in any such system) — this method makes THIS test's assertion about a
     * *subsequent* commit deterministic instead of depending on that race.
     */
    void forceFutureCommitsToFail() {
      forceCommitsToFail.set(true);
    }

    int processedCount() {
      return processedCount.get();
    }

    long durableOffset() {
      return readSourceOffset(provider);
    }

    long standbyReadiness() {
      final PartitionRoleController<?, TestRecord> controller = controllers.get(PARTITION);
      return controller == null ? Long.MAX_VALUE : controller.standbyReadiness();
    }

    PartitionRoleController.Role role() {
      final PartitionRoleController<?, TestRecord> controller = controllers.get(PARTITION);
      return controller == null ? null : controller.role();
    }

    boolean awaitClosed(final long timeout, final TimeUnit unit) throws InterruptedException {
      return closed.await(timeout, unit);
    }

    /**
     * A stable, comparable snapshot of this member's CELLS column family: hex(key) -> hex(value).
     */
    Map<String, String> cellsSnapshot() {
      final KeyValueStore<DbBytes, DbBytes> store =
          provider.keyValueStore(TestColumnFamilies.CELLS, new DbBytes(), new DbBytes());
      final Map<String, String> snapshot = new TreeMap<>();
      store.forEach(
          (k, v) ->
              snapshot.put(
                  HexFormat.of().formatHex(k.getBytes()), HexFormat.of().formatHex(v.getBytes())));
      return snapshot;
    }

    private PartitionRoleController<?, TestRecord> startAsStandby(final int partition) {
      final ChangelogPublisher publisher =
          new ChangelogPublisher(changelogBroker.client, CHANGELOG_TOPIC, partition);
      final var controller =
          PartitionRoleController.<TestColumnFamilies, TestRecord>startAsStandby(
              provider,
              p -> new RecordingTask(p, publisher, processedCount, closed),
              p ->
                  ChangelogApplier.singleColumnFamily(
                      changelogBroker.client,
                      CHANGELOG_TOPIC,
                      partition,
                      p,
                      TestColumnFamilies.CELLS,
                      () -> readChangelogPosition(p),
                      cut -> {
                        writeSourceOffset(p, cut.sourceOffset());
                        writeChangelogPosition(p, cut.changelogPosition());
                      }));
      controllers.put(partition, controller);
      return controller;
    }
  }

  /**
   * A decoded source record carrying its own source offset — the row key {@link RecordingTask}
   * uses.
   */
  private record TestRecord(long offset, byte[] payload) {}

  /**
   * A minimal changelog-backed {@link Task}: each processed record becomes one keyed row (the row
   * key is the record's own source offset, big-endian), published to the changelog then persisted
   * into the CELLS column family in the same transaction as the durable offset — the same shape
   * {@link ProjectionStageTask}/{@link io.camunda.analytics.pipeline.stage.AggregationStageTask}
   * follow, stripped to the minimum this IT needs. Never closes its provider: that is the owning
   * {@link PartitionRoleController}'s job.
   */
  private static final class RecordingTask implements Task<TestRecord> {

    private final StateStoreProvider<TestColumnFamilies> provider;
    private final ChangelogPublisher publisher;
    private final AtomicInteger processedCount;
    private final CountDownLatch closedLatch;
    private final List<TestRecord> pending = new ArrayList<>();

    private RecordingTask(
        final StateStoreProvider<TestColumnFamilies> provider,
        final ChangelogPublisher publisher,
        final AtomicInteger processedCount,
        final CountDownLatch closedLatch) {
      this.provider = provider;
      this.publisher = publisher;
      this.processedCount = processedCount;
      this.closedLatch = closedLatch;
    }

    @Override
    public void process(final TestRecord record) {
      pending.add(record);
      processedCount.incrementAndGet();
    }

    @Override
    public long restore() {
      return readSourceOffset(provider);
    }

    @Override
    public CommitCut freezeCut(final long offset) {
      final List<TestRecord> rows = List.copyOf(pending);
      pending.clear();
      return new CommitCut() {
        private long changelogPosition = -1L;

        @Override
        public void publish() {
          final List<ChangelogRecord> records = new ArrayList<>();
          for (final TestRecord row : rows) {
            records.add(ChangelogRecord.put(rowKey(row.offset()), row.payload()));
          }
          changelogPosition = publisher.publish(records, offset);
        }

        @Override
        public void persist() {
          provider.runInTransaction(
              () -> {
                final KeyValueStore<DbBytes, DbBytes> store =
                    provider.keyValueStore(TestColumnFamilies.CELLS, new DbBytes(), new DbBytes());
                for (final TestRecord row : rows) {
                  final DbBytes key = new DbBytes();
                  key.wrapBytes(rowKey(row.offset()));
                  final DbBytes value = new DbBytes();
                  value.wrapBytes(row.payload());
                  store.put(key, value);
                }
                writeSourceOffset(provider, offset);
                writeChangelogPosition(provider, changelogPosition);
              });
        }

        @Override
        public void complete(final boolean success) {
          if (!success) {
            pending.addAll(0, rows);
          }
        }
      };
    }

    @Override
    public void close() {
      closedLatch.countDown();
    }

    // closeKeepingStores() is not overridden: this task owns nothing besides the provider, which it
    // never closes either way (the controller alone owns that), so the default (delegating to
    // close()) is already correct.
  }

  private static byte[] rowKey(final long offset) {
    return ByteBuffer.allocate(Long.BYTES).putLong(offset).array();
  }

  private static long readSourceOffset(final StateStoreProvider<TestColumnFamilies> provider) {
    return readOffsetsSlot(provider, 0);
  }

  private static void writeSourceOffset(
      final StateStoreProvider<TestColumnFamilies> provider, final long offset) {
    writeOffsetsSlot(provider, 0, offset);
  }

  private static long readChangelogPosition(final StateStoreProvider<TestColumnFamilies> provider) {
    final long value = readOffsetsSlot(provider, 1);
    return value == Task.NO_OFFSET ? ChangelogApplier.NO_POSITION : value;
  }

  private static void writeChangelogPosition(
      final StateStoreProvider<TestColumnFamilies> provider, final long position) {
    writeOffsetsSlot(provider, 1, position);
  }

  private static long readOffsetsSlot(
      final StateStoreProvider<TestColumnFamilies> provider, final int slot) {
    final KeyValueStore<DbInt, DbLong> store =
        provider.keyValueStore(TestColumnFamilies.OFFSETS, new DbInt(), new DbLong());
    final DbInt key = new DbInt();
    key.wrapInt(slot);
    return store.get(key).map(DbLong::getValue).orElse(Task.NO_OFFSET);
  }

  private static void writeOffsetsSlot(
      final StateStoreProvider<TestColumnFamilies> provider, final int slot, final long value) {
    final KeyValueStore<DbInt, DbLong> store =
        provider.keyValueStore(TestColumnFamilies.OFFSETS, new DbInt(), new DbLong());
    final DbInt key = new DbInt();
    key.wrapInt(slot);
    final DbLong v = new DbLong();
    v.wrapLong(value);
    store.put(key, v);
  }
}
