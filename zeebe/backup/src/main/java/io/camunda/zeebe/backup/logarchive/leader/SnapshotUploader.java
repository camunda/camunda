/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.leader;

import io.camunda.zeebe.backup.logarchive.model.LogArchiveJson;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveKeys;
import io.camunda.zeebe.backup.logarchive.model.SnapshotDescriptorFile;
import io.camunda.zeebe.backup.logarchive.processing.LogArchiveListener;
import io.camunda.zeebe.backup.logarchive.state.LogArchiveMeta;
import io.camunda.zeebe.backup.logarchive.store.LogArchiveStore;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.management.ArchivedSnapshot;
import io.camunda.zeebe.protocol.impl.record.value.management.LogArchiveRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.management.LogArchiveIntent;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ScheduledTimer;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.PersistedSnapshotStore;
import io.camunda.zeebe.snapshots.SnapshotReservation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Leader-only actor that uploads the newest local snapshot to the log archive and commits it with
 * an {@code ARCHIVE_SNAPSHOT} command. One upload at a time: {@code IDLE → RESERVED → UPLOADING →
 * AWAIT_COMMIT → IDLE}. The snapshot stays reserved until the command is accepted or rejected.
 */
@NullMarked
public final class SnapshotUploader extends Actor implements LogArchiveListener {

  static final Duration INITIAL_DELAY = Duration.ofSeconds(5);
  static final Duration AWAIT_COMMIT_TIMEOUT = Duration.ofMinutes(1);
  static final Duration WRITE_RETRY_MIN = Duration.ofMillis(500);
  static final Duration WRITE_RETRY_MAX = Duration.ofSeconds(10);

  private static final Logger LOG = LoggerFactory.getLogger(SnapshotUploader.class);

  private enum State {
    IDLE,
    RESERVED,
    UPLOADING,
    AWAIT_COMMIT
  }

  private final int partitionId;
  private final LogArchiveStore store;
  private final PersistedSnapshotStore snapshotStore;
  private final LogStreamWriter writer;
  private final Duration uploadInterval;

  private State state = State.IDLE;
  private boolean closed;
  private @Nullable String timelineId;
  private long archivedIndex = -1;
  private @Nullable String lastArchivedSnapshotId;

  // current attempt; every async callback checks the attempt number to ignore stale results
  private long attempt;
  private @Nullable SnapshotReservation reservation;
  private @Nullable ArchivedSnapshot pendingSnapshot;
  private @Nullable String currentId;
  private @Nullable ScheduledTimer awaitTimer;
  private Duration writeBackoff = WRITE_RETRY_MIN;

  public SnapshotUploader(
      final int partitionId,
      final LogArchiveStore store,
      final PersistedSnapshotStore snapshotStore,
      final LogStreamWriter writer,
      final Duration uploadInterval) {
    super("SnapshotUploader-" + partitionId);
    this.partitionId = partitionId;
    this.store = store;
    this.snapshotStore = snapshotStore;
    this.writer = writer;
    this.uploadInterval = uploadInterval;
  }

  @Override
  protected void onActorStarted() {
    actor.runAtFixedRate(uploadInterval, this::tick);
  }

  @Override
  protected void onActorClosing() {
    closed = true;
    attempt++;
    cancelAwaitTimer();
    release();
    state = State.IDLE;
  }

  @Override
  public void onRecovered(final LogArchiveMeta meta) {
    final var recoveredTimeline = meta.getTimelineId();
    final var recoveredIndex = meta.getArchivedIndex();
    actor.run(
        () -> {
          if (!recoveredTimeline.isEmpty()) {
            timelineId = recoveredTimeline;
          }
          archivedIndex = recoveredIndex;
          actor.schedule(INITIAL_DELAY, this::tick);
        });
  }

  @Override
  public void onTimelineStarted(final String startedTimelineId) {
    actor.run(() -> timelineId = startedTimelineId);
  }

  @Override
  public void onLogArchived(final long newArchivedIndex, final long archivedPosition) {
    actor.run(() -> archivedIndex = newArchivedIndex);
  }

  @Override
  public void onSnapshotArchived(final String snapshotId) {
    actor.run(
        () -> {
          if (state == State.AWAIT_COMMIT && snapshotId.equals(currentId)) {
            lastArchivedSnapshotId = snapshotId;
            finishAttempt();
          }
        });
  }

  @Override
  public void onSnapshotRejected(final String snapshotId) {
    actor.run(
        () -> {
          if (state == State.AWAIT_COMMIT && snapshotId.equals(currentId)) {
            finishAttempt();
          }
        });
  }

