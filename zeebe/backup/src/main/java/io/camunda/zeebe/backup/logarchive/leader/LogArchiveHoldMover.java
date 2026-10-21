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
 * Leader-only actor that moves the restore hold of an authority timeline (spec section 25.2): every
 * {@code releaseInterval} and after every archived snapshot, it computes the floor snapshot S like
 * the releaser does and, if S is above the hold, commits {@code MOVE_HOLD(S.index, S.id)}.
 *
 * <p>It stays idle before the recovery check, on a timeline that diverged from the bucket and on a
 * {@code CLUSTER} timeline, where the releaser is in charge. The hold only ever moves up.
 */
@NullMarked
public final class LogArchiveHoldMover extends Actor
    implements LogArchiveListener, LogArchiveRecoveryListener {

  static final Duration AWAIT_COMMIT_TIMEOUT = Duration.ofMinutes(1);

  private static final Logger LOG = LoggerFactory.getLogger(LogArchiveHoldMover.class);

  /** What the hold mover reads from the replicated state. */
  public record HoldState(
      LogArchiveMeta meta,
      List<CoveredCheckpoint> backupCheckpoints,
      List<ArchivedSnapshot> snapshots) {}

  private final int partitionId;
  private final LogStreamWriter writer;
  private final Supplier<HoldState> stateReader;
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
  public LogArchiveHoldMover(
      final int partitionId,
      final LogStreamWriter writer,
      final Supplier<HoldState> stateReader,
      final Duration retentionWindow,
      final Duration releaseInterval) {
    super("LogArchiveHoldMover-" + partitionId);
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
          tick();
        });
  }

  @Override
  public void onSnapshotArchived(final String snapshotId) {
    actor.run(this::tick);
  }

  @Override
  public void onHoldMoved(final long holdIndex) {
    actor.run(this::clearAwait);
  }

  @Override
  public void onMoveHoldRejected(final long holdIndex) {
    actor.run(this::clearAwait);
  }

  private void tick() {
    if (!active || awaitingCommit) {
      return;
    }
    final var state = stateReader.get();
    final var meta = state.meta();
    if (meta.getTimelineId().isEmpty() || !meta.isAuthority()) {
      return;
    }
    final var floor =
        LogArchiveReleaseFloor.floorSnapshot(
            state.backupCheckpoints(),
            state.snapshots(),
            ActorClock.currentTimeMillis(),
            retentionWindow);
    if (floor.isEmpty() || floor.get().getIndex() <= meta.getHoldIndex()) {
      return;
    }
    final var snapshot = floor.get();

    final var result =
        writer.tryWrite(
            WriteContext.internal(),
            LogAppendEntry.of(
                new RecordMetadata()
                    .recordType(RecordType.COMMAND)
                    .valueType(ValueType.LOG_ARCHIVE)
                    .intent(LogArchiveIntent.MOVE_HOLD),
                new LogArchiveRecord()
                    .setTimelineId(meta.getTimelineId())
                    .setHoldIndex(snapshot.getIndex())
                    .setHoldSnapshotId(snapshot.getId())));
    if (result.isLeft()) {
      LOG.debug(
          "Partition {}: could not write MOVE_HOLD({}) ({}), retrying on the next tick",
          partitionId,
          snapshot.getIndex(),
          result.getLeft());
      return;
    }
    awaitingCommit = true;
    awaitTimer =
        actor.schedule(
            AWAIT_COMMIT_TIMEOUT,
            () -> {
              if (awaitingCommit) {
                LOG.warn("Partition {}: timed out waiting for the hold to move", partitionId);
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
