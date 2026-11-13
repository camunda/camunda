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

import io.camunda.zeebe.backup.common.BackupMetadata;
import io.camunda.zeebe.backup.common.BackupMetadata.CheckpointEntry;
import io.camunda.zeebe.backup.common.BackupMetadata.RangeEntry;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.restore.RestorePointModel.Rejected;
import io.camunda.zeebe.restore.RestorePointModel.Resolved;
import io.camunda.zeebe.restore.RestorePointResolver.RestorableBackups;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.IntStream;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;
import net.jqwik.api.statistics.Statistics;
import org.jspecify.annotations.Nullable;

/**
 * Compares {@link RestorePointResolver} with {@link RestorePointModel} on backup metadata that a
 * simulated cluster writes: checkpoints taken on every partition, backups that fail on some of
 * them, compaction that leaves gaps between backups, and backups deleted later, which shrink and
 * split ranges. The time range and exported positions are drawn from the timestamps and positions
 * of that history, so they hit its boundaries.
 */
final class RestorePointResolverPropertyTest {

  private static final Instant START = Instant.parse("2025-01-01T00:00:00Z");
  private static final int MAX_PARTITIONS = 3;

  @Property(tries = 1000)
  void shouldResolveTheRestorePointOfTheModel(
      @ForAll("histories") final History history, @ForAll("queries") final Query query) {
    // given
    final var metadata = history.metadata();
    final var from = query.from(history);
    final var to = query.to(history);
    final var exportedPositions = query.exportedPositions(history);

    // when
    final var expected = RestorePointModel.resolve(metadata, from, to, exportedPositions);

    // then
    switch (expected) {
      case final Resolved resolved -> {
        Statistics.label("outcome").collect("resolved");
        final var actual = RestorePointResolver.resolve(metadata, from, to, exportedPositions);
        assertThat(actual)
            .describedAs(
                "metadata=%s, from=%s, to=%s, exportedPositions=%s",
                metadata, from, to, exportedPositions)
            .isEqualTo(resolved.backups());
        assertRestorable(metadata, actual, exportedPositions);
      }
      case final Rejected rejected -> {
        Statistics.label("outcome").collect("rejected: " + rejected.reason());
        assertThatThrownBy(
                () -> RestorePointResolver.resolve(metadata, from, to, exportedPositions),
                "expected a rejection, as the model rejects with '%s', for metadata=%s, from=%s, to=%s, exportedPositions=%s",
                rejected.detail(),
                metadata,
                from,
                to,
                exportedPositions)
            .isInstanceOf(IllegalStateException.class);
      }
    }
    Statistics.label("partitions").collect(metadata.size());
    Statistics.label("ranges on partition 1").collect(metadata.getFirst().ranges().size());
  }

  /**
   * Checks what makes a resolved restore usable, independent of which restore point was chosen:
   * every partition restores consecutive, gap free backups it holds, and its restored log ends at
   * the restore point as that partition recorded it.
   */
  private static void assertRestorable(
      final List<BackupMetadata> metadata,
      final RestorableBackups restorable,
      final @Nullable Map<Integer, Long> exportedPositions) {
    assertThat(restorable.partitions().keySet())
        .containsExactlyInAnyOrderElementsOf(
            metadata.stream().map(BackupMetadata::partitionId).toList());
    for (final var partition : metadata) {
      final var restore = restorable.partitions().get(partition.partitionId());
      final var byId = new TreeMap<Long, CheckpointEntry>();
      partition
          .checkpoints()
          .forEach(checkpoint -> byId.put(checkpoint.checkpointId(), checkpoint));
      final var restorePoint = byId.get(restorable.globalCheckpointId());
      assertThat(restorePoint).describedAs("restore point on partition").isNotNull();
      assertThat(restore.checkpointPosition()).hasValue(restorePoint.checkpointPosition());

      final var backups =
          byId
              .subMap(restore.backupIds().first(), true, restore.backupIds().last(), true)
              .values()
              .stream()
              .filter(checkpoint -> checkpoint.checkpointType().shouldCreateBackup())
              .toList();
      assertThat(backups)
          .describedAs("consecutive backups of partition %d", partition.partitionId())
          .extracting(CheckpointEntry::checkpointId)
          .containsExactlyElementsOf(restore.backupIds());
      for (int i = 1; i < backups.size(); i++) {
        assertThat(backups.get(i).getFirstLogPositionOrDefault())
            .describedAs("first log position of backup %d", backups.get(i).checkpointId())
            .isLessThanOrEqualTo(backups.get(i - 1).checkpointPosition() + 1);
      }
      assertThat(backups.getLast().checkpointId())
          .isGreaterThanOrEqualTo(restorePoint.checkpointId());
      if (backups.size() > 1) {
        assertThat(restorePoint.checkpointPosition())
            .isGreaterThan(backups.get(backups.size() - 2).checkpointPosition());
      } else {
        assertThat(restorePoint).isEqualTo(backups.getFirst());
      }
      final var exported =
          exportedPositions == null ? null : exportedPositions.get(partition.partitionId());
      if (exported != null) {
        assertThat(restorePoint.checkpointPosition()).isGreaterThanOrEqualTo(exported);
      }
    }
  }