  private void tick() {
    if (closed || state != State.IDLE || timelineId == null) {
      return;
    }
    final var latest = snapshotStore.getLatestSnapshot();
    if (latest.isEmpty()) {
      return;
    }
    final var snapshot = latest.get();
    if (snapshot.getId().equals(lastArchivedSnapshotId) || snapshot.getIndex() > archivedIndex) {
      return;
    }

    state = State.RESERVED;
    final long thisAttempt = ++attempt;
    currentId = snapshot.getId();
    final var timeline = timelineId;
    actor.runOnCompletion(
        snapshot.reserve(),
        (reserved, error) -> {
          if (thisAttempt != attempt) {
            if (error == null) {
              reserved.release();
            }
            return;
          }
          if (error != null) {
            LOG.warn("Failed to reserve snapshot {} for archiving", snapshot.getId(), error);
            finishAttempt();
            return;
          }
          reservation = reserved;
          upload(snapshot, timeline, thisAttempt);
        });
  }

  private void upload(
      final PersistedSnapshot snapshot, final String timeline, final long thisAttempt) {
    state = State.UPLOADING;
    final var id = snapshot.getId();
    final var prefix = LogArchiveKeys.partitionPrefix(timeline, partitionId);
    final CompletableFuture<Void> uploaded;
    final ArchivedSnapshot archived;
    try {
      final Map<String, Path> files = snapshot.files();
      long size = 0;
      final var uploads = new ArrayList<CompletableFuture<Void>>();
      for (final var file : files.entrySet()) {
        size += Files.size(file.getValue());
        uploads.add(
            store.putFile(
                prefix + LogArchiveKeys.snapshotFileKey(id, file.getKey()), file.getValue()));
      }
      final var metadata = snapshot.getMetadata();
      final var names = new ArrayList<>(files.keySet());
      final var descriptor =
          new SnapshotDescriptorFile(
              id,
              snapshot.getIndex(),
              snapshot.getTerm(),
              metadata.processedPosition(),
              snapshot.snapshotId().getExportedPosition(),
              metadata.lastFollowupEventPosition(),
              metadata.maxExportedPosition(),
              names.size(),
              size,
              names);
      archived =
          new ArchivedSnapshot()
              .setId(id)
              .setIndex(descriptor.index())
              .setTerm(descriptor.term())
              .setProcessedPosition(descriptor.processedPosition())
              .setExportedPosition(descriptor.exportedPosition())
              .setLastFollowupEventPosition(descriptor.lastFollowupEventPosition())
              .setMaxExportedPosition(descriptor.maxExportedPosition())
              .setFileCount(descriptor.fileCount())
              .setSizeBytes(descriptor.sizeBytes());
      uploaded =
          CompletableFuture.allOf(uploads.toArray(CompletableFuture[]::new))
              .thenCompose(
                  ignored ->
                      store.put(
                          prefix + LogArchiveKeys.snapshotDescriptorKey(id),
                          LogArchiveJson.write(descriptor)));
    } catch (final IOException | RuntimeException e) {
      LOG.warn("Failed to upload snapshot {} to the log archive", id, e);
      finishAttempt();
      return;
    }

    uploaded.whenComplete(
        (ignored, error) ->
            actor.run(
                () -> {
                  if (thisAttempt != attempt) {
                    return;
                  }
                  if (error != null) {
                    LOG.warn("Failed to upload snapshot {} to the log archive", id, error);
                    finishAttempt();
                    return;
                  }
                  pendingSnapshot = archived;
                  writeBackoff = WRITE_RETRY_MIN;
                  writeCommand(timeline, thisAttempt);
                }));
  }

  private void writeCommand(final String timeline, final long thisAttempt) {
    if (thisAttempt != attempt || pendingSnapshot == null) {
      return;
    }
    final var result =
        writer.tryWrite(
            WriteContext.internal(),
            LogAppendEntry.of(
                new RecordMetadata()
                    .recordType(RecordType.COMMAND)
                    .valueType(ValueType.LOG_ARCHIVE)
                    .intent(LogArchiveIntent.ARCHIVE_SNAPSHOT),
                new LogArchiveRecord().setTimelineId(timeline).setSnapshot(pendingSnapshot)));
    if (result.isLeft()) {
      final var delay = writeBackoff;
      writeBackoff =
          writeBackoff.multipliedBy(2).compareTo(WRITE_RETRY_MAX) > 0
              ? WRITE_RETRY_MAX
              : writeBackoff.multipliedBy(2);
      LOG.debug("Could not write ARCHIVE_SNAPSHOT ({}), retrying in {}", result.getLeft(), delay);
      actor.schedule(delay, () -> writeCommand(timeline, thisAttempt));
      return;
    }
    state = State.AWAIT_COMMIT;
    awaitTimer =
        actor.schedule(
            AWAIT_COMMIT_TIMEOUT,
            () -> {
              if (thisAttempt == attempt && state == State.AWAIT_COMMIT) {
                LOG.warn("Timed out waiting for snapshot {} to be archived", currentId);
                finishAttempt();
              }
            });
  }

  private void finishAttempt() {
    attempt++;
    cancelAwaitTimer();
    release();
    pendingSnapshot = null;
    currentId = null;
    state = State.IDLE;
  }

  private void cancelAwaitTimer() {
    if (awaitTimer != null) {
      awaitTimer.cancel();
      awaitTimer = null;
    }
  }

  private void release() {
    final var toRelease = reservation;
    reservation = null;
    if (toRelease != null) {
      toRelease.release();
    }
  }
}
