/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore.logarchive;

import io.camunda.zeebe.backup.logarchive.format.DecodedLogObject;
import io.camunda.zeebe.backup.logarchive.format.LogObjectDecoder;
import io.camunda.zeebe.backup.logarchive.leader.LogArchiverTimeline;
import io.camunda.zeebe.backup.logarchive.leader.LogArchiverTimeline.Takeover;
import io.camunda.zeebe.backup.logarchive.model.ArchivedSnapshotJson;
import io.camunda.zeebe.backup.logarchive.model.CoveredCheckpointJson;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveJson;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveKeys;
import io.camunda.zeebe.backup.logarchive.model.LogObjectJson;
import io.camunda.zeebe.backup.logarchive.model.SnapshotDescriptorFile;
import io.camunda.zeebe.backup.logarchive.store.LogArchiveStore;
import io.camunda.zeebe.journal.CheckedJournalException.FlushException;
import io.camunda.zeebe.journal.JournalMetaStore.InMemory;
import io.camunda.zeebe.journal.file.SegmentedJournal;
import io.camunda.zeebe.restore.logarchive.HandoverRestorePlan.BridgedFromArchive;
import io.camunda.zeebe.snapshots.SnapshotFileInfoProvider;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.camunda.zeebe.util.FileUtil;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Restores partitions from the log archive (spec section 13).
 *
 * <p>Every broker restores its own partitions independently. All brokers read the same archive and
 * resolve the same restore point, so they rebuild identical journals and agree on the new timeline
 * id without coordinating.
 */
public final class LogArchiveRestore {

  /** File in the partition data directory holding the timeline the partition archives to. */
  public static final String TIMELINE_FILE_NAME = "log-archive-timeline";

  private static final Logger LOG = LoggerFactory.getLogger(LogArchiveRestore.class);

  private final LogArchiveStore store;
  private final SnapshotFileInfoProvider fileInfoProvider;
  private final MeterRegistry meterRegistry;

  public LogArchiveRestore(
      final LogArchiveStore store,
      final SnapshotFileInfoProvider fileInfoProvider,
      final MeterRegistry meterRegistry) {
    this.store = store;
    this.fileInfoProvider = fileInfoProvider;
    this.meterRegistry = meterRegistry;
  }

  /**
   * Restores the given local partitions of a broker to one checkpoint of a timeline. The restore
   * point is resolved against the archives of all {@code partitionCount} partitions, not only the
   * local ones, so that every broker picks the same one.
   *
   * @param timelineId the timeline to restore from
   * @param partitionCount the number of partitions of the cluster
   * @param checkpointId the checkpoint to restore to, or empty for the newest checkpoint that the
   *     archives of all partitions cover
   * @param targets the partitions to restore on this broker
   * @return the restore point and the result per restored partition
   * @throws LogArchiveRestoreException if the archive does not allow restoring to the checkpoint
   */
  public LogArchiveRestoreResult restorePartitions(
      final String timelineId,
      final int partitionCount,
      final Optional<Long> checkpointId,
      final Collection<LogArchiveRestoreTarget> targets) {
    for (final var target : targets) {
      if (target.partitionId() < 1 || target.partitionId() > partitionCount) {
        throw new IllegalArgumentException(
            "Partition %d is outside of the partition count %d"
                .formatted(target.partitionId(), partitionCount));
      }
    }

    final var views = new HashMap<Integer, LogArchiveView>();
    IntStream.rangeClosed(1, partitionCount)
        .forEach(id -> views.put(id, LogArchiveView.read(store, timelineId, id)));
    final var restorePoint = LogArchiveRestorePointResolver.resolve(views, checkpointId);
    LOG.info(
        "Restoring partitions {} from log archive timeline '{}' to checkpoint {}",
        targets.stream().map(LogArchiveRestoreTarget::partitionId).sorted().toList(),
        timelineId,
        restorePoint.checkpointId());

    final var results = new TreeMap<Integer, LogArchivePartitionRestoreResult>();
    for (final var target : targets) {
      final var view = Objects.requireNonNull(views.get(target.partitionId()));
      results.put(
          target.partitionId(), restorePartition(view, restorePoint.checkpointId(), target));
    }
    return new LogArchiveRestoreResult(
        restorePoint, restoredTimelineId(timelineId, restorePoint.checkpointId()), results);
  }