  @Provide
  Arbitrary<History> histories() {
    final var partitionEvent =
        Combinators.combine(
                Arbitraries.integers().between(1, 40),
                Arbitraries.integers().between(0, 59),
                Arbitraries.frequency(Tuple.of(5, true), Tuple.of(1, false)),
                Arbitraries.oneOf(Arbitraries.just(0), Arbitraries.integers().between(0, 100)))
            .as(PartitionEvent::new);
    final var checkpoint =
        Combinators.combine(
                Arbitraries.frequency(
                    Tuple.of(1, CheckpointType.MARKER),
                    Tuple.of(2, CheckpointType.SCHEDULED_BACKUP),
                    Tuple.of(1, CheckpointType.MANUAL_BACKUP)),
                Arbitraries.integers().between(1, 3),
                Arbitraries.integers().between(1, 120),
                partitionEvent.list().ofSize(MAX_PARTITIONS))
            .as(TakeCheckpoint::new);
    final var deletion = Arbitraries.integers().between(0, 20).map(DeleteBackup::new);
    final var step =
        Arbitraries.frequencyOf(
            Tuple.of(5, checkpoint.map(Step.class::cast)),
            Tuple.of(1, deletion.map(Step.class::cast)));
    return Combinators.combine(
            Arbitraries.integers().between(1, MAX_PARTITIONS),
            step.list().ofMinSize(2).ofMaxSize(15))
        .as(History::new);
  }

  @Provide
  Arbitrary<Query> queries() {
    final var time =
        Combinators.combine(
                Arbitraries.frequency(
                    Tuple.of(3, TimeKind.NONE),
                    Tuple.of(2, TimeKind.AT),
                    Tuple.of(1, TimeKind.JUST_BEFORE),
                    Tuple.of(1, TimeKind.JUST_AFTER),
                    Tuple.of(2, TimeKind.HALFWAY_TO_NEXT),
                    Tuple.of(1, TimeKind.BEFORE_ALL),
                    Tuple.of(1, TimeKind.AFTER_ALL)),
                Arbitraries.integers().between(0, 50))
            .as(TimeChoice::new);
    final var exported =
        Combinators.combine(
                Arbitraries.frequency(
                    Tuple.of(1, PositionKind.NOTHING_EXPORTED),
                    Tuple.of(4, PositionKind.NEAR_CHECKPOINT),
                    Tuple.of(2, PositionKind.NEAR_FIRST_LOG_POSITION),
                    Tuple.of(1, PositionKind.ANYWHERE)),
                Arbitraries.integers().between(0, 50),
                Arbitraries.integers().between(-1, 1))
            .as(PositionChoice::new);
    return Combinators.combine(
            time, time, Arbitraries.of(true, false), exported.list().ofSize(MAX_PARTITIONS))
        .as(Query::new);
  }

  /** A step of the simulated cluster, applied to every partition. */
  sealed interface Step permits TakeCheckpoint, DeleteBackup {}

  /**
   * Takes a checkpoint: a marker, or a backup that each partition records only if it succeeded
   * there.
   */
  record TakeCheckpoint(
      CheckpointType type, int idIncrement, int minutesLater, List<PartitionEvent> partitions)
      implements Step {

    @Override
    public String toString() {
      return "TakeCheckpoint[%s, id +%d, +%d min, %s]"
          .formatted(type, idIncrement, minutesLater, partitions);
    }
  }

  /**
   * What a partition does up to a checkpoint: write some records, record the checkpoint a little
   * later than the others, and compact part of its log. A partition that misses the checkpoint, or
   * whose backup fails, records nothing.
   */
  record PartitionEvent(
      int recordsWritten, int secondsLate, boolean recorded, int percentCompacted) {

    @Override
    public String toString() {
      return "(%d records, %ds late, %s, %d%% compacted)"
          .formatted(
              recordsWritten, secondsLate, recorded ? "recorded" : "missed", percentCompacted);
    }
  }

  /** Deletes one of the backups taken so far, on every partition. */
  record DeleteBackup(int backupSelector) implements Step {}

  /** The metadata a cluster of {@code partitionCount} partitions writes for the given steps. */
  record History(int partitionCount, List<Step> steps) {

