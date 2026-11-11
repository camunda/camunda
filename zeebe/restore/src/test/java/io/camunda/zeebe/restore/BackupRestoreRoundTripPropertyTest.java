/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.cluster.BrokerMemberId;
import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupMetadata;
import io.camunda.zeebe.backup.common.BackupMetadata.CheckpointEntry;
import io.camunda.zeebe.backup.common.BackupMetadata.RangeEntry;
import io.camunda.zeebe.backup.management.BackupService;
import io.camunda.zeebe.dynamic.config.state.PartitionRestore;
import io.camunda.zeebe.journal.CheckedJournalException.FlushException;
import io.camunda.zeebe.journal.JournalMetaStore;
import io.camunda.zeebe.journal.file.SegmentedJournal;
import io.camunda.zeebe.logstreams.log.LogStreamWriter.WriteFailure;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.restore.PartitionRestoreService.BackupValidator;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.camunda.zeebe.snapshots.SnapshotFilesInfo;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.RandomDistribution;
import net.jqwik.api.Tuple;
import net.jqwik.api.statistics.Statistics;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.Nullable;

/**
 * Writes a generated journal with snapshots, compaction and checkpoints, takes a backup at every
 * backup checkpoint, and restores each restore point from every chain of backups the backup
 * metadata considers restorable. The restored journal must equal the original journal up to the
 * restore point, and start early enough for the restored snapshot.
 *
 * <p>The journal, snapshot store, backup service and restore service are the real ones. The actors
 * run on a controlled scheduler, driven by the test thread, so a run only depends on its seed. The
 * backup store copies a backup's files when saving it, as they are compacted or replaced later.
 */
final class BackupRestoreRoundTripPropertyTest {

  private static final int PARTITION_ID = 1;
  private static final int NODE_ID = 1;
  // small enough that segment boundaries fall between checkpoints and compaction deletes segments
  private static final int MAX_SEGMENT_SIZE = 1024;

  @Property(tries = 25)
  void shouldRestoreTheJournalUpToTheRestorePoint(
      @ForAll("operations") final List<Operation> operations) throws Exception {
    try (final var partition = new BackedUpPartition()) {
      // given
      operations.forEach(partition::apply);
      final var restores = partition.restorableChains();
      Statistics.label("backups").collect(partition.backups().size());
      Statistics.label("restores").collect(restores.size());

      for (final var restore : restores) {
        // when
        final var restored = partition.restore(restore);

        // then
        assertRestoredUpToTheRestorePoint(partition, restore, restored);
      }
    }
  }

  /**
   * Pins a state the property does not generate: a snapshot past the latest backup, which the
   * compaction cap of continuous backups prevents (seed -6622629924197784659 found it before the
   * generator followed the cap). Backup 1 is the last entry of a segment, and the snapshot of
   * backup 2 is in the next segment, so the log of backup 2 starts one position after backup 1. The
   * checkpoint appliers count this as gap free and the restore point resolver restores both
   * together, but the restore needs backup 1's checkpoint record in the log of backup 2 and fails.
   * When the range rule and the restore agree, this test changes with them.
   */
  @Example
  void shouldFailToRestoreABackupWhoseLogStartsRightAfterThePreviousBackup() throws Exception {
    try (final var partition = new BackedUpPartition()) {
      // given
      List.of(
              new Append(
                  List.of(
                      new Entry(1, 60, true),
                      new Entry(1, 15, true),
                      new Entry(1, 40, true),
                      new Entry(1, 105, true),
                      new Entry(1, 11, true))),
              new Append(
                  List.of(
                      new Entry(3, 34, true),
                      new Entry(2, 34, true),
                      new Entry(2, 10, true),
                      new Entry(3, 8, true))),
              new Checkpoint(true),
              new Append(List.of(new Entry(1, 119, true))))
          .forEach(partition::apply);
      partition.takeSnapshotPastTheLatestBackup();
      partition.apply(new Checkpoint(true));
      final var backups = partition.backups();
      assertThat(backups.get(1).firstLogPosition()).isEqualTo(backups.get(0).position() + 1);
      final var restore = new RestoreChain(backups, backups.get(1));
      assertThat(partition.restorableChains()).contains(restore);
      final var metadata =
          new BackupMetadata(
              BackupMetadata.VERSION,
              PARTITION_ID,
              Instant.EPOCH,
              backups.stream()
                  .map(
                      backup ->
                          new CheckpointEntry(
                              backup.checkpointId(),
                              backup.position(),
                              Instant.EPOCH.plusSeconds(backup.checkpointId()),
                              CheckpointType.SCHEDULED_BACKUP,
                              OptionalLong.of(backup.firstLogPosition())))
                  .toList(),
              List.of(new RangeEntry(1, 2)));
      assertThat(
              RestorePointResolver.resolve(List.of(metadata), null, null, Map.of(PARTITION_ID, -1L))
                  .partitions())
          .containsExactly(Map.entry(PARTITION_ID, restore.partitionRestore()));

      // when - then
      assertThatThrownBy(() -> partition.restore(restore))
          .rootCause()
          .hasMessageContaining("Cannot find a record at checkpoint position");
    }
  }