  /**
   * Restores one partition to a checkpoint its archive covers, without looking at other partitions.
   * Callers that restore a cluster must resolve a common checkpoint first, e.g. with {@link
   * LogArchiveRestorePointResolver}.
   *
   * @param view the folded archive of the partition, see {@link LogArchiveView#read}
   * @param checkpointId the checkpoint to restore to
   * @param target where to restore to; its data directory must be empty or not exist
   * @throws LogArchiveRestoreException if the archive does not allow restoring to the checkpoint
   */
  public LogArchivePartitionRestoreResult restorePartition(
      final LogArchiveView view, final long checkpointId, final LogArchiveRestoreTarget target) {
    return restorePartition(view, checkpointId, target, Optional.empty());
  }

  /**
   * Like {@link #restorePartition(LogArchiveView, long, LogArchiveRestoreTarget)}, but the rebuilt
   * journal also contains the log after {@code exportedPosition}, so a secondary storage that
   * exported up to it resumes exporting without a gap. The journal then starts at the object
   * holding {@code exportedPosition + 1} if that is below the snapshot, see {@link
   * LogArchiveRestorePlanner#plan(LogArchiveView, CoveredCheckpointJson, Optional)}.
   *
   * @param exportedPosition the position the secondary storage exported up to, empty to start the
   *     journal at the snapshot
   */
  public LogArchivePartitionRestoreResult restorePartition(
      final LogArchiveView view,
      final long checkpointId,
      final LogArchiveRestoreTarget target,
      final Optional<Long> exportedPosition) {
    if (view.partitionId() != target.partitionId()) {
      throw new IllegalArgumentException(
          "View of partition %d cannot restore partition %d"
              .formatted(view.partitionId(), target.partitionId()));
    }
    final var checkpoint =
        view.findCheckpoint(checkpointId)
            .orElseThrow(
                () ->
                    new LogArchiveRestoreException(
                        "Checkpoint %d is not covered by the log archive of partition %d (timeline '%s')"
                            .formatted(checkpointId, view.partitionId(), view.timelineId())));
    final var plan = LogArchiveRestorePlanner.plan(view, checkpoint, exportedPosition);
    LOG.info(
        "Restoring partition {} to checkpoint {} at position {}: snapshot {}, objects {} to {}",
        plan.partitionId(),
        checkpointId,
        checkpoint.checkpointPosition(),
        plan.snapshot().map(ArchivedSnapshotJson::id).orElse("<none>"),
        plan.objects().getFirst().key(),
        plan.objects().getLast().key());

    final var dataDirectory = target.dataDirectory();
    try {
      ensureEmptyDirectory(dataDirectory);
      if (plan.snapshot().isPresent()) {
        installSnapshot(view, plan.snapshot().get(), target);
      }
      final long lastIndex = rebuildJournal(view, plan, target);
      verifyJournal(plan, target);
      final var newTimelineId = restoredTimelineId(view.timelineId(), checkpointId);
      writeTimelineFile(dataDirectory, newTimelineId);
      writeTakeoverFile(
          dataDirectory, view.timelineId(), lastIndex, checkpoint.checkpointPosition());
      LOG.info(
          "Restored partition {} to checkpoint {}: journal {} to {}, new timeline '{}'",
          plan.partitionId(),
          checkpointId,
          plan.firstIndex(),
          lastIndex,
          newTimelineId);
      return new LogArchivePartitionRestoreResult(
          plan.partitionId(),
          checkpointId,
          checkpoint.checkpointPosition(),
          plan.snapshot().map(ArchivedSnapshotJson::id),
          plan.firstIndex(),
          lastIndex,
          newTimelineId);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    } catch (final FlushException e) {
      throw new LogArchiveRestoreException(
          "Failed to flush the restored journal of partition %d".formatted(plan.partitionId()), e);
    }
  }

