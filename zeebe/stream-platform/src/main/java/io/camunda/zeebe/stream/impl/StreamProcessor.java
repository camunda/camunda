/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.layered.zdb.LayeredZeebeDb;
import io.camunda.zeebe.logstreams.impl.Loggers;
import io.camunda.zeebe.logstreams.log.LogRecordAwaiter;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.stream.api.RecordProcessor;
import io.camunda.zeebe.stream.api.StreamClock;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock.Modification;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.ScheduledCommandCache.StageableScheduledCommandCache;
import io.camunda.zeebe.stream.impl.metrics.ScheduledTaskMetrics;
import io.camunda.zeebe.stream.impl.metrics.StreamProcessorMetrics;
import io.camunda.zeebe.stream.impl.records.RecordValues;
import io.camunda.zeebe.stream.impl.state.DbKeyGenerator;
import io.camunda.zeebe.stream.impl.state.StreamProcessorDbState;
import io.camunda.zeebe.util.exception.UnrecoverableException;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthReport;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
  private final StageableScheduledCommandCache scheduledCommandCache;

  // log stream
  private final LogStream logStream;
  private final int partitionId;
  // snapshotting
  private final ZeebeDb zeebeDb;
  // processing
  private final StreamProcessorContext streamProcessorContext;
  private LogStreamReader logStreamReader;
  private ProcessingStateMachine processingStateMachine;
  private ReplayStateMachine replayStateMachine;

  private CompletableActorFuture<Void> openFuture;
  private final CompletableActorFuture<Void> closeFuture = new CompletableActorFuture<>();
  private volatile long lastTickTime;
  private volatile boolean shouldProcess = true;
  private ActorFuture<LastProcessingPositions> replayCompletedFuture;

  private final List<RecordProcessor> recordProcessors = new ArrayList<>();
  private AsyncScheduleServiceContext asyncScheduleServiceContext;
  private ProcessingScheduleServiceImpl processorActorService;
  // set once in onActorStarted iff the db is layered (experimental flag); null otherwise; the
  // persistence driver is created only once its IO actor was submitted to the scheduler
  private LayeredStatePersistence layeredStatePersistence;
  private LayeredPersistIoActor layeredPersistIo;

  protected StreamProcessor(final StreamProcessorBuilder processorBuilder) {
    super("StreamProcessor", processorBuilder.getProcessingContext().partitionId());
    actorSchedulingService = processorBuilder.getActorSchedulingService();
    lifecycleAwareListeners = new ArrayList<>(processorBuilder.getLifecycleListeners());
    zeebeDb = processorBuilder.getZeebeDb();
    scheduledCommandCache = processorBuilder.scheduledCommandCache();

    streamProcessorContext =
        processorBuilder
            .getProcessingContext()
            .eventCache(new RecordValues())
            .actor(actor)
            .abortCondition(this::isClosed);
    logStream = streamProcessorContext.getLogStream();
    partitionId = logStream.getPartitionId();
    metrics = new StreamProcessorMetrics(streamProcessorContext.getMeterRegistry());
    metrics.initializeProcessorPhase(streamProcessorContext.getStreamProcessorPhase());
    recordProcessors.addAll(processorBuilder.getRecordProcessors());
  }

  public static StreamProcessorBuilder builder() {
    return new StreamProcessorBuilder();
  }

  @Override
  protected void onActorStarting() {
    final var reader = logStream.newLogStreamReader();
    logStreamReader = reader;
    streamProcessorContext.logStreamReader(reader);
  }

  @Override
  protected void onActorStarted() {
    try {
      final var startRecoveryTimer = metrics.startRecoveryTimer();
      LOG.debug("Recovering state of partition {} from snapshot", partitionId);
      final long snapshotPosition = recoverFromSnapshot();

      if (zeebeDb instanceof LayeredZeebeDb) {
        // tasks scheduled through the sync API read persisted state, so they must run on this
        // actor behind a persist barrier (see below) to observe every committed batch — otherwise
        // event-driven checkers can lose wake-ups. Tasks scheduled through the async API run on
        // their own actors behind a freeze preparation instead: buffered state is frozen into a
        // fresh read view before each execution, giving view-reading checkers the same freshness
        streamProcessorContext.setEnableAsyncScheduledTasks(false);
      }

      final var scheduledTaskMetrics =
          ScheduledTaskMetrics.of(streamProcessorContext.getMeterRegistry());

      final var actorServiceFactory =
          new ProcessingScheduleServiceFactory(
              streamProcessorContext::getStreamProcessorPhase,
              streamProcessorContext.getAbortCondition(),
              logStream::newLogStreamWriter,
              scheduledCommandCache,
              streamProcessorContext.getClock(),
              streamProcessorContext.getScheduledTaskCheckInterval(),
              scheduledTaskMetrics);

      // FREEZE-barrier: async tasks (AsyncProcessingScheduleServiceActor, e.g. the timer/message
      // checkers reading views) only need the buffered state frozen into a fresh view — cheap, no
      // IO; contrast with the DRAIN-barrier below for sync tasks reading persisted state
      asyncScheduleServiceContext =
          new AsyncScheduleServiceContext(
              actorSchedulingService,
              actorServiceFactory,
              zeebeDb instanceof LayeredZeebeDb ? this::prepareViewForScheduledTask : null,
              streamProcessorContext.partitionId());

      processorActorService = actorServiceFactory.create();

      if (zeebeDb instanceof LayeredZeebeDb) {
        // DRAIN-barrier: sync tasks (ProcessingScheduleServiceImpl jobs on this actor) read
        // persisted state, so the buffer is drained to RocksDB before every task runs and its
        // reads observe every batch committed before the task's preparation; contrast with the
        // cheap FREEZE-barrier above for view-reading async tasks. A null barrier result lets the
        // task run right away (nothing buffered, or recovery has not built the persistence driver
        // yet — nothing to drain then either)
        processorActorService.taskExecutionBarrier(
            () ->
                layeredStatePersistence == null
                    ? null
                    : layeredStatePersistence.prepareForScheduledTask());
      }

      final var processingScheduleService =
          new ExtendedProcessingScheduleServiceImpl(
              asyncScheduleServiceContext,
              processorActorService,
              streamProcessorContext.enableAsyncScheduledTasks());
      streamProcessorContext.scheduleService(processingScheduleService);

      initRecordProcessors();

      initLayeredStatePersistence();

      healthCheckTick();

      replayStateMachine =
          new ReplayStateMachine(recordProcessors, streamProcessorContext, this::shouldProcessNext);

      openFuture.complete(null);
      replayCompletedFuture = replayStateMachine.startRecover(snapshotPosition);

      if (!shouldProcess) {
        setStateToPausedAndNotifyListeners();
      } else {
        streamProcessorContext.streamProcessorPhase(Phase.REPLAY);
        metrics.setStreamProcessorReplay();
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
      // the layered persist IO actor closes before this actor: its close awaits an in-flight
      // persist job, whose completion callback then still finds this actor alive to complete the
      // round — afterwards no persist IO can touch the database anymore, so the owner of this
      // partition's database may safely close or take it over
      actor.run(
          () ->
              asyncScheduleServiceContext
                  .closeActors(actor)
                  .andThen(this::closeLayeredPersistIo, actor)
                  .andThen(actor::close, actor));
    }

    return closeFuture;
  }

  private ActorFuture<Void> closeLayeredPersistIo() {
    return layeredPersistIo == null
        ? CompletableActorFuture.completed(null)
        : layeredPersistIo.closeAsync();
  }

  @Override
  protected void handleFailure(final Throwable failure) {
    onFailure(failure);
  }

  @Override
  public void onActorFailed() {
    streamProcessorContext.streamProcessorPhase(Phase.FAILED);
    metrics.setStreamProcessorFailed();
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
    streamProcessorContext.getLogStreamReader().close();
    logStream.removeRecordAvailableListener(this);
    replayStateMachine.close();
    scheduledCommandCache.clear();
  }

  private void healthCheckTick() {
    lastTickTime = ActorClock.currentTimeMillis();
    actor.schedule(HEALTH_CHECK_TICK_DURATION, this::healthCheckTick);
  }

  private void startProcessing(final LastProcessingPositions lastProcessingPositions) {
    processingStateMachine =
        new ProcessingStateMachine(
            streamProcessorContext,
            this::shouldProcessNext,
            recordProcessors,
            scheduledCommandCache);

    logStream.registerRecordAvailableListener(this);

    // start reading
    lifecycleAwareListeners.forEach(l -> l.onRecovered(streamProcessorContext));
    processingStateMachine.startProcessing(lastProcessingPositions);
    if (!shouldProcess) {
      setStateToPausedAndNotifyListeners();
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
            streamProcessorContext.getKeyGeneratorControls(),
            streamProcessorContext.getClock(),
            streamProcessorContext.getMeterRegistry());

    recordProcessors.forEach(processor -> processor.init(processorContext));

    lifecycleAwareListeners.addAll(processorContext.getLifecycleListeners());
  }

  private void initLayeredStatePersistence() {
    if (!(zeebeDb instanceof final LayeredZeebeDb<?> layeredDb)) {
      return;
    }
    // the persist step of a round runs on a dedicated io-bound actor so the blocking RocksDB
    // batch commit never occupies this (cpu-bound) actor; the driver is created only once that
    // actor was submitted, so a prepared round can always be handed to it
    layeredPersistIo = new LayeredPersistIoActor(streamProcessorContext.partitionId());
    actorSchedulingService
        .submitActor(layeredPersistIo, SchedulingHints.ioBound())
        .onComplete(
            (ok, error) -> {
              if (error != null) {
                // never successfully scheduled, so there is nothing to close on failure
                layeredPersistIo = null;
                onFailure(error);
                return;
              }
              // all layered column families exist now (created during recovery and
              // record-processor init), so the persist driver can capture the engine domain's
              // store set
              layeredStatePersistence =
                  new LayeredStatePersistence(
                      layeredDb,
                      () ->
                          streamProcessorContext
                              .getLastProcessedPositionState()
                              .getLastSuccessfulProcessedRecordPosition(),
                      actor::submit,
                      layeredPersistIo::persist);
              streamProcessorContext.batchCommittedListener(
                  layeredStatePersistence::onBatchCommitted);
              // scheduled directly on the actor (not the processing schedule service) so the
              // cadences also cover the replay phase, where the layered context buffers writes too
              scheduleLayeredPersistTick();
              scheduleLayeredFreezeTick();
            },
            actor);
  }

  private void scheduleLayeredPersistTick() {
    actor.schedule(
        layeredStatePersistence.persistInterval(),
        () -> {
          layeredStatePersistence.onPeriodicTick();
          scheduleLayeredPersistTick();
        });
  }

  private void scheduleLayeredFreezeTick() {
    actor.schedule(
        layeredStatePersistence.freezeInterval(),
        () -> {
          layeredStatePersistence.onFreezeTick();
          scheduleLayeredFreezeTick();
        });
  }

  /**
   * Freezes the layered db's buffered state into a fresh read view; installed as the async schedule
   * services' task preparation, so a view acquired by a scheduled task after the returned future
   * completed observes every batch committed before the task ran. Waits for an in-flight batch to
   * complete before freezing; completes immediately while recovery has not built the layered
   * persistence yet (nothing is buffered then).
   */
  private ActorFuture<Void> prepareViewForScheduledTask() {
    final CompletableActorFuture<Void> frozen = new CompletableActorFuture<>();
    actor.run(() -> tryFreezeForScheduledTask(frozen));
    return frozen;
  }

  private void tryFreezeForScheduledTask(final CompletableActorFuture<Void> frozen) {
    try {
      if (layeredStatePersistence == null) {
        frozen.complete(null);
        return;
      }
      if (!layeredStatePersistence.tryFreezeForScheduledTask()) {
        // a batch is mid-flight on this actor; run again once it completed
        actor.submit(() -> tryFreezeForScheduledTask(frozen));
        return;
      }
      frozen.complete(null);
    } catch (final Exception e) {
      frozen.completeExceptionally(e);
    }
  }

  /**
   * Drains the layered db's buffered state to RocksDB so a subsequent {@code createSnapshot}
   * checkpoints a durable cut at least as new as the caller's last-processed position. Completes
   * immediately when the experimental layered-state flag is off; awaits an in-flight persist round
   * and any in-flight batch before draining otherwise (all waiting is future-chained on this actor,
   * never blocking).
   */
  public ActorFuture<Void> flushBufferedState() {
    if (!(zeebeDb instanceof LayeredZeebeDb)) {
      return CompletableActorFuture.completed(null);
    }
    final CompletableActorFuture<Void> flushed = new CompletableActorFuture<>();
    actor.run(() -> tryFlushBufferedState(flushed));
    return flushed;
  }

  private void tryFlushBufferedState(final CompletableActorFuture<Void> flushed) {
    try {
      if (layeredStatePersistence == null) {
        if (actor.isClosed() || isFailed()) {
          // the persistence driver will never be built anymore; nothing durable can be promised
          flushed.completeExceptionally(
              new IllegalStateException(
                  "Cannot flush buffered state, stream processor is closed or failed"));
          return;
        }
        // recovery has not built the persistence driver yet (its IO actor is still being
        // submitted); re-check once it exists — writes may already be buffered by then
        actor.submit(() -> tryFlushBufferedState(flushed));
        return;
      }
      layeredStatePersistence.flushForSnapshot(flushed);
    } catch (final Exception e) {
      flushed.completeExceptionally(e);
    }
  }

  private long recoverFromSnapshot() {
    // with the experimental layered-state flag on, the processing context is the layered db's
    // engine-domain context: batch commits promote an in-memory layer instead of writing RocksDB,
    // and LayeredStatePersistence drains the buffered state in periodic persist rounds
    final TransactionContext transactionContext;
    if (zeebeDb instanceof final LayeredZeebeDb<?> layeredDb) {
      // the db (and thus the domain context) may be reused across stream processors, e.g. on a
      // follower-to-leader transition; a predecessor that died mid-batch left uncommitted staged
      // writes behind — discard them, replay rebuilds their effects from the log. A predecessor
      // that died between preparing and completing a persist round left the round outstanding —
      // abort it (the segments stay buffered and this processor's rounds retry them); safe
      // because the predecessor closed its persist IO actor before its close/failure completed,
      // and a new stream processor only starts after its predecessor fully stopped
      layeredDb.defaultDomain().discardOpenBatch();
      layeredDb.defaultDomain().abortStaleRound();
      transactionContext = layeredDb.layeredContext();
    } else {
      transactionContext = zeebeDb.createContext();
    }
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
    final var writer = logStream.newLogStreamWriter();
    streamProcessorContext.logStreamWriter(writer);
    streamProcessorContext.streamProcessorPhase(Phase.PROCESSING);
    metrics.setStreamProcessorProcessing();

    processorActorService
        .open(actor)
        .andThen(() -> asyncScheduleServiceContext.submitActors(actor), actor)
        .onComplete(
            (ignored, error) -> {
              if (error != null) {
                onFailure(error);
                return;
              }
              startProcessing(lastProcessingPositions);
            },
            actor);
  }

  private void onFailure(final Throwable throwable) {
    LOG.error("Actor {} failed in phase {}.", getName(), actor.getLifecyclePhase(), throwable);

    // close the layered persist IO actor before failing this actor, so an in-flight persist
    // round finished (its completion callback still runs on this actor) and no persist IO can
    // race a successor taking over the partition's database
    asyncScheduleServiceContext
        .closeActors(actor)
        .andThen(this::closeLayeredPersistIo, actor)
        .onComplete(
            (v, t) -> {
              actor.fail(throwable);
              if (!openFuture.isDone()) {
                openFuture.completeExceptionally(throwable);
              }

              if (streamProcessorContext.getProcessorMode().equals(StreamProcessorMode.REPLAY)
                  && !(throwable instanceof UnrecoverableException)) {
                // If the stream processor is in replay mode, we do not want to report it as dead
                // because it is not critical. The leaders are still active and able to process
                // requests.
                final var report =
                    HealthReport.unhealthy(this)
                        .withIssue(throwable, ActorClock.current().instant());
                failureListeners.forEach(l -> l.onFailure(report));
              } else {

                // If it is a leader, we always want to report it as dead so that all related
                // services
                // are shutdown. (https://github.com/camunda/camunda/issues/16180)
                final var report =
                    HealthReport.dead(this).withIssue(throwable, ActorClock.current().instant());
                failureListeners.forEach(l -> l.onUnrecoverableFailure(report));
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
          if (isInReplayOnlyMode() || processingStateMachine == null) {
            return replayStateMachine.getLastSourceEventPosition();
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
  public String componentName() {
    return getName();
  }

  @Override
  public HealthReport getHealthReport() {
    final var instant =
        ActorClock.current() != null ? ActorClock.current().instant() : Instant.now();
    if (actor.isClosed()) {
      return HealthReport.unhealthy(this).withMessage("actor is closed", instant);
    }

    if (processingStateMachine != null && !processingStateMachine.isMakingProgress()) {
      return HealthReport.unhealthy(this)
          .withMessage("Processing not making progress. It is in an error handling loop.", instant);
    }

    // If healthCheckTick was not invoked it indicates the actor is blocked in a runUntilDone loop.
    if (ActorClock.currentTimeMillis() - lastTickTime > HEALTH_CHECK_TICK_DURATION.toMillis() * 2) {
      final StringBuilder message = new StringBuilder("actor appears blocked, ");
      if (processingStateMachine != null) {
        message.append(processingStateMachine.describeCurrentState());
      } else if (replayStateMachine != null) {
        message.append(replayStateMachine.describeCurrentState());
      } else {
        message.append("in phase ").append(streamProcessorContext.getStreamProcessorPhase());
      }
      return HealthReport.unhealthy(this).withMessage(message.toString(), instant);
    } else if (streamProcessorContext.getStreamProcessorPhase() == Phase.FAILED) {
      return HealthReport.unhealthy(this).withMessage("in failed phase", instant);
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
    metrics.setStreamProcessorPaused();
  }

  public void resumeProcessing() {
    actor.call(
        () -> {
          if (!shouldProcess) {
            shouldProcess = true;
            if (isInReplayOnlyMode() || !replayCompletedFuture.isDone()) {
              streamProcessorContext.streamProcessorPhase(Phase.REPLAY);
              metrics.setStreamProcessorReplay();
              actor.submit(replayStateMachine::replayNextEvent);
              LOG.debug("Resumed replay for partition {}", partitionId);
            } else {
              // we only want to call the lifecycle listeners on processing resume
              // since the listeners are not recovered yet
              lifecycleAwareListeners.forEach(StreamProcessorLifecycleAware::onResumed);
              streamProcessorContext.streamProcessorPhase(Phase.PROCESSING);
              metrics.setStreamProcessorProcessing();
              if (processingStateMachine != null) {
                actor.submit(processingStateMachine::tryToReadNextRecord);
              }
              LOG.debug("Resumed processing for partition {}", partitionId);
            }
          }
        });
  }

  @Override
  public void onRecordAvailable() {
    actor.run(processingStateMachine::tryToReadNextRecord);
  }

  /**
   * Returns an immutable clock fixed at the time of the call, and with the current modification. We
   * do not return the instant source but really a fixed time since the instant source may not
   * always be thread safe.
   *
   * <p>NOTE: this method is mostly for visibility to allow us to debug timing issues.
   */
  public ActorFuture<StreamClock> getClock() {
    return actor.call(
        () -> {
          final var clock = streamProcessorContext.getClock();
          return new ImmutableStreamClock(clock.instant(), clock.currentModification());
        });
  }

  private record ImmutableStreamClock(Instant instant, Modification currentModification)
      implements StreamClock {}

  public enum Phase {
    INITIAL,
    REPLAY,
    PROCESSING,
    FAILED,
    PAUSED,
  }
}
