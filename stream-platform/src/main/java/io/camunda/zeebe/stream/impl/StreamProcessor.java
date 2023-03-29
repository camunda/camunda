/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.logstreams.impl.Loggers;
import io.camunda.zeebe.logstreams.log.LogRecordAwaiter;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.stream.api.RecordProcessor;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.impl.metrics.StreamProcessorMetrics;
import io.camunda.zeebe.stream.impl.records.RecordValues;
import io.camunda.zeebe.stream.impl.state.DbKeyGenerator;
import io.camunda.zeebe.stream.impl.state.StreamProcessorDbState;
import io.camunda.zeebe.util.exception.UnrecoverableException;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthReport;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
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
public class StreamProcessor extends Actor implements HealthMonitorable, LogRecordAwaiter {

  public static final long UNSET_POSITION = -1L;
  public static final Duration HEALTH_CHECK_TICK_DURATION = Duration.ofSeconds(5);

  private static final String ERROR_MESSAGE_RECOVER_FROM_SNAPSHOT_FAILED =
      "Expected to find event with the snapshot position %s in log stream, but nothing was found. Failed to recover '%s'.";
  private static final Logger LOG = Loggers.LOGSTREAMS_LOGGER;
  private final ActorSchedulingService actorSchedulingService;
  private final AtomicBoolean isOpened = new AtomicBoolean(false);
  private final List<StreamProcessorLifecycleAware> lifecycleAwareListeners;
  private final Set<FailureListener> failureListeners = new HashSet<>();
  private final StreamProcessorMetrics metrics;

  // log stream
  private final LogStream logStream;
  private final int partitionId;
  // snapshotting
  private final ZeebeDb zeebeDb;
  // processing
  private final StreamProcessorContext streamProcessorContext;
  private final String actorName;
  private LogStreamReader logStreamReader;
  private ProcessingStateMachine processingStateMachine;
  private ReplayStateMachine replayStateMachine;

  private CompletableActorFuture<Void> openFuture;
  private final CompletableActorFuture<Void> closeFuture = new CompletableActorFuture<>();
  private volatile long lastTickTime;
  private boolean shouldProcess = true;
  private ActorFuture<LastProcessingPositions> replayCompletedFuture;

  private final List<RecordProcessor> recordProcessors = new ArrayList<>();
  private ProcessingScheduleServiceImpl processorActorService;
  private ProcessingScheduleServiceImpl asyncScheduleService;
  private AsyncProcessingScheduleServiceActor asyncActor;
  private final int nodeId;

  protected StreamProcessor(final StreamProcessorBuilder processorBuilder) {
    actorSchedulingService = processorBuilder.getActorSchedulingService();
    lifecycleAwareListeners = new ArrayList<>(processorBuilder.getLifecycleListeners());
    zeebeDb = processorBuilder.getZeebeDb();

    streamProcessorContext =
        processorBuilder
            .getProcessingContext()
            .eventCache(new RecordValues())
            .actor(actor)
            .abortCondition(this::isClosed);
    logStream = streamProcessorContext.getLogStream();
    partitionId = logStream.getPartitionId();
    nodeId = processorBuilder.getNodeId();
    actorName = buildActorName(nodeId, "StreamProcessor", partitionId);
    metrics = new StreamProcessorMetrics(partitionId);
    recordProcessors.addAll(processorBuilder.getRecordProcessors());
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

      // the schedule service actor is only opened if the replay is done,
      // until then it is an unusable and closed schedule service
      processorActorService =
          new ProcessingScheduleServiceImpl(
              streamProcessorContext::getStreamProcessorPhase,
              streamProcessorContext.getAbortCondition(),
              logStream::newLogStreamWriter);
      asyncScheduleService =
          new ProcessingScheduleServiceImpl(
              streamProcessorContext::getStreamProcessorPhase, // this is volatile
              () -> false, // we will just stop the actor in this case, no need to provide this
              logStream::newLogStreamWriter);
      asyncActor =
          new AsyncProcessingScheduleServiceActor(asyncScheduleService, nodeId, partitionId);
      final var extendedProcessingScheduleService =
          new ExtendedProcessingScheduleServiceImpl(
              processorActorService, asyncScheduleService, asyncActor.getActorControl());
      streamProcessorContext.scheduleService(extendedProcessingScheduleService);

      initRecordProcessors();

      healthCheckTick();

      replayStateMachine =
          new ReplayStateMachine(recordProcessors, streamProcessorContext, this::shouldProcessNext);

      openFuture.complete(null);
      replayCompletedFuture = replayStateMachine.startRecover(snapshotPosition);

      if (!shouldProcess) {
        setStateToPausedAndNotifyListeners();
      } else {
        streamProcessorContext.streamProcessorPhase(Phase.REPLAY);
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
      lifecycleAwareListeners.forEach(StreamProcessorLifecycleAware::onClose);
    }
  }