  /**
   * Completes a bridged restore of one partition (spec section 18.5a): the old backups of the plan
   * were already restored into the target's journal without their snapshot (see {@code
   * PartitionRestoreService#restoreLog}). Truncates that journal after {@code switchIndex - 1},
   * appends the archived frames from {@code switchIndex} up to the checkpoint, then installs the
   * archived snapshot and writes the timeline file, as {@link #restorePartition} does.
   *
   * <p>Fails, writing nothing more, unless the journal is one log at the seam: the old journal
   * starts at or before the exported position + 1 and reaches at least to {@code switchIndex}, and
   * every archived frame it also holds has the same asqn and checksum (the checksum covers the
   * whole serialized entry: index, asqn and data).
   *
   * @param bridged the plan of the partition
   * @param target where to restore to; holds the journal restored from the old backups
   * @throws LogArchiveRestoreException if the archive does not allow restoring to the checkpoint,
   *     or the old journal and the archive do not form one log
   */
  public LogArchivePartitionRestoreResult restoreBridgedPartition(
      final BridgedFromArchive bridged, final LogArchiveRestoreTarget target) {
    final var view = bridged.view();
    if (view.partitionId() != target.partitionId()) {
      throw new IllegalArgumentException(
          "View of partition %d cannot restore partition %d"
              .formatted(view.partitionId(), target.partitionId()));
    }
    final var checkpoint = bridged.checkpoint();
    final long checkpointId = checkpoint.checkpointId();
    final var plan =
        LogArchiveRestorePlanner.planBridged(view, checkpoint, bridged.exportedPosition());
    if (plan.firstIndex() != bridged.switchIndex()) {
      throw failure(
          plan,
          "the archived objects start at index %d, not at the switch index %d"
              .formatted(plan.firstIndex(), bridged.switchIndex()));
    }
    LOG.info(
        "Restoring partition {} bridged to checkpoint {} at position {}: old backups {} up to index"
            + " {}, then timeline '{}' from switch index {} (objects {} to {}), snapshot {}",
        plan.partitionId(),
        checkpointId,
        checkpoint.checkpointPosition(),
        bridged.oldBackupIds(),
        bridged.switchIndex() - 1,
        view.timelineId(),
        bridged.switchIndex(),
        plan.objects().getFirst().key(),
        plan.objects().getLast().key(),
        plan.snapshot().map(ArchivedSnapshotJson::id).orElse("<none>"));

    try {
      final var range = bridgeJournal(view, plan, bridged, target);
      if (plan.snapshot().isPresent()) {
        installSnapshot(view, plan.snapshot().get(), target);
      }
      verifyJournal(plan, target);
      final var newTimelineId = restoredTimelineId(view.timelineId(), checkpointId);
      writeTimelineFile(target.dataDirectory(), newTimelineId);
      writeTakeoverFile(
          target.dataDirectory(), view.timelineId(), range[1], checkpoint.checkpointPosition());
      LOG.info(
          "Restored partition {} bridged to checkpoint {}: journal {} to {} (old backups up to {},"
              + " log archive from {}), new timeline '{}'",
          plan.partitionId(),
          checkpointId,
          range[0],
          range[1],
          bridged.switchIndex() - 1,
          bridged.switchIndex(),
          newTimelineId);
      return new LogArchivePartitionRestoreResult(
          plan.partitionId(),
          checkpointId,
          checkpoint.checkpointPosition(),
          plan.snapshot().map(ArchivedSnapshotJson::id),
          range[0],
          range[1],
          newTimelineId);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    } catch (final FlushException e) {
      throw new LogArchiveRestoreException(
          "Failed to flush the restored journal of partition %d".formatted(plan.partitionId()), e);
    }
  }

