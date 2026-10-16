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
import io.camunda.zeebe.backup.logarchive.model.ArchivedSnapshotJson;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveJson;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveKeys;
import io.camunda.zeebe.backup.logarchive.model.LogObjectJson;
import io.camunda.zeebe.backup.logarchive.model.SnapshotDescriptorFile;
import io.camunda.zeebe.backup.logarchive.store.LogArchiveStore;
import io.camunda.zeebe.journal.CheckedJournalException.FlushException;
import io.camunda.zeebe.journal.JournalMetaStore.InMemory;
import io.camunda.zeebe.journal.file.SegmentedJournal;
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
    final var plan = LogArchiveRestorePlanner.plan(view, checkpoint);
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
