/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore.logarchive;

import static io.camunda.zeebe.restore.logarchive.TimelineFixture.checkpoint;
import static io.camunda.zeebe.restore.logarchive.TimelineFixture.timestamp;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.backup.common.BackupMetadata;
import io.camunda.zeebe.backup.common.BackupMetadata.CheckpointEntry;
import io.camunda.zeebe.backup.common.BackupMetadata.RangeEntry;
import io.camunda.zeebe.backup.logarchive.model.HandoverFile;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.restore.RestorePointResolver;
import io.camunda.zeebe.restore.logarchive.HandoverRestorePlan.BridgedFromArchive;
import io.camunda.zeebe.restore.logarchive.HandoverRestorePlan.FromArchive;
import io.camunda.zeebe.restore.logarchive.HandoverRestorePlan.OldBackups;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A time range resolves the same with the log archive as with continuous backup (spec section
 * 18.5a): each case runs the same {@code from}, {@code to} and exported positions E against a
 * continuous-backup-only cluster and an equivalent cluster that uses the log archive, and expects
 * the same restore point or the same error.
 *
 * <p>Checkpoint {@code id} is taken at minute {@code id}, at position {@code 10 * id}, on both
 * partitions. The continuous backups have backups at 1, 3, 5, 7 and 9 and markers in between.
 *
 * <ul>
 *   <li>Handed over: the old backups end at the handover H = 5; the archive started at index 25 and
 *       covers 3 to 9, with snapshots that make it hold the same backups 7 and 9 after H.
 *   <li>Archive only: the archive started at index 21 and covers 3 to 9, with snapshots that make
 *       it hold backups 3, 5, 7 and 9; the continuous backups to compare with have exactly these.
 * </ul>
 */
final class TimeRangeParityTest {

  private static final int[] PARTITIONS = {1, 2};
  private static final String TIMELINE = "tl";

  private final InMemoryLogArchiveStore store = new InMemoryLogArchiveStore();

  static Stream<Arguments> handedOverCases() {
    return Stream.of(
        Arguments.of("the latest without --to", null, null, 15L, "checkpoint 9"),
        Arguments.of(
            "--to just after a checkpoint", null, at(6).plusSeconds(10), 15L, "checkpoint 6"),
        Arguments.of(
            "--to just before a checkpoint", null, at(7).minusSeconds(10), 15L, "checkpoint 7"),
        Arguments.of("--to before E", null, at(2), 35L, "checkpoint 4"),
        Arguments.of(
            "--from before every range",
            at(1).minusSeconds(30),
            null,
            15L,
            "No usable range found"),
        Arguments.of("--from trimmed to a backup at or below E", at(3), null, 35L, "checkpoint 9"),
        Arguments.of(
            "--from trimmed past E's backup", at(4), null, 35L, "After discarding backups"),
        Arguments.of(
            "--from before H and --to after H", at(1), at(8).plusSeconds(10), 15L, "checkpoint 8"),
        Arguments.of("--from after H", at(6), null, 75L, "checkpoint 9"),
        Arguments.of(
            "--from after H, trimmed past E's backup",
            at(6),
            null,
            65L,
            "After discarding backups"),
        Arguments.of("E ahead of every checkpoint", null, null, 1_000L, "No usable range found"),
        Arguments.of("E before the log", null, null, -1L, "checkpoint 9"),
        Arguments.of("without RDBMS", at(1), at(6), null, "checkpoint 6"),
        Arguments.of("without RDBMS, --from after H", at(7), null, null, "checkpoint 9"));
  }