  @Override
  public ActorFuture<Void> closeAsync() {
    if (isOpened.getAndSet(false)) {
      actor.run(() -> asyncActor.closeAsync().onComplete((v, t) -> actor.close()));
    }

    return closeFuture;
  }

  @Override
  protected void handleFailure(final Throwable failure) {
    onFailure(failure);
  }

  @Override
  public void onActorFailed() {
    streamProcessorContext.streamProcessorPhase(Phase.FAILED);
    isOpened.set(false);
    lifecycleAwareListeners.forEach(StreamProcessorLifecycleAware::onFailed);
    tearDown();
    closeFuture.complete(null);
  }

  private boolean shouldProcessNext() {
    return isOpened() && shouldProcess;
  }

  private void tearDown() {
    processorActorService.close();
    asyncScheduleService.close();
    streamProcessorContext.getLogStreamReader().close();
    logStream.removeRecordAvailableListener(this);
    replayStateMachine.close();
  }

  private void healthCheckTick() {
    lastTickTime = ActorClock.currentTimeMillis();
    actor.schedule(HEALTH_CHECK_TICK_DURATION, this::healthCheckTick);
  }

  private void chainSteps(final int index, final Step[] steps, final Runnable last) {
    if (index == steps.length) {
      last.run();
      return;
    }

    final Step step = steps[index];
    step.run()
        .onComplete(
            (v, t) -> {
              if (t == null) {
                chainSteps(index + 1, steps, last);
              } else {
                onFailure(t);
              }
            });
  }

  private void onRetrievingWriter(
      final LogStreamWriter writer,
      final Throwable errorOnReceivingWriter,
      final LastProcessingPositions lastProcessingPositions) {

    if (errorOnReceivingWriter == null) {
      streamProcessorContext.logStreamWriter(writer);

      streamProcessorContext.streamProcessorPhase(Phase.PROCESSING);

      chainSteps(
          0,
          new Step[] {
            () -> processorActorService.open(actor),
            () -> actorSchedulingService.submitActor(asyncActor)
          },
          () -> startProcessing(lastProcessingPositions));
    } else {
      onFailure(errorOnReceivingWriter);
    }
  }

  private void startProcessing(final LastProcessingPositions lastProcessingPositions) {
    processingStateMachine =
        new ProcessingStateMachine(
            streamProcessorContext, this::shouldProcessNext, recordProcessors);

    logStream.registerRecordAvailableListener(this);

    // start reading
    lifecycleAwareListeners.forEach(l -> l.onRecovered(streamProcessorContext));
    processingStateMachine.startProcessing(lastProcessingPositions);
    if (!shouldProcess) {
      setStateToPausedAndNotifyListeners();
    }
  }

