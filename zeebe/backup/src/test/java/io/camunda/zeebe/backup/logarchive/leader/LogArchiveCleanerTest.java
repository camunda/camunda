/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.leader;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.backup.logarchive.model.LogArchiveKeys;
import io.camunda.zeebe.backup.logarchive.state.DbLogArchiveState.OpenObject;
import io.camunda.zeebe.backup.logarchive.state.DbLogArchiveState.UnmergedObject;
import io.camunda.zeebe.backup.logarchive.state.LogArchiveMeta;
import io.camunda.zeebe.backup.logarchive.store.LogArchiveStore;
import io.camunda.zeebe.protocol.impl.record.value.management.IndexChunk;
import io.camunda.zeebe.protocol.impl.record.value.management.LogObject;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.ToLongFunction;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

final class LogArchiveCleanerTest {

  private static final int PARTITION = 2;
  private static final String TIMELINE = "tl-0";
  private static final String PREFIX = LogArchiveKeys.partitionPrefix(TIMELINE, PARTITION);
  private static final Duration INTERVAL = Duration.ofMinutes(1);
  private static final Duration GRACE = Duration.ofMinutes(5);

  @RegisterExtension
  private final ControlledActorSchedulerExtension scheduler =
      new ControlledActorSchedulerExtension();

  private final FakeStore store = new FakeStore();
  private final FakeStateView stateView = new FakeStateView();
  private long eventPlaneNextSeq = -1;
  private LogArchiveCleaner cleaner;

  @BeforeEach
  void setUp() {
    scheduler.getClock().pinCurrentTime();
  }

  @Test
  void shouldDeleteReplacedAndOrphanedObjectsOnly() {
    // given - M = 8 by the merged object [1, 8], R = 0
    final var merged = data(1, 8, 7);
    stateView.merged.add(new LogObject().setKey(merged).setFirstIndex(1).setLastIndex(8));
    stateView.meta = meta(8, 0, 10);
    store.put(
        data(1, 4, 6), // replaced by the merge
        data(5, 8, 6), // replaced by the merge
        data(3, 8, 5), // an orphan of an old term, never committed
        data(1, 8, 6), // a merge that lost
        merged,
        data(9, 12, 7), // above M: may be in flight
        data(7, 10, 5), // ends above M
        "data/00000000000000000001-00000000000000000008-l1.seg", // not a data key of the cluster
        "commits/00000000000000000001.json");
    start(null);
    resumeTimeline();

    // when
    runAfter(INTERVAL);
    runAfter(GRACE);

    // then
    assertThat(store.deleted)
        .containsExactlyInAnyOrder(
            PREFIX + data(1, 4, 6),
            PREFIX + data(5, 8, 6),
            PREFIX + data(3, 8, 5),
            PREFIX + data(1, 8, 6));
    assertThat(store.keys).contains(PREFIX + merged, PREFIX + data(9, 12, 7));
  }

  @Test
  void shouldKeepAProposedArchivedObjectThatIsMerged() {
    // given - a run of one object was merged as it is
    final var single = data(1, 8, 6);
    stateView.merged.add(new LogObject().setKey(single).setFirstIndex(1).setLastIndex(8));
    stateView.meta = meta(8, 0, 10);
    store.put(single);
    start(null);
    resumeTimeline();

    // when
    runAfter(INTERVAL);
    runAfter(GRACE);

    // then
    assertThat(store.deleted).isEmpty();
  }

  @Test
  void shouldNotDeleteObjectsAtOrBelowTheReleasedIndex() {
    // given - R = 8: everything up to it belongs to the event plane
    stateView.meta = meta(16, 8, 10);
    stateView.merged.add(new LogObject().setKey(data(9, 16, 7)).setFirstIndex(9).setLastIndex(16));
    store.put(data(1, 4, 6), data(5, 8, 6), data(9, 12, 6), data(13, 16, 6), data(9, 16, 7));
    start(null);
    resumeTimeline();

    // when
    runAfter(INTERVAL);
    runAfter(GRACE);

    // then
    assertThat(store.deleted)
        .containsExactlyInAnyOrder(PREFIX + data(9, 12, 6), PREFIX + data(13, 16, 6));
  }

  @Test
  void shouldDeleteOnlyAfterTheGracePeriod() {
    // given
    stateView.meta = meta(8, 0, 10);
    store.put(data(1, 8, 6));
    start(null);
    resumeTimeline();

    // when - seen as garbage on the first run
    runAfter(INTERVAL);
    runAfter(GRACE.minusSeconds(1));

    // then
    assertThat(store.deleted).isEmpty();

    // when
    runAfter(INTERVAL);

    // then
    assertThat(store.deleted).containsExactly(PREFIX + data(1, 8, 6));
  }