  private static void assertRestoredUpToTheRestorePoint(
      final BackedUpPartition partition, final RestoreChain restore, final Restored restored) {
    final var restorePoint = restore.restorePoint();
    assertThat(restored.entries()).describedAs("restored journal of %s", restore).isNotEmpty();
    assertThat(restored.entries().getLast().index())
        .describedAs("last restored index of %s", restore)
        .isEqualTo(restorePoint.index());
    assertThat(restored.entries())
        .describedAs("restored entries of %s", restore)
        .containsExactlyElementsOf(
            partition.written(restored.entries().getFirst().index(), restorePoint.index()));
    final var snapshotIndex = restore.first().snapshotIndex();
    assertThat(restored.entries().getFirst().index())
        .describedAs("first restored index of %s", restore)
        .isLessThanOrEqualTo(snapshotIndex == null ? 1 : snapshotIndex);
    final var snapshotId = restore.first().snapshotId();
    assertThat(restored.snapshotIds())
        .describedAs("restored snapshots of %s", restore)
        .containsExactlyElementsOf(snapshotId == null ? Set.of() : Set.of(snapshotId));
  }

  @Provide
  Arbitrary<List<Operation>> operations() {
    final var entry =
        Combinators.combine(
                Arbitraries.integers().between(1, 3),
                Arbitraries.integers().between(8, 120),
                Arbitraries.frequency(Tuple.of(9, true), Tuple.of(1, false)))
            .as(Entry::new);
    final var append = entry.list().ofMinSize(1).ofMaxSize(8).map(Append::new);
    final var checkpoint =
        Arbitraries.frequency(Tuple.of(2, true), Tuple.of(1, false)).map(Checkpoint::new);
    final var snapshot = Arbitraries.integers().between(0, 10).map(TakeSnapshot::new);
    final var compact = Arbitraries.just(new Compact());
    return Arbitraries.frequencyOf(
            Tuple.of(5, append.map(Operation.class::cast)),
            Tuple.of(3, checkpoint.map(Operation.class::cast)),
            Tuple.of(2, snapshot.map(Operation.class::cast)),
            Tuple.of(1, compact.map(Operation.class::cast)))
        .list()
        .ofMaxSize(50)
        .withSizeDistribution(RandomDistribution.uniform());
  }

  sealed interface Operation permits Append, Checkpoint, TakeSnapshot, Compact {}

  /** Appends entries to the journal. */
  record Append(List<Entry> entries) implements Operation {}

  /**
   * An entry with an application sequence number some positions after the previous one, as a batch
   * of records takes several, or without one, as Raft's own entries.
   */
  record Entry(int positions, int size, boolean application) {}

  /** Appends a checkpoint record and, for a backup checkpoint, takes a backup at it. */
  record Checkpoint(boolean backup) implements Operation {}