    List<BackupMetadata> metadata() {
      final var partitions =
          IntStream.rangeClosed(1, partitionCount).mapToObj(SimulatedPartition::new).toList();
      final var backupIds = new TreeSet<Long>();
      var checkpointId = 0L;
      var time = START;
      for (final var step : steps) {
        switch (step) {
          case final TakeCheckpoint checkpoint -> {
            checkpointId += checkpoint.idIncrement();
            time = time.plus(Duration.ofMinutes(checkpoint.minutesLater()));
            for (final var partition : partitions) {
              partition.takeCheckpoint(
                  checkpointId,
                  checkpoint.type(),
                  time,
                  checkpoint.partitions().get(partition.partitionId - 1));
            }
            if (checkpoint.type().shouldCreateBackup()) {
              backupIds.add(checkpointId);
            }
          }
          case final DeleteBackup deletion -> {
            if (!backupIds.isEmpty()) {
              final var deleted =
                  backupIds.stream().toList().get(deletion.backupSelector() % backupIds.size());
              backupIds.remove(deleted);
              partitions.forEach(partition -> partition.deleteBackup(deleted));
            }
          }
        }
      }
      return partitions.stream().map(SimulatedPartition::metadata).toList();
    }

    /** When any partition recorded a checkpoint, or only a backup. */
    List<Instant> timestamps(final boolean backupsOnly) {
      return metadata().stream()
          .flatMap(partition -> partition.checkpoints().stream())
          .filter(checkpoint -> !backupsOnly || checkpoint.checkpointType().shouldCreateBackup())
          .map(CheckpointEntry::checkpointTimestamp)
          .distinct()
          .sorted()
          .toList();
    }
  }

  /**
   * One partition's checkpoint and backup range state, maintained as the checkpoint appliers do: a
   * backup extends the range of the latest backup if its log starts at most one position after it,
   * and starts a new range otherwise; a deleted backup shrinks or splits the range containing it
   * and drops the checkpoints before the first remaining range.
   */
  static final class SimulatedPartition {
    private final int partitionId;
    private final TreeMap<Long, CheckpointEntry> checkpoints = new TreeMap<>();
    private final TreeMap<Long, Long> ranges = new TreeMap<>();
    private long position;
    private long firstLogPosition = 1;

    SimulatedPartition(final int partitionId) {
      this.partitionId = partitionId;
    }

    void takeCheckpoint(
        final long checkpointId,
        final CheckpointType type,
        final Instant time,
        final PartitionEvent event) {
      position += event.recordsWritten();
      final var compactable = Math.max(0, position - 1 - firstLogPosition);
      firstLogPosition += compactable * event.percentCompacted() / 100;
      if (!event.recorded()) {
        return;
      }
      final var timestamp = time.plusSeconds(event.secondsLate());
      if (!type.shouldCreateBackup()) {
        checkpoints.put(
            checkpointId,
            new CheckpointEntry(checkpointId, position, timestamp, type, OptionalLong.empty()));
        return;
      }
      final var latest = latestBackup();
      final var range = latest == null ? null : rangeContaining(latest.checkpointId());
      if (latest != null && range != null && firstLogPosition <= latest.checkpointPosition() + 1) {
        ranges.put(range.start(), checkpointId);
      } else {
        ranges.put(checkpointId, checkpointId);
      }
      checkpoints.put(
          checkpointId,
          new CheckpointEntry(
              checkpointId, position, timestamp, type, OptionalLong.of(firstLogPosition)));
    }

    void deleteBackup(final long checkpointId) {
      checkpoints.remove(checkpointId);
      final var range = rangeContaining(checkpointId);
      if (range == null) {
        return;
      }
      final var predecessor = backupBefore(checkpointId);
      final var successor = backupAfter(checkpointId);
      final var isStart = range.start() == checkpointId;
      final var isEnd = range.end() == checkpointId;
      if (isStart && isEnd) {
        ranges.remove(range.start());
      } else if (isStart) {
        ranges.remove(range.start());
        if (successor != null) {
          ranges.put(successor, range.end());
        }
      } else if (isEnd) {
        if (predecessor != null) {
          ranges.put(range.start(), predecessor);
        } else {
          ranges.remove(range.start());
        }
      } else if (predecessor != null && successor != null) {
        ranges.put(range.start(), predecessor);
        ranges.put(successor, range.end());
      }
      if (!ranges.isEmpty()) {
        final var firstLog = checkpoints.get(ranges.firstKey()).getFirstLogPositionOrDefault();
        while (!checkpoints.isEmpty()
            && checkpoints.firstEntry().getValue().getFirstLogPositionOrDefault() < firstLog) {
          checkpoints.pollFirstEntry();
        }
      }
    }

    BackupMetadata metadata() {
      return new BackupMetadata(
          BackupMetadata.VERSION,
          partitionId,
          START,
          List.copyOf(checkpoints.values()),
          ranges.entrySet().stream()
              .map(range -> new RangeEntry(range.getKey(), range.getValue()))
              .toList());
    }