  @Test
  void shouldKeepTheCleanedIndexBelowAPendingObjectAndAdvanceAfterItsDeletion() {
    // given - [1, 4] and [9, 12] are garbage, M = 16, R = 0
    stateView.meta = meta(16, 0, 10);
    stateView.merged.add(new LogObject().setKey(data(1, 16, 7)).setFirstIndex(1).setLastIndex(16));
    store.put(data(1, 4, 6), data(9, 12, 6), data(1, 16, 7));
    start(null);
    resumeTimeline();
    assertThat(cleaner.cleanedIndex()).isZero();

    // when - both are in their grace period
    runAfter(INTERVAL);

    // then - clean only up to just below the first pending object
    assertThat(cleaner.cleanedIndex()).isEqualTo(3);

    // when
    runAfter(GRACE);

    // then
    assertThat(store.deleted).hasSize(2);
    assertThat(cleaner.cleanedIndex()).isEqualTo(16);
  }

  @Test
  void shouldNotAdvanceTheCleanedIndexPastAnObjectThatFailedToDelete() {
    // given
    stateView.meta = meta(16, 0, 10);
    store.put(data(5, 8, 6), data(9, 12, 6));
    store.failDeletes = 1;
    start(null);
    resumeTimeline();
    runAfter(INTERVAL);

    // when - the first delete fails
    runAfter(GRACE);

    // then
    assertThat(cleaner.cleanedIndex()).isEqualTo(7);

    // when
    runAfter(INTERVAL);

    // then
    assertThat(cleaner.cleanedIndex()).isEqualTo(16);
  }

  @Test
  void shouldStartAtTheReleasedIndexOnANewLeader() {
    // given
    stateView.meta = meta(16, 8, 10);
    store.put(data(9, 12, 6));

    // when
    start(null);
    resumeTimeline();

    // then - nothing new is clean before the first sweep
    assertThat(cleaner.cleanedIndex()).isEqualTo(8);

    // when
    runAfter(INTERVAL);

    // then - the pending object keeps it just below its end
    assertThat(cleaner.cleanedIndex()).isEqualTo(11);
  }

  @Test
  void shouldDeleteOnlyWhatTheEventPlaneHasSeenMerged() {
    // given - recovered with M = 0 as of seq 4; merges to 8 (seq 5) and to 16 (seq 7)
    stateView.meta = meta(0, 0, 5);
    store.put(data(1, 8, 6), data(9, 16, 6));
    start(timeline -> eventPlaneNextSeq);
    resumeTimeline();
    stateView.meta = meta(16, 0, 8);
    cleaner.onLogMerged(5, 8, 80);
    cleaner.onLogMerged(7, 16, 160);
    scheduler.workUntilDone();

    // when - the event plane has applied seq 5 and 6
    eventPlaneNextSeq = 7;
    runAfter(INTERVAL);
    runAfter(GRACE);

    // then
    assertThat(store.deleted).containsExactly(PREFIX + data(1, 8, 6));

    // when - it applied seq 7
    eventPlaneNextSeq = 8;
    runAfter(INTERVAL);
    runAfter(GRACE);

    // then
    assertThat(store.deleted).containsExactly(PREFIX + data(1, 8, 6), PREFIX + data(9, 16, 6));
  }

  @Test
  void shouldDeleteNothingOnANewLeaderUntilTheEventPlaneConfirmedTheRecoveredSeq() {
    // given - the recovered state has M = 8 as of seq 9 (nextSeq 10)
    stateView.meta = meta(8, 0, 10);
    store.put(data(1, 8, 6));
    start(timeline -> eventPlaneNextSeq);
    resumeTimeline();

    // when - unknown, then behind seq 9
    runAfter(GRACE);
    eventPlaneNextSeq = 9;
    runAfter(GRACE);

    // then
    assertThat(store.deleted).isEmpty();

    // when
    eventPlaneNextSeq = 10;
    runAfter(INTERVAL);
    runAfter(GRACE);

    // then
    assertThat(store.deleted).containsExactly(PREFIX + data(1, 8, 6));
  }

  @Test
  void shouldDeleteNothingBeforeTheTimelineIsResumed() {
    // given
    stateView.meta = meta(8, 0, 10);
    store.put(data(1, 8, 6));
    start(null);
    cleaner.onRecoveryChecked(stateView.meta.copy(), false);
    scheduler.workUntilDone();

    // when
    runAfter(GRACE);
    runAfter(GRACE);

    // then
    assertThat(store.listCount).isZero();
  }

