/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import static io.camunda.zeebe.util.Unit.unit;
import static java.util.Objects.requireNonNull;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.logstreams.impl.Loggers;
import io.camunda.zeebe.logstreams.log.LogRecordAwaiter;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
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
import org.agrona.CloseHelper;
import org.jspecify.annotations.Nullable;
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
  private @Nullable LogStreamReader logStreamReader;
  private @Nullable LogStreamReader processingLogStreamReader;
  private @Nullable ProcessingStateMachine processingStateMachine;
  private @Nullable ReplayStateMachine replayStateMachine;

  private @Nullable CompletableActorFuture<Void> openFuture;
  private final CompletableActorFuture<Void> closeFuture = new CompletableActorFuture<>();
  private volatile long lastTickTime;
  private volatile boolean shouldProcess = true;
  private @Nullable ActorFuture<LastProcessingPositions> replayCompletedFuture;

  private final List<RecordProcessor> recordProcessors = new ArrayList<>();
  private @Nullable AsyncScheduleServiceContext asyncScheduleServiceContext;

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
    if (!isInReplayOnlyMode()) {
      // Only the processing state machine reads uncommitted records, and it is never created in
      // replay-only mode. Since an open reader defers deletion of the segment it sits on, opening
      // one here would hold on to a segment that nothing ever reads.
      final var processingReader = logStream.newUncommittedLogStreamReader();
      processingLogStreamReader = processingReader;
      streamProcessorContext.processingLogStreamReader(processingReader);
    }
  }

  @Override
  protected void onActorStarted() {
    try {
      final var startRecoveryTimer = metrics.startRecoveryTimer();
      LOG.debug("Recovering state of partition {} from snapshot", partitionId);
      final long snapshotPosition = recoverFromSnapshot();

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

      final var asyncScheduleServiceContext =
          new AsyncScheduleServiceContext(
              actorSchedulingService, actorServiceFactory, streamProcessorContext.partitionId());
      this.asyncScheduleServiceContext = asyncScheduleServiceContext;

      final var processingScheduleService =
          new ExtendedProcessingScheduleServiceImpl(asyncScheduleServiceContext);
      streamProcessorContext.scheduleService(processingScheduleService);

      initRecordProcessors();

      healthCheckTick();

      replayStateMachine =
          new ReplayStateMachine(recordProcessors, streamProcessorContext, this::shouldProcessNext);

      requireNonNull(openFuture).complete(unit());

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
    closeFuture.complete(unit());
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
      actor.run(
          () ->
              requireNonNull(asyncScheduleServiceContext)
                  .closeActors(actor)
                  .andThen(actor::close, actor));
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
    metrics.setStreamProcessorFailed();
    isOpened.set(false);
    lifecycleAwareListeners.forEach(StreamProcessorLifecycleAware::onFailed);
    tearDown();
    closeFuture.complete(unit());
  }

  private boolean shouldProcessNext() {
    return isOpened() && shouldProcess;
  }

  /**
   * Closes the reader that replay reads committed records from, at most once. In REPLAY mode replay
   * never finishes, so this only runs on shutdown; in PROCESSING mode it runs as soon as replay is
   * done.
   */
  private void closeReplayReader() {
    final var reader = logStreamReader;
    if (reader != null) {
      logStreamReader = null;
      reader.close();
    }
  }

  private void tearDown() {
    closeReplayReader();
    CloseHelper.close(processingLogStreamReader);
    logStream.removeAppendedRecordAvailableListener(this);
    CloseHelper.close(processingStateMachine);
    CloseHelper.close(replayStateMachine);
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

    // Processing reads uncommitted records, so it must be woken as soon as records are appended;
    // the later commit of those same records would tell it nothing new.
    logStream.registerAppendedRecordAvailableListener(this);

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
    return requireNonNull(openFuture);
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

    final var logStreamReader = requireNonNull(this.logStreamReader);
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
    // Replay is the only consumer of the committed reader, and it is done: it is reached only in
    // PROCESSING mode, where the replay state machine never registers as a record-available
    // listener and so is never woken again. Leaving the reader open would pin the oldest log
    // position that any reader still needs, keeping segments (and, in the test log storage, every
    // appended entry) alive for the lifetime of the partition.
    closeReplayReader();

    final var writer = logStream.newLogStreamWriter();
    streamProcessorContext.logStreamWriter(writer);
    streamProcessorContext.streamProcessorPhase(Phase.PROCESSING);
    metrics.setStreamProcessorProcessing();

    requireNonNull(asyncScheduleServiceContext)
        .submitActors(actor)
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

    requireNonNull(asyncScheduleServiceContext)
        .closeActors(actor)
        .onComplete(
            (v, t) -> {
              actor.fail(throwable);
              final var actorClock = ActorClock.current();
              final var instant = actorClock != null ? actorClock.instant() : Instant.now();
              final var future = requireNonNull(openFuture);
              if (!future.isDone()) {
                future.completeExceptionally(throwable);
              }

              if (streamProcessorContext.getProcessorMode().equals(StreamProcessorMode.REPLAY)
                  && !(throwable instanceof UnrecoverableException)) {
                // If the stream processor is in replay mode, we do not want to report it as dead
                // because it is not critical. The leaders are still active and able to process
                // requests.
                final var report = HealthReport.unhealthy(this).withIssue(throwable, instant);
                failureListeners.forEach(l -> l.onFailure(report));
              } else {

                // If it is a leader, we always want to report it as dead so that all related
                // services
                // are shutdown. (https://github.com/camunda/camunda/issues/16180)
                final var report = HealthReport.dead(this).withIssue(throwable, instant);
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
            final var replayStateMachine = requireNonNull(this.replayStateMachine);
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
            final var replayStateMachine = requireNonNull(this.replayStateMachine);
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
    final var actorClock = ActorClock.current();
    final var instant = actorClock != null ? actorClock.instant() : Instant.now();
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
    if (isInReplayOnlyMode() || !requireNonNull(replayCompletedFuture).isDone()) {
      LOG.debug("Paused replay for partition {}", partitionId);
    } else {
      lifecycleAwareListeners.forEach(StreamProcessorLifecycleAware::onPaused);
      LOG.debug("Paused processing for partition {}", partitionId);
    }

    shouldProcess = false;
    streamProcessorContext.streamProcessorPhase(Phase.PAUSED);
    metrics.setStreamProcessorPaused();
  }

  public ActorFuture<Void> resumeProcessing() {
    return actor.call(
        () -> {
          if (!shouldProcess) {
            shouldProcess = true;
            if (isInReplayOnlyMode() || !requireNonNull(replayCompletedFuture).isDone()) {
              streamProcessorContext.streamProcessorPhase(Phase.REPLAY);
              metrics.setStreamProcessorReplay();
              final var replayStateMachine = requireNonNull(this.replayStateMachine);
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
    final var processingStateMachine = requireNonNull(this.processingStateMachine);
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
