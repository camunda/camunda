/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.metrics.StreamProcessorMetrics;
import io.camunda.zeebe.engine.state.ZeebeDbState;
import io.camunda.zeebe.engine.state.processing.DbLastProcessedPositionState;
import io.camunda.zeebe.logstreams.impl.Loggers;
import io.camunda.zeebe.logstreams.log.LogRecordAwaiter;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamBatchWriter;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.util.exception.UnrecoverableException;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthReport;
import io.camunda.zeebe.util.retry.AbortableRetryStrategy;
import io.camunda.zeebe.util.sched.Actor;
import io.camunda.zeebe.util.sched.ActorControl;
import io.camunda.zeebe.util.sched.ActorSchedulingService;
import io.camunda.zeebe.util.sched.clock.ActorClock;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.slf4j.Logger;

/*

+-------------------+
|                   |
|   ActorStarting   |
|                   |
+-------------------+
          |
          v
+-------------------+
|                   |                                    +-----------------+
|   Create Reader   |                                    |                 |
|                   |       ---------------------------> |    Actor close  | <-------------------------------
+-------------------+       |          |                 |                 |                                 |
          |                 |          |                 +-----------------+                                 |
          v                 |          |                                                                     |
+-------------------+       |          |                                                                     |
|                   |       |    +-----------+        +-------------+        +-----------------+      +------------+
|   Actor Started   |--------    |           |        |             |        |                 |      |            |
|                   |----------->|   Replay  |------->|   Replay    |------->| Create writer   | ---->|   Process  |
+-------------------+            |           |        |   Completed |        |                 |      |            |
                                 +-----------+        +-------------+        +-----------------+      +------------+
                                        |                                            |                      |
                                        |                                            |                      |
                                        |                                            |                      |
                                        v                                            |                      |
                                  +-------------+                                    |                      |
                                  |   Actor     |                                    |                      |
                                  |   Failed    |  <---------------------------------------------------------
                                  |             |
                                  +-------------+


https://textik.com/#f8692d3c3e76c699
*/
public class StreamPlatform extends Actor implements HealthMonitorable, LogRecordAwaiter {

  public static final long UNSET_POSITION = -1L;
  public static final Duration HEALTH_CHECK_TICK_DURATION = Duration.ofSeconds(5);

  private static final String ERROR_MESSAGE_RECOVER_FROM_SNAPSHOT_FAILED =
      "Expected to find event with the snapshot position %s in log stream, but nothing was found. Failed to recover '%s'.";
  private static final Logger LOG = Loggers.LOGSTREAMS_LOGGER;
  private final ActorSchedulingService actorSchedulingService;
  private final AtomicBoolean isOpened = new AtomicBoolean(false);
  private final Set<FailureListener> failureListeners = new HashSet<>();
  private final StreamProcessorMetrics metrics;

  // log stream
  private final LogStream logStream;
  private final int partitionId;
  // snapshotting
  private final ZeebeDb zeebeDb;
  // processing
  private final ProcessingContext processingContext;
  private final String actorName;
  private LogStreamReader logStreamReader;
  private ProcessingStateMachine processingStateMachine;
  private ReplayStateMachine replayStateMachine;

  private volatile Phase phase = Phase.INITIAL;

  private CompletableActorFuture<Void> openFuture;
  private final CompletableActorFuture<Void> closeFuture = new CompletableActorFuture<>();
  private volatile long lastTickTime;
  private boolean shouldProcess = true;
  private ActorFuture<LastProcessingPositions> replayCompletedFuture;
  private final Engine processor;

  protected StreamPlatform(final StreamProcessorBuilder processorBuilder) {
    actorSchedulingService = processorBuilder.getActorSchedulingService();
    zeebeDb = processorBuilder.getZeebeDb();

    processingContext =
        processorBuilder
            .getProcessingContext()
            .eventCache(new RecordValues())
            .actor(actor)
            .abortCondition(this::isClosed);
    logStream = processingContext.getLogStream();
    partitionId = logStream.getPartitionId();
    actorName = buildActorName(processorBuilder.getNodeId(), "StreamProcessor", partitionId);
    metrics = new StreamProcessorMetrics(partitionId);

    // todo this can be then created outside!
    processor = new Engine(processorBuilder);
  }

  public static StreamProcessorBuilder builder() {
    return new StreamProcessorBuilder();
  }

  @Override
  protected Map<String, String> createContext() {
    final var context = super.createContext();
    context.put(ACTOR_PROP_PARTITION_ID, Integer.toString(partitionId));
    return context;
  }

  @Override
  public String getName() {
    return actorName;
  }

  @Override
  protected void onActorStarting() {
    actor.runOnCompletionBlockingCurrentPhase(
        logStream.newLogStreamReader(), this::onRetrievingReader);
  }

