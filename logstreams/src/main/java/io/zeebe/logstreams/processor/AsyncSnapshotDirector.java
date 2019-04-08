/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.logstreams.processor;

import io.zeebe.logstreams.impl.Loggers;
import io.zeebe.logstreams.spi.SnapshotController;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorCondition;
import io.zeebe.util.sched.SchedulingHints;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.slf4j.Logger;

public class AsyncSnapshotDirector extends Actor {

  private static final Logger LOG = Loggers.SNAPSHOT_LOGGER;
  private static final String LOG_MSG_WAIT_UNTIL_COMMITTED =
      "Finished taking snapshot, need to wait until last written event position {} is committed, current commit position is {}. After that snapshot can be marked as valid.";

  private static final String ERROR_MSG_ON_RESOLVE_PROCESSED_POS =
      "Unexpected error in resolving last processed position.";
  private static final String ERROR_MSG_ON_RESOLVE_WRITTEN_POS =
      "Unexpected error in resolving last written position.";
  private static final String ERROR_MSG_ENSURING_MAX_SNAPSHOT_COUNT =
      "Unexpected exception occurred on ensuring maximum snapshot count.";
  private static final String ERROR_MSG_MOVE_SNAPSHOT =
      "Unexpected exception occurred on moving valid snapshot.";

  private static final String LOG_MSG_ENFORCE_SNAPSHOT =
      "Enforce snapshot creation for last written position {} with commit position {}.";
  private static final String ERROR_MSG_ENFORCED_SNAPSHOT =
      "Unexpected exception occured on creating snapshot, was enforced to do so.";

  private static final int INITIAL_POSITION = -1;

  private final Runnable prepareTakingSnapshot = this::prepareTakingSnapshot;

  private final Supplier<ActorFuture<Long>> asyncLastProcessedPositionSupplier;
  private final Supplier<ActorFuture<Long>> asyncLastWrittenPositionSupplier;

  private final SnapshotController snapshotController;

  private final Consumer<ActorCondition> conditionRegistration;
  private final Consumer<ActorCondition> conditionCheckOut;
  private final LongSupplier commitPositionSupplier;
  private final String name;
  private final Duration snapshotRate;
  private final StreamProcessorMetrics metrics;
  private final String processorName;
  private final int maxSnapshots;

  private ActorCondition commitCondition;
  private long lastWrittenEventPosition = INITIAL_POSITION;
  private boolean pendingSnapshot;
  private long lowerBoundSnapshotPosition;

  AsyncSnapshotDirector(
      String name,
      Duration snapshotRate,
      Supplier<ActorFuture<Long>> asyncLastProcessedPositionSupplier,
      Supplier<ActorFuture<Long>> asyncLastWrittenPositionSupplier,
      SnapshotController snapshotController,
      Consumer<ActorCondition> conditionRegistration,
      Consumer<ActorCondition> conditionCheckOut,
      LongSupplier commitPositionSupplier,
      StreamProcessorMetrics metrics,
      int maxSnapshots) {
    this.asyncLastProcessedPositionSupplier = asyncLastProcessedPositionSupplier;
    this.asyncLastWrittenPositionSupplier = asyncLastWrittenPositionSupplier;
    this.snapshotController = snapshotController;
    this.conditionRegistration = conditionRegistration;
    this.conditionCheckOut = conditionCheckOut;
    this.commitPositionSupplier = commitPositionSupplier;
    this.processorName = name;
    this.name = name + "-snapshot-director";
    this.snapshotRate = snapshotRate;
    this.metrics = metrics;
    this.maxSnapshots = Math.max(maxSnapshots, 1);
  }

  @Override
  protected void onActorStarting() {
    actor.setSchedulingHints(SchedulingHints.ioBound());
    actor.runAtFixedRate(snapshotRate, prepareTakingSnapshot);

    commitCondition = actor.onCondition(getConditionNameForPosition(), this::onCommitCheck);
    conditionRegistration.accept(commitCondition);
  }

  @Override
  public String getName() {
    return name;
  }

  private String getConditionNameForPosition() {
    return getName() + "-wait-for-endPosition-committed";
  }

  @Override
  protected void onActorCloseRequested() {
    conditionCheckOut.accept(commitCondition);
  }

  ActorFuture<Void> enforceSnapshotCreation(
      final long lastWrittenPosition, final long commitPosition) {
    final ActorFuture<Void> snapshotCreation = new CompletableActorFuture<>();
    actor.call(
        () -> {
          if (lastWrittenPosition <= commitPosition) {
            LOG.debug(LOG_MSG_ENFORCE_SNAPSHOT, lastWrittenPosition, commitPosition);
            try {
              createSnapshot(() -> snapshotController.takeSnapshot(commitPosition));
            } catch (Exception ex) {
              LOG.error(ERROR_MSG_ENFORCED_SNAPSHOT, ex);
            }
          }
          snapshotCreation.complete(null);
        });
    return snapshotCreation;
  }

  private void prepareTakingSnapshot() {
    if (pendingSnapshot) {
      return;
    }

    final ActorFuture<Long> lastProcessedPosition = asyncLastProcessedPositionSupplier.get();
    actor.runOnCompletion(
        lastProcessedPosition,
        (lowerBoundSnapshotPosition, error) -> {
          if (error == null) {
            this.lowerBoundSnapshotPosition = lowerBoundSnapshotPosition;
            takeSnapshot();
          } else {
            LOG.error(ERROR_MSG_ON_RESOLVE_PROCESSED_POS, error);
          }
        });
  }

  private void takeSnapshot() {
    pendingSnapshot = true;
    createSnapshot(snapshotController::takeTempSnapshot);

    final ActorFuture<Long> lastWrittenPosition = asyncLastWrittenPositionSupplier.get();
    actor.runOnCompletion(
        lastWrittenPosition,
        (endPosition, error) -> {
          if (error == null) {
            final long commitPosition = commitPositionSupplier.getAsLong();
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

    LOG.info("Creation of snapshot for {} took {} ms.", processorName, snapshotCreationTime);
    metrics.recordSnapshotCreationTime(snapshotCreationTime);
  }

  private void onCommitCheck() {
    final long currentCommitPosition = commitPositionSupplier.getAsLong();

    if (pendingSnapshot && currentCommitPosition >= lastWrittenEventPosition) {
      try {

        snapshotController.moveValidSnapshot(lowerBoundSnapshotPosition);

        try {
          snapshotController.ensureMaxSnapshotCount(maxSnapshots);
        } catch (Exception ex) {
          LOG.error(ERROR_MSG_ENSURING_MAX_SNAPSHOT_COUNT, ex);
        }

      } catch (Exception ex) {
        LOG.error(ERROR_MSG_MOVE_SNAPSHOT, ex);
      } finally {
        pendingSnapshot = false;
      }
    }
  }

  public void close() {
    actor.close();
  }
}