  private void onRetrievingReader(
      final LogStreamReader reader, final Throwable errorOnReceivingReader) {
    if (errorOnReceivingReader == null) {
      logStreamReader = reader;
      streamProcessorContext.logStreamReader(reader);
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

  private void initRecordProcessors() {
    final var processorContext =
        new RecordProcessorContextImpl(
            partitionId,
            streamProcessorContext.getScheduleService(),
            zeebeDb,
            streamProcessorContext.getTransactionContext(),
            streamProcessorContext.getPartitionCommandSender(),
            streamProcessorContext.getKeyGeneratorControls());

    recordProcessors.forEach(processor -> processor.init(processorContext));

    lifecycleAwareListeners.addAll(processorContext.getLifecycleListeners());
  }

  private long recoverFromSnapshot() {
    final TransactionContext transactionContext = zeebeDb.createContext();
    streamProcessorContext.transactionContext(transactionContext);
    streamProcessorContext.keyGeneratorControls(
        new DbKeyGenerator(partitionId, zeebeDb, transactionContext));

    final StreamProcessorDbState streamProcessorDbState =
        new StreamProcessorDbState(zeebeDb, transactionContext);
    streamProcessorContext.lastProcessedPositionState(
        streamProcessorDbState.getLastProcessedPositionState());

    final long snapshotPosition =
        streamProcessorDbState
            .getLastProcessedPositionState()
            .getLastSuccessfulProcessedRecordPosition();

    final boolean failedToRecoverReader = !logStreamReader.seekToNextEvent(snapshotPosition);
    if (failedToRecoverReader
        && streamProcessorContext.getProcessorMode() == StreamProcessorMode.PROCESSING) {
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
        .newLogStreamWriter()
        .onComplete(
            (logStreamWriter, errorOnReceivingWriter) ->
                onRetrievingWriter(
                    logStreamWriter, errorOnReceivingWriter, lastProcessingPositions));
  }

  private void onFailure(final Throwable throwable) {
    LOG.error("Actor {} failed in phase {}.", actorName, actor.getLifecyclePhase(), throwable);

    final var asyncActorCloseFuture = asyncActor.closeAsync();
    asyncActorCloseFuture.onComplete(
        (v, t) -> {
          actor.fail(throwable);
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
        });
  }

  public boolean isOpened() {
    return isOpened.get();
  }

  public boolean isClosed() {
    return !isOpened.get();
  }

  public boolean isFailed() {
    return streamProcessorContext.getStreamProcessorPhase() == Phase.FAILED;
  }

  public ActorFuture<Long> getLastProcessedPositionAsync() {
    return actor.call(
        () -> {
          if (isInReplayOnlyMode()) {
            return replayStateMachine.getLastSourceEventPosition();
          } else if (processingStateMachine == null) {
            // StreamProcessor is still replay mode
            return StreamProcessor.UNSET_POSITION;
          } else {
            return processingStateMachine.getLastSuccessfulProcessedRecordPosition();
          }
        });
  }

  private boolean isInReplayOnlyMode() {
    return streamProcessorContext.getProcessorMode() == StreamProcessorMode.REPLAY;
  }

  public ActorFuture<Long> getLastWrittenPositionAsync() {
    return actor.call(
        () -> {
          if (isInReplayOnlyMode()) {
            return replayStateMachine.getLastReplayedEventPosition();
          } else if (processingStateMachine == null) {
            // StreamProcessor is still replay mode
            return StreamProcessor.UNSET_POSITION;
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
    } else if (streamProcessorContext.getStreamProcessorPhase() == Phase.FAILED) {
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
    return actor.call(streamProcessorContext::getStreamProcessorPhase);
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
      lifecycleAwareListeners.forEach(StreamProcessorLifecycleAware::onPaused);
      LOG.debug("Paused processing for partition {}", partitionId);
    }

    shouldProcess = false;
    streamProcessorContext.streamProcessorPhase(Phase.PAUSED);
  }

  public void resumeProcessing() {
    actor.call(
        () -> {
          if (!shouldProcess) {
            shouldProcess = true;

            if (isInReplayOnlyMode() || !replayCompletedFuture.isDone()) {
              streamProcessorContext.streamProcessorPhase(Phase.REPLAY);
              actor.submit(replayStateMachine::replayNextEvent);
              LOG.debug("Resumed replay for partition {}", partitionId);
            } else {
              // we only want to call the lifecycle listeners on processing resume
              // since the listeners are not recovered yet
              lifecycleAwareListeners.forEach(StreamProcessorLifecycleAware::onResumed);
              streamProcessorContext.streamProcessorPhase(Phase.PROCESSING);
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

  private static final class AsyncProcessingScheduleServiceActor extends Actor {

    private final ProcessingScheduleServiceImpl scheduleService;
    private CompletableActorFuture<Void> closeFuture = CompletableActorFuture.completed(null);
    private final String asyncScheduleActorName;
    private final int partitionId;

    public AsyncProcessingScheduleServiceActor(
        final ProcessingScheduleServiceImpl scheduleService,
        final int nodeId,
        final int partitionId) {
      this.scheduleService = scheduleService;
      asyncScheduleActorName = buildActorName(nodeId, "AsyncProcessingScheduleActor", partitionId);
      this.partitionId = partitionId;
    }

    @Override
    protected Map<String, String> createContext() {
      final var context = super.createContext();
      context.put(ACTOR_PROP_PARTITION_ID, Integer.toString(partitionId));
      return context;
    }

    @Override
    public String getName() {
      return asyncScheduleActorName;
    }

    @Override
    protected void onActorStarting() {
      final ActorFuture<Void> actorFuture = scheduleService.open(actor);
      actor.runOnCompletionBlockingCurrentPhase(
          actorFuture,
          (v, t) -> {
            if (t != null) {
              actor.fail(t);
            }
          });
      closeFuture = new CompletableActorFuture<>();
    }

    @Override
    protected void onActorClosed() {
      closeFuture.complete(null);
    }

    @Override
    public CompletableActorFuture<Void> closeAsync() {
      actor.close();
      return closeFuture;
    }

    @Override
    public void onActorFailed() {
      closeFuture.complete(null);
    }

    public ActorControl getActorControl() {
      return actor;
    }
  }

  private interface Step {
    ActorFuture<Void> run();
  }

  public enum Phase {
    INITIAL,
    REPLAY,
    PROCESSING,
    FAILED,
    PAUSED,
  }
}