  @Override
  protected void onActorStarted() {
    try {
      LOG.debug("Recovering state of partition {} from snapshot", partitionId);
      final var startRecoveryTimer = metrics.startRecoveryTimer();
      final long snapshotPosition = recoverFromSnapshot();

      final TransactionContext transactionContext = zeebeDb.createContext();

      final var partitionId = processingContext.getPartitionId();
      final var zeebeState = new ZeebeDbState(partitionId, zeebeDb, transactionContext);

      processingContext.transactionContext(transactionContext);
      processingContext.zeebeState(zeebeState);

      processor.init(processingContext);

      healthCheckTick();

      replayStateMachine =
          new ReplayStateMachine(processor, processingContext, this::shouldProcessNext);

      openFuture.complete(null);
      replayCompletedFuture = replayStateMachine.startRecover(snapshotPosition);

      if (!shouldProcess) {
        setStateToPausedAndNotifyListeners();
      } else {
        phase = Phase.REPLAY;
      }

      if (isInReplayOnlyMode()) {
        replayCompletedFuture.onComplete(
            (v, error) -> {
              if (error != null) {
                LOG.error("The replay of events failed.", error);
                onFailure(error);
              }
            });

      } else {
        replayCompletedFuture.onComplete(
            (lastProcessingPositions, error) -> {
              if (error != null) {
                LOG.error("The replay of events failed.", error);
                onFailure(error);
              } else {
                onRecovered(lastProcessingPositions);
                // observe recovery time
                startRecoveryTimer.close();
              }
            });
      }
    } catch (final RuntimeException e) {
      onFailure(e);
    }
  }

  @Override
  protected void onActorClosing() {
    tearDown();
  }

  @Override
  protected void onActorClosed() {
    closeFuture.complete(null);
    LOG.debug("Closed stream processor controller {}.", getName());
  }

  @Override
  protected void onActorCloseRequested() {
    if (!isFailed()) {
      processor.onClose();
    }
  }

  @Override
  public ActorFuture<Void> closeAsync() {
    isOpened.set(false);
    actor.close();
    return closeFuture;
  }

  @Override
  protected void handleFailure(final Throwable failure) {
    onFailure(failure);
  }

  @Override
  public void onActorFailed() {
    phase = Phase.FAILED;
    isOpened.set(false);
    processor.onFailed();
    tearDown();
    closeFuture.complete(null);
  }

  private boolean shouldProcessNext() {
    return isOpened() && shouldProcess;
  }

  private void tearDown() {
    processingContext.getLogStreamReader().close();
    logStream.removeRecordAvailableListener(this);
    replayStateMachine.close();
  }

  private void healthCheckTick() {
    lastTickTime = ActorClock.currentTimeMillis();
    actor.runDelayed(HEALTH_CHECK_TICK_DURATION, this::healthCheckTick);
  }

  private void onRetrievingWriter(
      final LogStreamBatchWriter batchWriter,
      final Throwable errorOnReceivingWriter,
      final LastProcessingPositions lastProcessingPositions) {

    if (errorOnReceivingWriter == null) {

      processingContext.logStreamWriter(batchWriter);

      phase = Phase.PROCESSING;

      processingStateMachine =
          new ProcessingStateMachine(processor, processingContext, this::shouldProcessNext);

      logStream.registerRecordAvailableListener(this);

      // start reading

      final var processingSchedulingService =
          new ProcessingSchedulingServiceImpl(actor, batchWriter, () -> !shouldProcess);
      processingContext.processingSchedulingService(processingSchedulingService);
      processor.onRecovered(processingContext);

      processingStateMachine.startProcessing(lastProcessingPositions);
      if (!shouldProcess) {
        setStateToPausedAndNotifyListeners();
      }
    } else {
      onFailure(errorOnReceivingWriter);
    }
  }

  private void onRetrievingReader(
      final LogStreamReader reader, final Throwable errorOnReceivingReader) {
    if (errorOnReceivingReader == null) {
      logStreamReader = reader;
      processingContext.logStreamReader(reader);
    } else {
      LOG.error("Unexpected error on retrieving reader from log stream.", errorOnReceivingReader);
      actor.close();
    }
  }

  public ActorFuture<Void> openAsync(final boolean pauseOnStart) {
    if (isOpened.compareAndSet(false, true)) {
      shouldProcess = !pauseOnStart;
      openFuture = new CompletableActorFuture<>();
      actorSchedulingService.submitActor(this);
    }
    return openFuture;
  }