  /** Takes a snapshot some entries before the end of the journal, capped as in production. */
  record TakeSnapshot(int entriesBeforeEnd) implements Operation {}

  /** Deletes the segments before the latest snapshot. */
  record Compact() implements Operation {}

  /** An entry of a journal. */
  record JournalEntry(long index, long asqn, String data) {}

  /** A checkpoint record in the journal; a backup checkpoint carries what its backup holds. */
  record CheckpointRecord(
      long checkpointId,
      long index,
      long position,
      @Nullable Long firstLogPosition,
      @Nullable String snapshotId,
      @Nullable Long snapshotIndex) {

    boolean isBackup() {
      return firstLogPosition != null;
    }

    @Override
    public String toString() {
      return isBackup()
          ? "backup %d at position %d (first log position %d, snapshot index %s)"
              .formatted(checkpointId, position, firstLogPosition, snapshotIndex)
          : "marker %d at position %d".formatted(checkpointId, position);
    }
  }

  /**
   * Backups restored up to a restore point: the backups are consecutive and gap free by the rule
   * the backup metadata uses, and the last is the first backup at or after the restore point.
   */
  record RestoreChain(List<CheckpointRecord> backups, CheckpointRecord restorePoint) {

    CheckpointRecord first() {
      return backups.getFirst();
    }

    PartitionRestore partitionRestore() {
      final var ids = new TreeSet<Long>();
      backups.forEach(backup -> ids.add(backup.checkpointId()));
      return new PartitionRestore(ids, OptionalLong.of(restorePoint.position()));
    }

    @Override
    public String toString() {
      return "restore of %s up to %s".formatted(backups, restorePoint);
    }
  }

  record Restored(List<JournalEntry> entries, Set<String> snapshotIds) {}

  /** A partition's journal, snapshot store and backup service, and the backups taken of it. */
  private static final class BackedUpPartition implements AutoCloseable {
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ControlledActorSchedulerExtension scheduler =
        new ControlledActorSchedulerExtension();
    private final Path root;
    private final Path dataDirectory;
    private final TestRestorableBackupStore backupStore;
    private final SegmentedJournal journal;
    private final FileBasedSnapshotStore snapshotStore;
    private final BackupService backupService;
    private final TreeMap<Long, JournalEntry> written = new TreeMap<>();
    private final List<CheckpointRecord> checkpoints = new ArrayList<>();
    private final Map<String, Long> snapshotIndexes = new HashMap<>();
    private long lastPosition;
    private long lastSnapshotIndex;
    private long checkpointId;
    private int restores;

    BackedUpPartition() throws Exception {
      root = Files.createTempDirectory("backup-round-trip");
      try {
        dataDirectory = Files.createDirectories(root.resolve("data"));
        backupStore =
            new TestRestorableBackupStore(Files.createDirectories(root.resolve("backup-store")));
        scheduler.beforeEach(null);
        journal =
            SegmentedJournal.builder(meterRegistry)
                .withDirectory(dataDirectory.toFile())
                .withName(raftPartition(dataDirectory).name())
                .withMaxSegmentSize(MAX_SEGMENT_SIZE)
                .withMetaStore(new JournalMetaStore.InMemory())
                .build();
        snapshotStore =
            new FileBasedSnapshotStore(
                NODE_ID,
                PARTITION_ID,
                dataDirectory,
                path -> SnapshotFilesInfo.none(),
                meterRegistry);
        await(scheduler.submitActor(snapshotStore));
        backupService =
            new BackupService(
                BrokerMemberId.from(NODE_ID),
                new PartitionId("raft", PARTITION_ID),
                backupStore,
                snapshotStore,
                dataDirectory,
                index -> CompletableFuture.completedFuture(journal.getTailSegments(index)),
                meterRegistry,
                (context, entries, source) -> Either.left(WriteFailure.CLOSED),
                null,
                null);
        await(scheduler.submitActor(backupService));
      } catch (final Exception e) {
        FileUtil.deleteFolder(root);
        throw e;
      }
    }

