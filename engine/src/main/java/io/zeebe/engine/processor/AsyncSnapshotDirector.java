/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor;

import io.zeebe.logstreams.impl.Loggers;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.spi.SnapshotController;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorCondition;
import io.zeebe.util.sched.SchedulingHints;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.time.Duration;
import org.slf4j.Logger;

public class AsyncSnapshotDirector extends Actor {

  private static final Logger LOG = Loggers.SNAPSHOT_LOGGER;
  private static final String LOG_MSG_WAIT_UNTIL_COMMITTED =
      "Finished taking snapshot, need to wait until last written event position {} is committed, current commit position is {}. After that snapshot can be marked as valid.";
  private static final String ERROR_MSG_ON_RESOLVE_PROCESSED_POS =
      "Unexpected error in resolving last processed position.";
  private static final String ERROR_MSG_ON_RESOLVE_WRITTEN_POS =
      "Unexpected error in resolving last written position.";
  private static final String ERROR_MSG_MOVE_SNAPSHOT =
      "Unexpected exception occurred on moving valid snapshot.";
  private static final String LOG_MSG_ENFORCE_SNAPSHOT =
      "Enforce snapshot creation. Last successful processed position is {}.";
  private static final String ERROR_MSG_ENFORCED_SNAPSHOT =
      "Unexpected exception occurred on creating snapshot, was enforced to do so.";

  private static final int INITIAL_POSITION = -1;
  private final SnapshotController snapshotController;
  private final LogStream logStream;
  private final String name;
  private final Duration snapshotRate;
  private final String processorName;
  private final StreamProcessor streamProcessor;
  private ActorCondition commitCondition;
  private long lastWrittenEventPosition = INITIAL_POSITION;
  private boolean pendingSnapshot;
  private long lowerBoundSnapshotPosition;
  private long lastValidSnapshotPosition;
  private final Runnable prepareTakingSnapshot = this::prepareTakingSnapshot;

  public AsyncSnapshotDirector(
      StreamProcessor streamProcessor,
      SnapshotController snapshotController,
      LogStream logStream,
      Duration snapshotRate) {
    this.streamProcessor = streamProcessor;
    this.snapshotController = snapshotController;
    this.logStream = logStream;
    this.processorName = streamProcessor.getName();
    this.name = processorName + "-snapshot-director";
    this.snapshotRate = snapshotRate;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  protected void onActorStarting() {
    actor.setSchedulingHints(SchedulingHints.ioBound());
    actor.runAtFixedRate(snapshotRate, prepareTakingSnapshot);

    commitCondition = actor.onCondition(getConditionNameForPosition(), this::onCommitCheck);
    logStream.registerOnCommitPositionUpdatedCondition(commitCondition);

    lastValidSnapshotPosition = snapshotController.getLastValidSnapshotPosition();
    LOG.debug(
        "The position of the last valid snapshot is '{}'. Taking snapshots beyond this position.",
        lastValidSnapshotPosition);
  }

  @Override
  protected void onActorCloseRequested() {
    logStream.removeOnCommitPositionUpdatedCondition(commitCondition);
  }

  private String getConditionNameForPosition() {
    return getName() + "-wait-for-endPosition-committed";
  }

  private void prepareTakingSnapshot() {
    if (pendingSnapshot) {
      return;
    }

    final ActorFuture<Long> lastProcessedPosition = streamProcessor.getLastProcessedPositionAsync();
    actor.runOnCompletion(
        lastProcessedPosition,
        (lowerBoundSnapshotPosition, error) -> {
          if (error == null) {
            if (lowerBoundSnapshotPosition > lastValidSnapshotPosition) {
              this.lowerBoundSnapshotPosition = lowerBoundSnapshotPosition;
              takeSnapshot();
            } else {
              LOG.debug(
                  "No changes since last snapshot we will skip snapshot creation. Last valid snapshot position {}, new lower bound position {}",
                  lastValidSnapshotPosition,
                  lowerBoundSnapshotPosition);
            }

          } else {
            LOG.error(ERROR_MSG_ON_RESOLVE_PROCESSED_POS, error);
          }
        });
  }

  private void takeSnapshot() {
    pendingSnapshot = true;
    createSnapshot(snapshotController::takeTempSnapshot);

    final ActorFuture<Long> lastWrittenPosition = streamProcessor.getLastWrittenPositionAsync();
    actor.runOnCompletion(
        lastWrittenPosition,
        (endPosition, error) -> {
          if (error == null) {
            final long commitPosition = logStream.getCommitPosition();
            lastWrittenEventPosition = endPosition;

            LOG.debug(LOG_MSG_WAIT_UNTIL_COMMITTED, endPosition, commitPosition);
            onCommitCheck();

          } else {
            pendingSnapshot = false;
            LOG.error(ERROR_MSG_ON_RESOLVE_WRITTEN_POS, error);
          }
        });
  }

  private void createSnapshot(Runnable snapshotCreation) {
    final long start = System.currentTimeMillis();
    snapshotCreation.run();

    final long end = System.currentTimeMillis();
    final long snapshotCreationTime = end - start;

    LOG.debug("Creation of snapshot for {} took {} ms.", processorName, snapshotCreationTime);
  }

  private void onCommitCheck() {
    final long currentCommitPosition = logStream.getCommitPosition();

    if (pendingSnapshot && currentCommitPosition >= lastWrittenEventPosition) {
      try {

        lastValidSnapshotPosition = lowerBoundSnapshotPosition;
        snapshotController.moveValidSnapshot(lowerBoundSnapshotPosition);
        snapshotController.replicateLatestSnapshot(actor::submit);

      } catch (Exception ex) {
        LOG.error(ERROR_MSG_MOVE_SNAPSHOT, ex);
      } finally {
        pendingSnapshot = false;
      }
    }
  }

  protected void enforceSnapshotCreation(
      final long lastWrittenPosition, final long lastProcessedPosition) {
    final long commitPosition = logStream.getCommitPosition();

    if (commitPosition >= lastWrittenPosition
        && lastProcessedPosition > lastValidSnapshotPosition) {

      LOG.debug(LOG_MSG_ENFORCE_SNAPSHOT, lastProcessedPosition);
      try {
        createSnapshot(() -> snapshotController.takeSnapshot(lastProcessedPosition));
      } catch (Exception ex) {
        LOG.error(ERROR_MSG_ENFORCED_SNAPSHOT, ex);
      }
    }
  }

  public ActorFuture<Void> closeAsync() {
    final CompletableActorFuture<Void> future = new CompletableActorFuture();

    actor.call(
        () ->
            actor.runOnCompletion(
                streamProcessor.getLastWrittenPositionAsync(),
                (writtenPosition, ex1) -> {
                  if (ex1 == null) {
                    actor.runOnCompletion(
                        streamProcessor.getLastProcessedPositionAsync(),
                        (processedPosition, ex2) -> {
                          if (ex2 == null) {
                            enforceSnapshotCreation(writtenPosition, processedPosition);
                            close();
                            future.complete(null);
                          } else {
                            LOG.error(ERROR_MSG_ON_RESOLVE_PROCESSED_POS, ex2);
                            close();
                            future.completeExceptionally(ex2);
                          }
                        });

                  } else {
                    LOG.error(ERROR_MSG_ON_RESOLVE_WRITTEN_POS, ex1);
                    close();
                    future.completeExceptionally(ex1);
                  }
                }));

    return future;
  }

  private void close() {
    actor.close();
  }
}
