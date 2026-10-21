/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.leader;

import io.camunda.zeebe.backup.logarchive.processing.LogArchiveListener;
import io.camunda.zeebe.backup.logarchive.state.LogArchiveMeta;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.management.ArchivedSnapshot;
import io.camunda.zeebe.protocol.impl.record.value.management.CoveredCheckpoint;
import io.camunda.zeebe.protocol.impl.record.value.management.LogArchiveRecord;
import io.camunda.zeebe.protocol.impl.record.value.management.LogObject;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.management.LogArchiveIntent;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ScheduledTimer;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Leader-only actor that hands the log below the restore floor over to the event plane (spec
 * section 21.3): every {@code releaseInterval} and after every merge, it computes R from the
 * replicated state and, if R moved, commits it with {@code RELEASE_LOG}. It does not wait for the
 * event plane; the announcer delivers the commit entry.
 *
 * <p>Nothing happens before {@link #onRecoveryChecked(LogArchiveMeta, boolean)} or {@link
 * #onTimelineStarted(String)}, and a timeline written before merges and releases existed (its meta
 * has no merged or released index) is left alone.
 */
@NullMarked
public final class LogArchiveReleaser extends Actor
    implements LogArchiveListener, LogArchiveRecoveryListener {

  static final Duration AWAIT_COMMIT_TIMEOUT = Duration.ofMinutes(1);

  private static final Logger LOG = LoggerFactory.getLogger(LogArchiveReleaser.class);

  /** What the releaser reads from the replicated state. */
  public record ReleaseState(
      LogArchiveMeta meta,
      List<CoveredCheckpoint> backupCheckpoints,
      List<ArchivedSnapshot> snapshots,
      List<LogObject> mergedObjects) {}

  private final int partitionId;
  private final LogStreamWriter writer;
  private final Supplier<ReleaseState> stateReader;
  private final Duration retentionWindow;
  private final Duration releaseInterval;

  // all fields below are only accessed on the actor thread
  private boolean active;
  private boolean awaitingCommit;
  private @Nullable ScheduledTimer awaitTimer;

  /**
   * @param stateReader reads the replicated state; called on the actor thread only
   * @param retentionWindow restore points younger than this stay restorable
   */
  public LogArchiveReleaser(
      final int partitionId,
      final LogStreamWriter writer,
      final Supplier<ReleaseState> stateReader,
      final Duration retentionWindow,
      final Duration releaseInterval) {
    super("LogArchiveReleaser-" + partitionId);
    this.partitionId = partitionId;
    this.writer = writer;
    this.stateReader = stateReader;
    this.retentionWindow = retentionWindow;
    this.releaseInterval = releaseInterval;
  }

  @Override
  protected void onActorStarted() {
    actor.runAtFixedRate(releaseInterval, this::tick);
  }

  @Override
  protected void onActorClosing() {
    active = false;
    clearAwait();
  }

  @Override
  public void onRecoveryChecked(final LogArchiveMeta meta, final boolean bucketAhead) {
    // if the bucket is ahead, the archiver forks a new timeline and announces it
    final boolean hasTimeline = !meta.getTimelineId().isEmpty() && !bucketAhead;
    actor.run(
        () -> {
          active = hasTimeline;
          tick();
        });
  }

  @Override
  public void onTimelineStarted(final String timelineId) {
    actor.run(
        () -> {
          active = true;
          clearAwait();
        });
  }

  @Override
  public void onLogMerged(final long mergedIndex, final long mergedPosition) {
    actor.run(this::tick);
  }

  @Override
  public void onLogReleased(final long releasedIndex) {
    actor.run(this::clearAwait);
  }

  @Override
  public void onReleaseLogRejected(final long releasedIndex) {
    actor.run(this::clearAwait);
  }

  private void tick() {
    if (!active || awaitingCommit) {
      return;
    }
    final var state = stateReader.get();
    final var meta = state.meta();
    if (meta.getTimelineId().isEmpty()
        || meta.getReleasedIndex() < 0
        || meta.getMergedIndex() < 0) {
      return;
    }
    final var released =
        LogArchiveReleaseFloor.releasedIndex(
            state.backupCheckpoints(),
            state.snapshots(),
            state.mergedObjects(),
            ActorClock.currentTimeMillis(),
            retentionWindow);
    if (released.isEmpty() || released.getAsLong() <= meta.getReleasedIndex()) {
      return;
    }

    final var result =
        writer.tryWrite(
            WriteContext.internal(),
            LogAppendEntry.of(
                new RecordMetadata()
                    .recordType(RecordType.COMMAND)
                    .valueType(ValueType.LOG_ARCHIVE)
                    .intent(LogArchiveIntent.RELEASE_LOG),
                new LogArchiveRecord()
                    .setTimelineId(meta.getTimelineId())
                    .setReleasedIndex(released.getAsLong())));
    if (result.isLeft()) {
      LOG.debug(
          "Partition {}: could not write RELEASE_LOG({}) ({}), retrying on the next tick",
          partitionId,
          released.getAsLong(),
          result.getLeft());
      return;
    }
    awaitingCommit = true;
    awaitTimer =
        actor.schedule(
            AWAIT_COMMIT_TIMEOUT,
            () -> {
              if (awaitingCommit) {
                LOG.warn("Partition {}: timed out waiting for the log to be released", partitionId);
                clearAwait();
              }
            });
  }

  private void clearAwait() {
    awaitingCommit = false;
    if (awaitTimer != null) {
      awaitTimer.cancel();
      awaitTimer = null;
    }
  }
}