    private @Nullable CheckpointEntry latestBackup() {
      return checkpoints.descendingMap().values().stream()
          .filter(checkpoint -> checkpoint.checkpointType().shouldCreateBackup())
          .findFirst()
          .orElse(null);
    }

    private @Nullable RangeEntry rangeContaining(final long checkpointId) {
      final var range = ranges.floorEntry(checkpointId);
      return range != null && range.getValue() >= checkpointId
          ? new RangeEntry(range.getKey(), range.getValue())
          : null;
    }

    private @Nullable Long backupBefore(final long checkpointId) {
      return checkpoints.headMap(checkpointId, false).descendingMap().values().stream()
          .filter(checkpoint -> checkpoint.checkpointType().shouldCreateBackup())
          .map(CheckpointEntry::checkpointId)
          .findFirst()
          .orElse(null);
    }

    private @Nullable Long backupAfter(final long checkpointId) {
      return checkpoints.tailMap(checkpointId, false).values().stream()
          .filter(checkpoint -> checkpoint.checkpointType().shouldCreateBackup())
          .map(CheckpointEntry::checkpointId)
          .findFirst()
          .orElse(null);
    }
  }

  enum TimeKind {
    NONE,
    AT,
    JUST_BEFORE,
    JUST_AFTER,
    HALFWAY_TO_NEXT,
    BEFORE_ALL,
    AFTER_ALL
  }

  /** A time relative to the timestamps of the history, so it hits their boundaries. */
  record TimeChoice(TimeKind kind, int selector) {

    @Nullable Instant resolve(final List<Instant> timestamps) {
      if (kind == TimeKind.NONE) {
        return null;
      }
      if (timestamps.isEmpty()) {
        return START;
      }
      final var index = selector % timestamps.size();
      final var at = timestamps.get(index);
      return switch (kind) {
        case NONE -> null;
        case AT -> at;
        case JUST_BEFORE -> at.minusMillis(1);
        case JUST_AFTER -> at.plusMillis(1);
        case HALFWAY_TO_NEXT ->
            index + 1 < timestamps.size()
                ? at.plus(Duration.between(at, timestamps.get(index + 1)).dividedBy(2))
                : at.plusSeconds(1);
        case BEFORE_ALL -> timestamps.getFirst().minus(Duration.ofHours(1));
        case AFTER_ALL -> timestamps.getLast().plus(Duration.ofHours(1));
      };
    }
  }

  enum PositionKind {
    NOTHING_EXPORTED,
    NEAR_CHECKPOINT,
    NEAR_FIRST_LOG_POSITION,
    ANYWHERE
  }

  /** An exported position relative to the positions a partition recorded. */
  record PositionChoice(PositionKind kind, int selector, int offset) {

    long resolve(final BackupMetadata partition) {
      final var checkpoints = partition.checkpoints();
      if (kind == PositionKind.NOTHING_EXPORTED || checkpoints.isEmpty()) {
        return -1;
      }
      final var checkpoint = checkpoints.get(selector % checkpoints.size());
      return switch (kind) {
        case NOTHING_EXPORTED -> -1;
        case NEAR_CHECKPOINT -> checkpoint.checkpointPosition() + offset;
        case NEAR_FIRST_LOG_POSITION ->
            checkpoint.firstLogPosition().orElse(checkpoint.checkpointPosition()) + offset;
        case ANYWHERE -> selector % (checkpoints.getLast().checkpointPosition() + 2);
      };
    }
  }

  /**
   * A time range and, if the restore has secondary storage, an exported position per partition.
   * {@code from} is drawn near backups, which ranges start and end at, and {@code to} near any
   * checkpoint. {@code to} is dropped if it is before {@code from}, which the restore API rejects.
   */
  record Query(
      TimeChoice fromChoice,
      TimeChoice toChoice,
      boolean withExportedPositions,
      List<PositionChoice> exported) {

    @Nullable Instant from(final History history) {
      return ordered(history)[0];
    }

    @Nullable Instant to(final History history) {
      return ordered(history)[1];
    }

    @Nullable Map<Integer, Long> exportedPositions(final History history) {
      if (!withExportedPositions) {
        return null;
      }
      final var positions = new HashMap<Integer, Long>();
      for (final var partition : history.metadata()) {
        positions.put(
            partition.partitionId(), exported.get(partition.partitionId() - 1).resolve(partition));
      }
      return positions;
    }

    private @Nullable Instant[] ordered(final History history) {
      final var from = fromChoice.resolve(history.timestamps(true));
      final var to = toChoice.resolve(history.timestamps(false));
      if (from != null && to != null && from.isAfter(to)) {
        return new Instant[] {from, null};
      }
      return new Instant[] {from, to};
    }
  }
}