    void apply(final Operation operation) {
      switch (operation) {
        case final Append append -> append.entries().forEach(this::append);
        case final Checkpoint checkpoint -> checkpoint(checkpoint.backup());
        case final TakeSnapshot snapshot -> takeSnapshot(snapshot.entriesBeforeEnd());
        case final Compact ignored -> journal.deleteUntil(lastSnapshotIndex);
      }
    }

    List<CheckpointRecord> backups() {
      return checkpoints.stream().filter(CheckpointRecord::isBackup).toList();
    }

    /**
     * Every chain of backups that restores a checkpoint, by the rules of the backup metadata: a
     * backup continues the previous one if its log starts at most one position after it, and a
     * restore point lies after the second to last backup, or is the only backup itself.
     */
    List<RestoreChain> restorableChains() {
      final var backups = backups();
      final var chains = new ArrayList<RestoreChain>();
      for (final var restorePoint : checkpoints) {
        final var last =
            backups.stream()
                .filter(backup -> backup.checkpointId() >= restorePoint.checkpointId())
                .findFirst();
        if (last.isEmpty()) {
          continue;
        }
        final var lastIndex = backups.indexOf(last.get());
        for (int first = lastIndex; first >= 0; first--) {
          if (first < lastIndex
              && backups.get(first + 1).firstLogPosition() > backups.get(first).position() + 1) {
            break;
          }
          final var chain = backups.subList(first, lastIndex + 1);
          final var endsAtRestorePoint =
              chain.size() == 1
                  ? chain.getFirst() == restorePoint
                  : restorePoint.position() > chain.get(chain.size() - 2).position();
          if (endsAtRestorePoint) {
            chains.add(new RestoreChain(List.copyOf(chain), restorePoint));
          }
        }
      }
      return chains;
    }

    Restored restore(final RestoreChain chain) throws IOException, FlushException {
      final var directory = Files.createDirectories(root.resolve("restore-" + restores++));
      final var raftPartition = raftPartition(directory);
      try {
        new PartitionRestoreService(
                backupStore,
                raftPartition,
                NODE_ID,
                path -> SnapshotFilesInfo.none(),
                meterRegistry)
            .restore(chain.partitionRestore(), BackupValidator.none());
      } catch (final RuntimeException e) {
        throw new AssertionError("Expected the %s to succeed, but it failed".formatted(chain), e);
      }

      final var entries = new ArrayList<JournalEntry>();
      try (final var restoredJournal =
              SegmentedJournal.builder(meterRegistry)
                  .withDirectory(directory.toFile())
                  .withName(raftPartition.name())
                  .withMetaStore(new JournalMetaStore.InMemory())
                  .build();
          final var reader = restoredJournal.openReader()) {
        while (reader.hasNext()) {
          final var record = reader.next();
          final var data = new byte[record.data().capacity()];
          record.data().getBytes(0, data);
          entries.add(
              new JournalEntry(
                  record.index(), record.asqn(), new String(data, StandardCharsets.UTF_8)));
        }
      }
      final var snapshotIds = new TreeSet<String>();
      final var snapshots = directory.resolve("snapshots");
      if (Files.exists(snapshots)) {
        try (final var files = Files.list(snapshots)) {
          files
              .filter(Files::isDirectory)
              .forEach(snapshot -> snapshotIds.add(snapshot.getFileName().toString()));
        }
      }
      FileUtil.deleteFolder(directory);
      return new Restored(entries, snapshotIds);
    }

    List<JournalEntry> written(final long fromIndex, final long toIndex) {
      return List.copyOf(written.subMap(fromIndex, true, toIndex, true).values());
    }

    private void append(final Entry entry) {
      final long asqn;
      if (entry.application()) {
        asqn = lastPosition + 1;
        lastPosition += entry.positions();
      } else {
        asqn = SegmentedJournal.ASQN_IGNORE;
      }
      write(asqn, "entry".repeat(entry.size() / 5));
    }