  /**
   * Joins the old journal in the target and the archived frames at the switch index.
   *
   * @return the first and the last index of the joined journal
   */
  private long[] bridgeJournal(
      final LogArchiveView view,
      final LogArchiveRestorePlan plan,
      final BridgedFromArchive bridged,
      final LogArchiveRestoreTarget target)
      throws FlushException {
    final long switchIndex = bridged.switchIndex();
    final long checkpointPosition = plan.checkpoint().checkpointPosition();
    try (final var journal = openJournal(target)) {
      if (journal.isEmpty()) {
        throw failure(plan, "the old backups restored no journal to bridge from");
      }
      final long firstIndex = journal.getFirstIndex();
      final long oldLastIndex = journal.getLastIndex();
      if (firstIndex >= switchIndex || oldLastIndex < switchIndex) {
        throw failure(
            plan,
            ("the journal of the old backups holds indexes %d to %d; it must start before and"
                    + " reach the switch index %d to overlap with the archive")
                .formatted(firstIndex, oldLastIndex, switchIndex));
      }
      final var overlap = new long[2][(int) (oldLastIndex - switchIndex + 1)];
      final long lastOldAsqn = readOldSide(journal, plan, bridged, overlap);

      journal.deleteAfter(switchIndex - 1);
      long expectedIndex = switchIndex;
      boolean continued = false;
      for (final var object : plan.objects()) {
        for (final var frame : download(view, object).frames()) {
          if (frame.index() != expectedIndex) {
            throw failure(
                plan,
                "object %s holds index %d where %d was expected"
                    .formatted(object.key(), frame.index(), expectedIndex));
          }
          if (frame.index() <= oldLastIndex) {
            final int offset = (int) (frame.index() - switchIndex);
            if (frame.asqn() != overlap[0][offset] || frame.checksum() != overlap[1][offset]) {
              throw failure(
                  plan,
                  ("the old backups and the log archive differ at index %d: asqn %d and checksum"
                          + " %d in the old backups, asqn %d and checksum %d in %s; they are not"
                          + " the same log")
                      .formatted(
                          frame.index(),
                          overlap[0][offset],
                          overlap[1][offset],
                          frame.asqn(),
                          frame.checksum(),
                          object.key()));
            }
          }
          if (!continued && frame.asqn() >= 0) {
            if (frame.asqn() <= lastOldAsqn) {
              throw failure(
                  plan,
                  ("the first archived entry at index %d has asqn %d, not after the last asqn %d"
                          + " of the old backups before the switch index")
                      .formatted(frame.index(), frame.asqn(), lastOldAsqn));
            }
            continued = true;
          }
          if (frame.asqn() > checkpointPosition) {
            throw missingCheckpointFrame(plan, frame.index(), frame.asqn());
          }
          final var record = journal.append(frame.checksum(), frame.serializedRecord());
          if (record.index() != frame.index() || record.asqn() != frame.asqn()) {
            throw failure(
                plan,
                "appended record (index %d, asqn %d) differs from frame (index %d, asqn %d) of %s"
                    .formatted(
                        record.index(), record.asqn(), frame.index(), frame.asqn(), object.key()));
          }
          if (frame.asqn() == checkpointPosition) {
            journal.flush();
            return new long[] {firstIndex, frame.index()};
          }
          expectedIndex++;
        }
      }
      throw missingCheckpointFrame(plan, expectedIndex - 1, -1);
    }
  }

  /**
   * Checks that the old journal starts early enough for the exporter and records the asqn and
   * checksum of the entries from the switch index on, which the archive must repeat.
   *
   * @param overlap filled with the asqns ({@code [0]}) and checksums ({@code [1]}) from the switch
   *     index on
   * @return the last asqn before the switch index, -1 if there is none
   */
  private static long readOldSide(
      final SegmentedJournal journal,
      final LogArchiveRestorePlan plan,
      final BridgedFromArchive bridged,
      final long[][] overlap) {
    final long switchIndex = bridged.switchIndex();
    final long required = Math.max(bridged.exportedPosition() + 1, 1);
    long firstAsqn = -1;
    long lastAsqn = -1;
    try (final var reader = journal.openReader()) {
      while (reader.hasNext()) {
        final var record = reader.next();
        if (record.index() >= switchIndex) {
          final int offset = (int) (record.index() - switchIndex);
          overlap[0][offset] = record.asqn();
          overlap[1][offset] = record.checksum();
        } else if (record.asqn() >= 0) {
          firstAsqn = firstAsqn < 0 ? record.asqn() : firstAsqn;
          lastAsqn = record.asqn();
        }
      }
    }
    if (firstAsqn < 0 || firstAsqn > required) {
      throw failure(
          plan,
          ("the journal of the old backups starts at asqn %d before the switch index %d, after the"
                  + " exported position %d + 1")
              .formatted(firstAsqn, switchIndex, bridged.exportedPosition()));
    }
    return lastAsqn;
  }

  /** The timeline a partition restored to a checkpoint archives to; deterministic on purpose. */
  public static String restoredTimelineId(final String sourceTimelineId, final long checkpointId) {
    return sourceTimelineId + ".r" + checkpointId;
  }

  /** The timeline a partition restored from a classic backup archives to. */
  public static String classicRestoredTimelineId(
      final String configuredTimeline, final long backupId) {
    return configuredTimeline + ".b" + backupId;
  }

  private static void ensureEmptyDirectory(final Path directory) throws IOException {
    if (Files.exists(directory) && !FileUtil.isEmpty(directory)) {
      throw new LogArchiveRestoreException(
          "Expected the partition data directory %s to be empty".formatted(directory));
    }
    FileUtil.ensureDirectoryExists(directory);
  }

