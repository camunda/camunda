/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.processor;

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
      "Enforce snapshot creation. Last successful processed position is {}.";
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
  private final SnapshotMetrics metrics;
  private final String processorName;
  private final int maxSnapshots;
  private final Consumer<Long> oldDataRemover;

  private ActorCondition commitCondition;
  private long lastWrittenEventPosition = INITIAL_POSITION;
  private boolean pendingSnapshot;
  private long lowerBoundSnapshotPosition;

  private long lastValidSnapshotPosition;

  public AsyncSnapshotDirector(
      String name,
      Duration snapshotRate,
      Supplier<ActorFuture<Long>> asyncLastProcessedPositionSupplier,
      Supplier<ActorFuture<Long>> asyncLastWrittenPositionSupplier,
      SnapshotController snapshotController,
      Consumer<ActorCondition> conditionRegistration,
      Consumer<ActorCondition> conditionCheckOut,
      LongSupplier commitPositionSupplier,
      SnapshotMetrics metrics,
      int maxSnapshots,
      Consumer<Long> oldDataRemover) {
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
    this.oldDataRemover = oldDataRemover;
  }

  @Override
  protected void onActorStarting() {
    actor.setSchedulingHints(SchedulingHints.ioBound());
    actor.runAtFixedRate(snapshotRate, prepareTakingSnapshot);

    commitCondition = actor.onCondition(getConditionNameForPosition(), this::onCommitCheck);
    conditionRegistration.accept(commitCondition);

    lastValidSnapshotPosition = snapshotController.getLastValidSnapshotPosition();
    LOG.debug(
        "The position of the last valid snapshot is '{}'. Taking snapshots beyond this position.",
        lastValidSnapshotPosition);
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

  public ActorFuture<Void> enforceSnapshotCreation(
      final long lastWrittenPosition, final long lastProcessedPosition) {
    final ActorFuture<Void> snapshotCreation = new CompletableActorFuture<>();
    actor.call(
        () -> {
          final long commitPosition = commitPositionSupplier.getAsLong();

          if (!pendingSnapshot
              && commitPosition >= lastWrittenPosition
              && lastProcessedPosition > lastValidSnapshotPosition) {

            LOG.debug(LOG_MSG_ENFORCE_SNAPSHOT, lastProcessedPosition);
            try {
              createSnapshot(() -> snapshotController.takeSnapshot(lastProcessedPosition));
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

        lastValidSnapshotPosition = lowerBoundSnapshotPosition;
        snapshotController.moveValidSnapshot(lowerBoundSnapshotPosition);

        try {
          snapshotController.ensureMaxSnapshotCount(maxSnapshots);
          if (snapshotController.getValidSnapshotsCount() == maxSnapshots) {
            oldDataRemover.accept(snapshotController.getPositionToDelete(maxSnapshots));
          }

        } catch (Exception ex) {
          LOG.error(ERROR_MSG_ENSURING_MAX_SNAPSHOT_COUNT, ex);
        }

        snapshotController.replicateLatestSnapshot(actor::submit);

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