  static Stream<Arguments> archiveOnlyCases() {
    return Stream.of(
        Arguments.of("the latest without --to", null, null, 25L, "checkpoint 9"),
        Arguments.of(
            "--to just after a checkpoint", null, at(6).plusSeconds(10), 25L, "checkpoint 6"),
        Arguments.of(
            "--to just before a checkpoint", null, at(7).minusSeconds(10), 25L, "checkpoint 7"),
        Arguments.of(
            "--from before every range",
            at(3).minusSeconds(30),
            null,
            25L,
            "No usable range found"),
        Arguments.of("--from trimmed to a backup at or below E", at(5), null, 55L, "checkpoint 9"),
        Arguments.of(
            "--from trimmed past E's backup", at(6), null, 55L, "After discarding backups"),
        Arguments.of("E before the archive", null, null, 15L, "No usable range found"),
        Arguments.of("E ahead of every checkpoint", null, null, 1_000L, "No usable range found"),
        Arguments.of("without RDBMS", at(3), at(8), null, "checkpoint 8"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("handedOverCases")
  void shouldResolveAHandedOverClusterLikeContinuousBackup(
      final String name,
      final @Nullable Instant from,
      final @Nullable Instant to,
      final @Nullable Long exported,
      final String outcome) {
    // given
    final var continuousBackup = List.of(continuousBackup(1), continuousBackup(2));
    final var handedOver = Map.of(1, handedOver(1), 2, handedOver(2));

    // when
    final var expected = continuousBackupOutcome(continuousBackup, from, to, exported);
    final var actual = logArchiveOutcome(handedOver, from, to, exported);

    // then
    assertThat(actual).isEqualTo(expected).contains(outcome);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("archiveOnlyCases")
  void shouldResolveAnArchiveOnlyClusterLikeContinuousBackup(
      final String name,
      final @Nullable Instant from,
      final @Nullable Instant to,
      final @Nullable Long exported,
      final String outcome) {
    // given
    final var continuousBackup = List.of(archiveEquivalent(1), archiveEquivalent(2));
    final var archiveOnly = Map.of(1, archiveOnly(1), 2, archiveOnly(2));

    // when
    final var expected = continuousBackupOutcome(continuousBackup, from, to, exported);
    final var actual = logArchiveOutcome(archiveOnly, from, to, exported);

    // then
    assertThat(actual).isEqualTo(expected).contains(outcome);
  }

  @Test
  void shouldRestoreACheckpointBeforeTheHandoverFromTheOldBackups() {
    // given
    final var sources = Map.of(1, handedOver(1), 2, handedOver(2));

    // when
    final var plan =
        HandoverRestoreResolver.resolve(
            sources, HandoverRestoreRequest.ofTimeRange(null, at(2), exported(35L)));

    // then - the same backups as continuous backup restores
    assertThat(plan.checkpointId()).isEqualTo(4);
    assertThat(plan.partitions().values()).containsOnly(new OldBackups(List.of(3L, 5L)));
  }

  @Test
  void shouldRestoreACheckpointAfterTheHandoverFromTheArchive() {
    // given - the archive reaches back to E + 1
    final var sources = Map.of(1, handedOver(1), 2, handedOver(2));

    // when
    final var plan =
        HandoverRestoreResolver.resolve(
            sources, HandoverRestoreRequest.ofTimeRange(at(3), null, exported(35L)));

    // then
    assertThat(plan.checkpointId()).isEqualTo(9);
    assertThat(plan.partitions().values())
        .allSatisfy(
            partition ->
                assertThat(partition)
                    .isInstanceOfSatisfying(
                        FromArchive.class,
                        archived -> assertThat(archived.exportedPosition()).contains(35L)));
  }

  @Test
  void shouldBridgeACheckpointAfterTheHandoverWhenTheArchiveStartsAfterE() {
    // given - E + 1 lies before the archive start 25; --from in the old range, --to after H
    final var sources = Map.of(1, handedOver(1), 2, handedOver(2));

    // when
    final var plan =
        HandoverRestoreResolver.resolve(
            sources,
            HandoverRestoreRequest.ofTimeRange(at(1), at(8).plusSeconds(10), exported(15L)));

    // then - the old backups from the one at or below E up to the first one reaching into the
    // archive, then the archive
    assertThat(plan.checkpointId()).isEqualTo(8);
    assertThat(plan.partitions().values())
        .allSatisfy(
            partition ->
                assertThat(partition)
                    .isInstanceOfSatisfying(
                        BridgedFromArchive.class,
                        bridged -> {
                          assertThat(bridged.oldBackupIds()).containsExactly(1L, 3L);
                          assertThat(bridged.switchIndex()).isEqualTo(25);
                        }));
  }

  // ---- fixtures ------------------------------------------------------------------------------

  /** Backups at 1, 3, 5 (the handover) 7 and 9, markers in between, in one range. */
  private static BackupMetadata continuousBackup(final int partitionId) {
    return metadata(partitionId, 1, 9, 1);
  }

  /** The old backups of the handed-over cluster: those of {@link #continuousBackup} up to H. */
  private static BackupMetadata oldBackups(final int partitionId) {
    return metadata(partitionId, 1, 5, 1);
  }

  /** The backups an archive from index 21 holds: 3, 5, 7 and 9, markers in between. */
  private static BackupMetadata archiveEquivalent(final int partitionId) {
    return metadata(partitionId, 3, 9, 21);
  }

  private static BackupMetadata metadata(
      final int partitionId, final long first, final long last, final long firstLogPosition) {
    final var checkpoints = new ArrayList<CheckpointEntry>();
    long logStart = firstLogPosition;
    for (long id = first; id <= last; id++) {
      final long position = 10 * id;
      if (id % 2 == 1) {
        final var type =
            id == 5 && first == 1 ? CheckpointType.HANDOVER : CheckpointType.SCHEDULED_BACKUP;
        checkpoints.add(new CheckpointEntry(id, position, at(id), type, OptionalLong.of(logStart)));
        logStart = position + 1;
      } else {
        checkpoints.add(
            new CheckpointEntry(id, position, at(id), CheckpointType.MARKER, OptionalLong.empty()));
      }
    }
    return new BackupMetadata(
        BackupMetadata.VERSION,
        partitionId,
        Instant.EPOCH,
        checkpoints,
        List.of(new RangeEntry(first, last)));
  }

  private PartitionRestoreSources handedOver(final int partitionId) {
    final var archive =
        TimelineFixture.start(store, TIMELINE, partitionId, 25, "")
            .archive(34, checkpoint(3, 30))
            .archive(44, checkpoint(4, 40))
            .snapshot(42)
            .archive(54, checkpoint(5, 50))
            .handover(5, 50)
            .archive(64, checkpoint(6, 60))
            .snapshot(62)
            .archive(74, checkpoint(7, 70))
            .archive(84, checkpoint(8, 80))
            .snapshot(82)
            .archive(94, checkpoint(9, 90))
            .view();
    return new PartitionRestoreSources(
        partitionId,
        Optional.of(oldBackups(partitionId)),
        new TreeSet<>(List.of(1L, 3L, 5L)),
        List.of(new HandoverFile(1, partitionId, 5, 50, TIMELINE)),
        List.of(archive));
  }

  private PartitionRestoreSources archiveOnly(final int partitionId) {
    final var archive =
        TimelineFixture.start(store, TIMELINE, partitionId, 21, "")
            .archive(24)
            .snapshot(22)
            .archive(34, checkpoint(3, 30))
            .archive(44, checkpoint(4, 40))
            .snapshot(42)
            .archive(54, checkpoint(5, 50))
            .archive(64, checkpoint(6, 60))
            .snapshot(62)
            .archive(74, checkpoint(7, 70))
            .archive(84, checkpoint(8, 80))
            .snapshot(82)
            .archive(94, checkpoint(9, 90))
            .view();
    return new PartitionRestoreSources(
        partitionId, Optional.empty(), new TreeSet<>(), List.of(), List.of(archive));
  }

  // ---- outcomes ------------------------------------------------------------------------------

  private static String continuousBackupOutcome(
      final List<BackupMetadata> metadata,
      final @Nullable Instant from,
      final @Nullable Instant to,
      final @Nullable Long exported) {
    try {
      final var exportedPositions = exported == null ? null : exported(exported);
      return "checkpoint "
          + RestorePointResolver.resolve(metadata, from, to, exportedPositions)
              .globalCheckpointId();
    } catch (final RuntimeException e) {
      return failure(e);
    }
  }

  private static String logArchiveOutcome(
      final Map<Integer, PartitionRestoreSources> sources,
      final @Nullable Instant from,
      final @Nullable Instant to,
      final @Nullable Long exported) {
    try {
      final var request =
          HandoverRestoreRequest.ofTimeRange(
              from, to, exported == null ? Map.of() : exported(exported));
      return "checkpoint " + HandoverRestoreResolver.resolve(sources, request).checkpointId();
    } catch (final RuntimeException e) {
      return failure(e);
    }
  }

  private static String failure(final RuntimeException e) {
    return e.getClass().getSimpleName() + ": " + e.getMessage();
  }

  private static Map<Integer, Long> exported(final long position) {
    final var positions = new TreeMap<Integer, Long>();
    for (final int partition : PARTITIONS) {
      positions.put(partition, position);
    }
    return positions;
  }

  private static Instant at(final long checkpointId) {
    return Instant.ofEpochMilli(timestamp(checkpointId));
  }
}