  private void installSnapshot(
      final LogArchiveView view,
      final ArchivedSnapshotJson snapshot,
      final LogArchiveRestoreTarget target)
      throws IOException {
    final var prefix = view.partitionPrefix();
    final var descriptorKey = prefix + LogArchiveKeys.snapshotDescriptorKey(snapshot.id());
    final var descriptor =
        LogArchiveJson.read(
            store
                .getIfExists(descriptorKey)
                .join()
                .orElseThrow(
                    () ->
                        new LogArchiveRestoreException(
                            "Snapshot descriptor %s is missing".formatted(descriptorKey))),
            SnapshotDescriptorFile.class);
    verifyDescriptor(snapshot, descriptor, descriptorKey);

    final var downloadDirectory =
        target.dataDirectory().resolve("restoring-log-archive-snapshot-" + snapshot.id());
    FileUtil.ensureDirectoryExists(downloadDirectory);
    try {
      final var files = new LinkedHashMap<String, Path>();
      for (final var fileName : descriptor.files()) {
        final var file = downloadDirectory.resolve(fileName);
        store
            .getToFile(prefix + LogArchiveKeys.snapshotFileKey(snapshot.id(), fileName), file)
            .join();
        files.put(fileName, file);
      }

      @SuppressWarnings("resource") // like PartitionRestoreService: never started as an actor
      final var snapshotStore =
          new FileBasedSnapshotStore(
              target.brokerId(),
              target.partitionId(),
              target.dataDirectory(),
              fileInfoProvider,
              meterRegistry);
      snapshotStore.restore(snapshot.id(), files);
    } finally {
      FileUtil.deleteFolderIfExists(downloadDirectory);
    }
  }

  private static void verifyDescriptor(
      final ArchivedSnapshotJson snapshot,
      final SnapshotDescriptorFile descriptor,
      final String descriptorKey) {
    if (!descriptor.id().equals(snapshot.id())
        || descriptor.index() != snapshot.index()
        || descriptor.processedPosition() != snapshot.processedPosition()
        || descriptor.lastFollowupEventPosition() != snapshot.lastFollowupEventPosition()
        || descriptor.maxExportedPosition() != snapshot.maxExportedPosition()
        || descriptor.files().size() != snapshot.fileCount()) {
      throw new LogArchiveRestoreException(
          "Snapshot descriptor %s does not match the archived snapshot %s"
              .formatted(descriptorKey, snapshot));
    }
    for (final var fileName : descriptor.files()) {
      if (fileName.isEmpty()
          || fileName.equals(".")
          || fileName.equals("..")
          || fileName.contains("/")
          || fileName.contains("\\")) {
        throw new LogArchiveRestoreException(
            "Snapshot descriptor %s lists an invalid file name '%s'"
                .formatted(descriptorKey, fileName));
      }
    }
  }

  /**
   * Appends every frame from the first object's first index up to and including the frame whose
   * asqn is the checkpoint position. The frames carry the serialized journal records and their
   * checksums, so the journal verifies each record while appending it and the result is
   * byte-identical to the source journal.
   *
   * @return the last index of the rebuilt journal
   */
  private long rebuildJournal(
      final LogArchiveView view,
      final LogArchiveRestorePlan plan,
      final LogArchiveRestoreTarget target)
      throws FlushException {
    final long checkpointPosition = plan.checkpoint().checkpointPosition();
    try (final var journal = openJournal(target)) {
      journal.reset(plan.firstIndex());
      long expectedIndex = plan.firstIndex();
      for (final var object : plan.objects()) {
        for (final var frame : download(view, object).frames()) {
          if (frame.index() != expectedIndex) {
            throw failure(
                plan,
                "object %s holds index %d where %d was expected"
                    .formatted(object.key(), frame.index(), expectedIndex));
          }
          if (frame.asqn() > checkpointPosition) {
            throw missingCheckpointFrame(plan, frame.index(), frame.asqn());
          }
          final var record = journal.append(frame.checksum(), frame.serializedRecord());
          if (record.index() != frame.index() || record.asqn() != frame.asqn()) {
            throw failure(
                plan,
                "appended record (index %d, asqn %d) differs from frame (index %d, asqn %d) of %s"
                    .formatted(
                        record.index(), record.asqn(), frame.index(), frame.asqn(), object.key()));
          }
          if (frame.asqn() == checkpointPosition) {
            journal.flush();
            return frame.index();
          }
          expectedIndex++;
        }
      }
      throw missingCheckpointFrame(plan, expectedIndex - 1, -1);
    }
  }