  @Test
  void shouldDeleteNothingOnATimelineTheBucketIsAheadOf() {
    // given - the bucket holds a later history of the recovered timeline
    stateView.meta = meta(8, 0, 10);
    store.put(data(1, 8, 6), data(9, 16, 6));
    start(null);
    cleaner.onRecoveryChecked(stateView.meta.copy(), true);
    cleaner.onLogArchived(8, 80);
    scheduler.workUntilDone();

    // when
    runAfter(GRACE);
    runAfter(GRACE);

    // then
    assertThat(store.listCount).isZero();
  }

  @Test
  void shouldDeleteNothingOnATimelineStartedBeforeMerging() {
    // given
    stateView.meta = meta(-1, -1, 10);
    store.put(data(1, 8, 6));
    start(null);
    resumeTimeline();

    // when
    runAfter(GRACE);
    runAfter(GRACE);

    // then
    assertThat(store.listCount).isZero();
  }

  @Test
  void shouldTryAgainAfterAFailedDelete() {
    // given
    stateView.meta = meta(8, 0, 10);
    store.put(data(1, 8, 6));
    store.failDeletes = 1;
    start(null);
    resumeTimeline();
    runAfter(INTERVAL);
    runAfter(GRACE);
    assertThat(store.deleted).isEmpty();

    // when
    runAfter(INTERVAL);

    // then
    assertThat(store.deleted).containsExactly(PREFIX + data(1, 8, 6));
  }

  // -----------------------------------------------------------------------------------------------

  private void start(final @Nullable ToLongFunction<String> confirmedNextSeq) {
    cleaner = new LogArchiveCleaner(PARTITION, store, stateView, confirmedNextSeq, INTERVAL, GRACE);
    scheduler.submitActor(cleaner);
    scheduler.workUntilDone();
  }

  private void resumeTimeline() {
    cleaner.onRecoveryChecked(stateView.meta.copy(), false);
    cleaner.onLogArchived(stateView.meta.getArchivedIndex(), 1);
    scheduler.workUntilDone();
  }

  /** Lets the time pass, running the cleaner on the way. */
  private void runAfter(final Duration duration) {
    scheduler.updateClock(duration);
    scheduler.workUntilDone();
  }

  private static LogArchiveMeta meta(
      final long mergedIndex, final long releasedIndex, final long nextSeq) {
    return new LogArchiveMeta()
        .setTimelineId(TIMELINE)
        .setStartIndex(1)
        .setArchivedIndex(20)
        .setMergedIndex(mergedIndex)
        .setReleasedIndex(releasedIndex)
        .setNextSeq(nextSeq);
  }

  private static String data(final long first, final long last, final long term) {
    return LogArchiveKeys.dataKey(first, last, term);
  }

  private static final class FakeStateView implements LogArchiveStateView {
    private LogArchiveMeta meta = new LogArchiveMeta();
    private final List<LogObject> merged = new ArrayList<>();

    @Override
    public LogArchiveMeta meta() {
      return meta.copy();
    }

    @Override
    public List<UnmergedObject> unmergedObjects() {
      return List.of();
    }

    @Override
    public List<OpenObject> openObjects() {
      return List.of();
    }

    @Override
    public List<IndexChunk> chunks() {
      return List.of();
    }

    @Override
    public List<LogObject> mergedObjects() {
      return List.copyOf(merged);
    }
  }

  private static final class FakeStore implements LogArchiveStore {
    private final TreeSet<String> keys = new TreeSet<>();
    private final List<String> deleted = new ArrayList<>();
    private int failDeletes;
    private int listCount;

    void put(final String... relativeKeys) {
      for (final var key : relativeKeys) {
        keys.add(PREFIX + key);
      }
    }

    @Override
    public CompletableFuture<Void> put(final String key, final byte[] content) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<Void> putFile(final String key, final Path file) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<Optional<byte[]>> getIfExists(final String key) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<byte[]> getRange(final String key, final long offset, final int len) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<Void> getToFile(final String key, final Path target) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<List<String>> list(final String prefix) {
      listCount++;
      return CompletableFuture.completedFuture(
          keys.stream().filter(k -> k.startsWith(prefix)).toList());
    }

    @Override
    public CompletableFuture<List<String>> listChildPrefixes(final String prefix) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<Void> delete(final String key) {
      if (failDeletes > 0) {
        failDeletes--;
        return CompletableFuture.failedFuture(new RuntimeException("expected"));
      }
      keys.remove(key);
      deleted.add(key);
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> closeAsync() {
      return CompletableFuture.completedFuture(null);
    }
  }
}