  private long recoverFromSnapshot() {
    final DbLastProcessedPositionState lastProcessedPositionState =
        new DbLastProcessedPositionState(zeebeDb, zeebeDb.createContext());
    processingContext.lastProcessedPositionState(lastProcessedPositionState);
    final long snapshotPosition =
        lastProcessedPositionState.getLastSuccessfulProcessedRecordPosition();

    final boolean failedToRecoverReader = !logStreamReader.seekToNextEvent(snapshotPosition);
    if (failedToRecoverReader
        && processingContext.getProcessorMode() == StreamProcessorMode.PROCESSING) {
      throw new IllegalStateException(
          String.format(ERROR_MESSAGE_RECOVER_FROM_SNAPSHOT_FAILED, snapshotPosition, getName()));
    }

    LOG.info(
        "Recovered state of partition {} from snapshot at position {}",
        partitionId,
        snapshotPosition);
    return snapshotPosition;
  }

  private void onRecovered(final LastProcessingPositions lastProcessingPositions) {
    logStream
        .newLogStreamBatchWriter()
        .onComplete(
            (batchWriter, errorOnReceivingWriter) ->
                onRetrievingWriter(batchWriter, errorOnReceivingWriter, lastProcessingPositions));
  }

  private void onFailure(final Throwable throwable) {
    LOG.error("Actor {} failed in phase {}.", actorName, actor.getLifecyclePhase(), throwable);
    actor.fail();
    if (!openFuture.isDone()) {
      openFuture.completeExceptionally(throwable);
    }

    if (throwable instanceof UnrecoverableException) {
      final var report = HealthReport.dead(this).withIssue(throwable);
      failureListeners.forEach(l -> l.onUnrecoverableFailure(report));
    } else {
      final var report = HealthReport.unhealthy(this).withIssue(throwable);
      failureListeners.forEach(l -> l.onFailure(report));
    }
  }

  public boolean isOpened() {
    return isOpened.get();
  }

  public boolean isClosed() {
    return !isOpened.get();
  }

  public boolean isFailed() {
    return phase == Phase.FAILED;
  }

  public ActorFuture<Long> getLastProcessedPositionAsync() {
    return actor.call(
        () -> {
          if (isInReplayOnlyMode()) {
            return replayStateMachine.getLastSourceEventPosition();
          } else if (processingStateMachine == null) {
            // StreamProcessor is still replay mode
            return StreamPlatform.UNSET_POSITION;
          } else {
            return processingStateMachine.getLastSuccessfulProcessedRecordPosition();
          }
        });
  }

  private boolean isInReplayOnlyMode() {
    return processingContext.getProcessorMode() == StreamProcessorMode.REPLAY;
  }

  public ActorFuture<Long> getLastWrittenPositionAsync() {
    return actor.call(
        () -> {
          if (isInReplayOnlyMode()) {
            return replayStateMachine.getLastReplayedEventPosition();
          } else if (processingStateMachine == null) {
            // StreamProcessor is still replay mode
            return StreamPlatform.UNSET_POSITION;
          } else {
            return processingStateMachine.getLastWrittenPosition();
          }
        });
  }

  @Override
  public HealthReport getHealthReport() {
    if (actor.isClosed()) {
      return HealthReport.unhealthy(this).withMessage("actor is closed");
    }

    if (processingStateMachine != null && !processingStateMachine.isMakingProgress()) {
      return HealthReport.unhealthy(this).withMessage("not making progress");
    }

    // If healthCheckTick was not invoked it indicates the actor is blocked in a runUntilDone loop.
    if (ActorClock.currentTimeMillis() - lastTickTime > HEALTH_CHECK_TICK_DURATION.toMillis() * 2) {
      return HealthReport.unhealthy(this).withMessage("actor appears blocked");
    } else if (phase == Phase.FAILED) {
      return HealthReport.unhealthy(this).withMessage("in failed phase");
    } else {
      return HealthReport.healthy(this);
    }
  }

  @Override
  public void addFailureListener(final FailureListener failureListener) {
    actor.run(() -> failureListeners.add(failureListener));
  }

  @Override
  public void removeFailureListener(final FailureListener failureListener) {
    actor.run(() -> failureListeners.remove(failureListener));
  }

  public ActorFuture<Phase> getCurrentPhase() {
    return actor.call(() -> phase);
  }

  public ActorFuture<Void> pauseProcessing() {
    return actor.call(
        () -> {
          if (shouldProcess) {
            setStateToPausedAndNotifyListeners();
          }
        });
  }

  public ActorFuture<Boolean> hasProcessingReachedTheEnd() {
    return actor.call(
        () ->
            processingStateMachine != null
                && !isInReplayOnlyMode()
                && processingStateMachine.hasReachedEnd());
  }

  private void setStateToPausedAndNotifyListeners() {
    if (isInReplayOnlyMode() || !replayCompletedFuture.isDone()) {
      LOG.debug("Paused replay for partition {}", partitionId);
    } else {
      processor.onPaused();
      LOG.debug("Paused processing for partition {}", partitionId);
    }

    shouldProcess = false;
    phase = Phase.PAUSED;
  }