    private long write(final long asqn, final String data) {
      final var record =
          journal.append(
              asqn,
              new DirectBufferWriter(new UnsafeBuffer(data.getBytes(StandardCharsets.UTF_8))));
      written.put(record.index(), new JournalEntry(record.index(), asqn, data));
      return record.index();
    }

    private void checkpoint(final boolean backup) {
      checkpointId++;
      final var position = ++lastPosition;
      final var index = write(position, "checkpoint " + checkpointId);
      if (!backup) {
        checkpoints.add(new CheckpointRecord(checkpointId, index, position, null, null, null));
        return;
      }

      final var saved =
          backupStore.waitForBackup(new BackupIdentifierImpl(NODE_ID, PARTITION_ID, checkpointId));
      final var taken =
          backupService.takeBackup(
              checkpointId,
              new BackupDescriptorImpl(
                  position, 1, "test", Instant.EPOCH, CheckpointType.SCHEDULED_BACKUP));
      await(taken);
      final Backup savedBackup = saved.join();
      final var snapshotId = savedBackup.descriptor().snapshotId().orElse(null);
      checkpoints.add(
          new CheckpointRecord(
              checkpointId,
              index,
              position,
              savedBackup.descriptor().firstLogPosition().orElseThrow(),
              snapshotId,
              snapshotId == null ? null : snapshotIndexes.get(snapshotId)));
    }

    /**
     * Takes a snapshot as a partition with continuous backups does: at most at the entry before the
     * latest backup's checkpoint record, and not at all before the first backup, so that the log a
     * later backup needs is never compacted.
     */
    private void takeSnapshot(final int entriesBeforeEnd) {
      final var backups = backups();
      if (backups.isEmpty()) {
        return;
      }
      takeSnapshotAt(
          Math.min(journal.getLastIndex() - entriesBeforeEnd, backups.getLast().index() - 1));
    }

    void takeSnapshotPastTheLatestBackup() {
      takeSnapshotAt(journal.getLastIndex());
    }

    private void takeSnapshotAt(final long index) {
      if (index <= lastSnapshotIndex || index < journal.getFirstIndex()) {
        return;
      }
      final var processed =
          written.headMap(index, true).values().stream()
              .mapToLong(JournalEntry::asqn)
              .filter(asqn -> asqn != SegmentedJournal.ASQN_IGNORE)
              .max()
              .orElse(0);
      final var transientSnapshot =
          snapshotStore.newTransientSnapshot(index, 1, processed, processed, false).get();
      transientSnapshot.take(
          path -> {
            try {
              FileUtil.ensureDirectoryExists(path);
              Files.writeString(path.resolve("state"), "state at " + index);
            } catch (final IOException e) {
              throw new UncheckedIOException(e);
            }
          });
      final var persisted =
          transientSnapshot
              .withLastFollowupEventPosition(processed)
              .withMaxExportedPosition(processed)
              .persist();
      snapshotIndexes.put(await(persisted).getId(), index);
      lastSnapshotIndex = index;
    }

    private <T> T await(final ActorFuture<T> future) {
      scheduler.workUntilDone();
      assertThat((Future<T>) future).describedAs("actor future after running all actors").isDone();
      return future.join();
    }

    private RaftPartition raftPartition(final Path directory) {
      final var metadata =
          new PartitionMetadata(
              new PartitionId("raft", PARTITION_ID), Set.of(), Map.of(), 1, new MemberId("1"));
      return new RaftPartition(metadata, null, directory.toFile(), meterRegistry);
    }

    @Override
    public void close() throws Exception {
      try {
        backupService.closeAsync();
        snapshotStore.closeAsync();
        scheduler.workUntilDone();
        scheduler.afterEach(null);
        journal.close();
        meterRegistry.close();
      } finally {
        FileUtil.deleteFolder(root);
      }
    }
  }
}
