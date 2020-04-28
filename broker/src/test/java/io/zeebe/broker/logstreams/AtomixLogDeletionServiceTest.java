/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.logstreams;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.raft.storage.RaftStorage;
import io.atomix.raft.storage.log.RaftLogReader;
import io.atomix.storage.journal.Indexed;
import io.atomix.storage.journal.JournalReader.Mode;
import io.atomix.storage.journal.JournalSegmentDescriptor;
import io.atomix.utils.time.WallClockTimestamp;
import io.zeebe.broker.clustering.atomix.storage.snapshot.AtomixRecordEntrySupplierImpl;
import io.zeebe.broker.clustering.atomix.storage.snapshot.AtomixSnapshotStorage;
import io.zeebe.broker.clustering.atomix.storage.snapshot.DbSnapshotStore;
import io.zeebe.logstreams.state.SnapshotMetrics;
import io.zeebe.logstreams.util.AtomixLogStorageRule;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public final class AtomixLogDeletionServiceTest {

  private static final ByteBuffer DATA = ByteBuffer.allocate(Integer.BYTES).putInt(0, 1);
  private static final int PARTITION_ID = 1;

  private final ActorSchedulerRule actorScheduler = new ActorSchedulerRule();
  private final TemporaryFolder temporaryFolder = new TemporaryFolder();
  private final AtomixLogStorageRule logStorageRule =
      new AtomixLogStorageRule(temporaryFolder, PARTITION_ID, b -> builder(b, temporaryFolder));

  @Rule
  public final RuleChain chain =
      RuleChain.outerRule(temporaryFolder).around(actorScheduler).around(logStorageRule);

  private AtomixSnapshotStorage snapshotStorage;
  private LogDeletionService deletionService;
  private Compactor compactor;

  @Before
  public void setUp() throws IOException {
    final var runtimeDirectory = temporaryFolder.newFolder().toPath();
    final var pendingDirectory = temporaryFolder.newFolder().toPath();
    snapshotStorage =
        new AtomixSnapshotStorage(
            runtimeDirectory,
            pendingDirectory,
            logStorageRule.getSnapshotStore(),
            new AtomixRecordEntrySupplierImpl(
                logStorageRule.getIndexMapping(),
                logStorageRule.getRaftLog().openReader(-1, Mode.COMMITS)),
            new SnapshotMetrics(PARTITION_ID));
    compactor = new Compactor();

    deletionService = new LogDeletionService(0, PARTITION_ID, compactor, snapshotStorage);
    actorScheduler.submitActor(deletionService).join();
  }

  @After
  public void tearDown() {
    snapshotStorage.close();
    deletionService.close();
  }

  @Test
  public void shouldDeleteUpToCompactionBound() {
    // given
    final var reader = logStorageRule.getRaftLog().openReader(-1);

    // when
    logStorageRule.appendEntry(1, 1, DATA).index();
    logStorageRule.appendEntry(2, 2, DATA).index();
    logStorageRule.appendEntry(3, 3, DATA).index();
    createSnapshot(2);

    // then
    compactor.awaitCompaction(2L, Duration.ofSeconds(5));
    reader.reset();
    final var entries = readAllEntries(reader);
    assertThat(entries).isNotEmpty().hasSize(2).extracting(Indexed::index).containsExactly(2L, 3L);
  }

  @Test
  public void shouldNotDeleteOnLowerCompactionBound() {
    // given
    final var reader = logStorageRule.getRaftLog().openReader(-1);

    // when
    logStorageRule.appendEntry(1, 1, DATA).index();
    logStorageRule.appendEntry(2, 2, DATA).index();
    logStorageRule.appendEntry(3, 3, DATA).index();
    createSnapshot(0);

    // then
    compactor.awaitCompaction(0L, Duration.ofSeconds(5));
    reader.reset();
    final var entries = readAllEntries(reader);
    assertThat(entries)
        .isNotEmpty()
        .hasSize(3)
        .extracting(Indexed::index)
        .containsExactly(1L, 2L, 3L);
  }

  @Test
  public void shouldDeleteLowerEntriesEvenIfIndexNotFound() {
    // given
    final var reader = logStorageRule.getRaftLog().openReader(-1);

    // when
    logStorageRule.appendEntry(1, 1, DATA).index();
    logStorageRule.appendEntry(2, 2, DATA).index();
    logStorageRule.appendEntry(3, 3, DATA).index();
    createSnapshot(5L);

    // then - expect exactly one segment left with entry 3
    compactor.awaitCompaction(5L, Duration.ofSeconds(5));
    reader.reset();
    final var entries = readAllEntries(reader);
    assertThat(entries).isNotEmpty().hasSize(1).extracting(Indexed::index).containsExactly(3L);
  }

  private void createSnapshot(final long index) {
    final var store = logStorageRule.getSnapshotStore();
    final var now = WallClockTimestamp.from(System.currentTimeMillis());
    final var pending = store.newPendingSnapshot(index, 0, now);
    pending.write(ByteBuffer.wrap("foo".getBytes()), ByteBuffer.wrap("bar".getBytes()));
    pending.commit();
  }

  private static RaftStorage.Builder builder(
      final RaftStorage.Builder builder, final TemporaryFolder folder) {
    try {
      return builder
          // hardcode max segment size to allow a single entry only
          .withMaxSegmentSize(JournalSegmentDescriptor.BYTES + 8 * Integer.BYTES)
          .withSnapshotStore(
              new DbSnapshotStore(
                  folder.newFolder("runtime").toPath(),
                  folder.newFolder("snapshots").toPath(),
                  new ConcurrentSkipListMap<>()));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private List<Indexed<?>> readAllEntries(final RaftLogReader reader) {
    final List<Indexed<?>> entries = new ArrayList<>();
    while (reader.hasNext()) {
      entries.add(reader.next());
    }
    return entries;
  }

  private final class Compactor implements LogCompactor {
    private final Map<Long, CompletableFuture<Void>> compactions = new ConcurrentHashMap<>();

    private void awaitCompaction(final long compactionBound, final Duration timeout) {
      final var future =
          compactions.computeIfAbsent(compactionBound, ignored -> new CompletableFuture<>());
      future.orTimeout(timeout.toNanos(), TimeUnit.NANOSECONDS).join();
    }

    @Override
    public CompletableFuture<Void> compactLog(final long compactionBound) {
      final var compactionResult =
          compactions.compute(
              compactionBound,
              (key, value) -> {
                if (value == null || value.isDone()) {
                  return new CompletableFuture<>();
                }

                return value;
              });
      logStorageRule
          .compact(compactionBound)
          .whenComplete(
              (nothing, error) -> {
                if (error != null) {
                  compactionResult.completeExceptionally(error);
                } else {
                  compactionResult.complete(nothing);
                }
              });

      return compactionResult;
    }
  }
}