  /**
   * The consistency checks of the TODO in {@code PartitionRestoreService}, on the reopened journal.
   */
  private void verifyJournal(
      final LogArchiveRestorePlan plan, final LogArchiveRestoreTarget target) {
    try (final var journal = openJournal(target);
        final var reader = journal.openReader()) {
      if (journal.getFirstIndex() > plan.snapshotIndex() + 1) {
        throw failure(
            plan,
            "the restored journal starts at index %d, after the snapshot index %d + 1"
                .formatted(journal.getFirstIndex(), plan.snapshotIndex()));
      }
      reader.seekToLast();
      final long lastAsqn = reader.hasNext() ? reader.next().asqn() : -1;
      if (lastAsqn != plan.checkpoint().checkpointPosition()) {
        throw failure(
            plan,
            "the last entry of the restored journal has asqn %d, not the checkpoint position %d"
                .formatted(lastAsqn, plan.checkpoint().checkpointPosition()));
      }
    }
  }

  private SegmentedJournal openJournal(final LogArchiveRestoreTarget target) {
    return SegmentedJournal.builder(meterRegistry)
        .withDirectory(target.dataDirectory().toFile())
        .withName(target.journalName())
        .withMetaStore(new InMemory())
        .build();
  }

  private DecodedLogObject download(final LogArchiveView view, final LogObjectJson object) {
    final var key = view.partitionPrefix() + object.key();
    final var bytes =
        store
            .getIfExists(key)
            .join()
            .orElseThrow(
                () -> new LogArchiveRestoreException("Log object %s is missing".formatted(key)));
    if (bytes.length != object.sizeBytes()
        || !LogArchiveView.sha256Hex(bytes).equals(object.sha256())) {
      throw new LogArchiveRestoreException(
          "Log object %s does not match its size %d and sha256 %s"
              .formatted(key, object.sizeBytes(), object.sha256()));
    }
    final var decoded = LogObjectDecoder.decode(bytes);
    final var header = decoded.header();
    if (header.firstIndex() != object.firstIndex()
        || header.lastIndex() != object.lastIndex()
        || header.partitionId() != view.partitionId()) {
      throw new LogArchiveRestoreException(
          "Log object %s holds indexes %d to %d of partition %d, but is described as %s"
              .formatted(
                  key, header.firstIndex(), header.lastIndex(), header.partitionId(), object));
    }
    return decoded;
  }

  /**
   * Durably writes the timeline the partition in the data directory archives to from now on; the
   * archiver prefers it over the configured timeline.
   */
  public static void writeTimelineFile(final Path dataDirectory, final String timelineId)
      throws IOException {
    final var file = dataDirectory.resolve(TIMELINE_FILE_NAME);
    final var temporary = dataDirectory.resolve(TIMELINE_FILE_NAME + ".tmp");
    Files.writeString(temporary, timelineId, StandardCharsets.UTF_8);
    FileUtil.flush(temporary);
    FileUtil.moveDurably(temporary, file, StandardCopyOption.ATOMIC_MOVE);
  }

  /**
   * Durably writes where the new timeline takes over its parent, next to the timeline file: the
   * Raft index and position of the restore point's entry in the source timeline. The archiver puts
   * it on {@code START_TIMELINE} (spec section 19.3).
   */
  public static void writeTakeoverFile(
      final Path dataDirectory,
      final String parentTimelineId,
      final long parentIndex,
      final long parentPosition)
      throws IOException {
    final var file = dataDirectory.resolve(LogArchiverTimeline.TAKEOVER_FILE_NAME);
    final var temporary = dataDirectory.resolve(LogArchiverTimeline.TAKEOVER_FILE_NAME + ".tmp");
    Files.write(temporary, new Takeover(parentTimelineId, parentIndex, parentPosition).toJson());
    FileUtil.flush(temporary);
    FileUtil.moveDurably(temporary, file, StandardCopyOption.ATOMIC_MOVE);
  }

  /**
   * Removes a takeover file left by an earlier restore into the directory, for restores that start
   * a timeline without a known takeover point (the classic restore from old backups).
   */
  public static void deleteTakeoverFile(final Path dataDirectory) throws IOException {
    Files.deleteIfExists(dataDirectory.resolve(LogArchiverTimeline.TAKEOVER_FILE_NAME));
  }

  private static LogArchiveRestoreException missingCheckpointFrame(
      final LogArchiveRestorePlan plan, final long index, final long asqn) {
    return failure(
        plan,
        "found no entry with asqn equal to the checkpoint position %d (stopped at index %d, asqn %d)"
            .formatted(plan.checkpoint().checkpointPosition(), index, asqn));
  }

  private static LogArchiveRestoreException failure(
      final LogArchiveRestorePlan plan, final String detail) {
    return new LogArchiveRestoreException(
        "Cannot restore partition %d to checkpoint %d: %s"
            .formatted(plan.partitionId(), plan.checkpoint().checkpointId(), detail));
  }
}