  public void resumeProcessing() {
    actor.call(
        () -> {
          if (!shouldProcess) {
            shouldProcess = true;

            if (isInReplayOnlyMode() || !replayCompletedFuture.isDone()) {
              phase = Phase.REPLAY;
              actor.submit(replayStateMachine::replayNextEvent);
              LOG.debug("Resumed replay for partition {}", partitionId);
            } else {
              // we only want to call the lifecycle listeners on processing resume
              // since the listeners are not recovered yet
              processor.onResumed();
              phase = Phase.PROCESSING;
              if (processingStateMachine != null) {
                actor.submit(processingStateMachine::readNextRecord);
              }
              LOG.debug("Resumed processing for partition {}", partitionId);
            }
          }
        });
  }

  @Override
  public void onRecordAvailable() {
    actor.run(processingStateMachine::readNextRecord);
  }

  public interface ProcessingSchedulingService {

    void runWithDelay(Duration duration, Supplier<ProcessingResult> scheduledProcessing);

    void runOnSuccess(
        ActorFuture<Void> deploymentPushedFuture, Supplier<ProcessingResult> scheduledProcessing);
  }

  public enum Phase {
    INITIAL,
    REPLAY,
    PROCESSING,
    FAILED,
    PAUSED,
  }

  public class ProcessingSchedulingServiceImpl implements ProcessingSchedulingService {
    private final ActorControl processingActor;
    private final BooleanSupplier isPaused;
    private final AbortableRetryStrategy writeRetryStrategy;
    private final LogStreamBatchWriter batchWriter;

    public ProcessingSchedulingServiceImpl(
        final ActorControl processingActor,
        final LogStreamBatchWriter batchWriter,
        final BooleanSupplier isPaused) {
      this.processingActor = processingActor;
      this.isPaused = isPaused;
      this.batchWriter = batchWriter;
      writeRetryStrategy = new AbortableRetryStrategy(processingActor);
    }

    @Override
    public void runWithDelay(
        final Duration duration, final Supplier<ProcessingResult> scheduledProcessing) {

      // THIS IS NOT ALLOWED DURING REPLAY - but should never happen
      if (phase == Phase.REPLAY) {
        throw new UnsupportedOperationException("Scheduling work during replay is not permitted.");
      }

      // WHAT WE HAVE TO GUARANTEE HERE
      //
      // A) WE run after the duration
      // B) all changes during a transaction are committed (when we schedule this during the
      // normal
      // processing)
      // C) WE execute after a processing not in between! This is necessary to not mess with dirty
      // buffers/writers
      //
      // CURRENTLY THIS IS GUARANTEED BY THE ACTOR SCHEDULER AND USING RUNUNITLDONE AND RUNDELAYED
      processingActor.runDelayed(duration, () -> executeScheduleProcessing(scheduledProcessing));
    }

    @Override
    public void runOnSuccess(
        final ActorFuture<Void> future, final Supplier<ProcessingResult> scheduledProcessing) {

      // THIS IS NOT ALLOWED DURING REPLAY - but should never happen
      if (phase == Phase.REPLAY) {
        throw new UnsupportedOperationException("Scheduling work during replay is not permitted.");
      }

      // WHAT WE HAVE TO GUARANTEE HERE
      //
      // A) WE run after the future
      // B) all changes during a transaction are committed (when we schedule this during the
      // normal
      // processing)
      // C) WE execute after a processing not in between! This is necessary to not mess with dirty
      // buffers/writers
      //
      // CURRENTLY THIS IS GUARANTEED BY THE ACTOR SCHEDULER AND USING RUNUNITLDONE AND
      // runOnCompletion
      processingActor.runOnCompletion(
          future,
          (v, t) -> {
            if (t != null) {
              return;
            }

            executeScheduleProcessing(scheduledProcessing);
          });
    }

    private void executeScheduleProcessing(final Supplier<ProcessingResult> scheduledProcessing) {
      // todo it would be much nicer if we could suspend actors instead of doing this here
      if (isPaused.getAsBoolean()) {
        processingActor.submit(() -> executeScheduleProcessing(scheduledProcessing));
        return;
      }

      // NO TRANSACTION - nothing is allowed to be written / updated (state related) so nothing will
      // be committed
      // STATE READING is allowed
      // Result will be written (result are potentially commands to change the state)
      final var processingResult = scheduledProcessing.get();

      writeRetryStrategy.runWithRetry(
          () -> {
            batchWriter.put(processingResult.getRecords());
            final long position = batchWriter.tryWrite();
            return position >= 0;
          });
    }
  }
}
