/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.log;

import io.camunda.zeebe.logstreams.impl.Loggers;
import io.camunda.zeebe.logstreams.log.LogRecordAwaiter;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.logstreams.storage.LogStorage.CommitListener;
import io.camunda.zeebe.logstreams.storage.LogStorageReader;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.exception.UnrecoverableException;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthReport;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import org.slf4j.Logger;

public final class LogStreamImpl extends Actor
    implements LogStream, FailureListener, CommitListener {

  private static final Logger LOG = Loggers.LOGSTREAMS_LOGGER;

  private final Set<LogRecordAwaiter> recordAwaiters = new HashSet<>();
  private final String logName;
  private final int partitionId;
  private final ActorSchedulingService actorSchedulingService;
  private final List<LogStreamReader> readers;
  private final int maxFragmentSize;
  private final LogStorage logStorage;
  private final CompletableActorFuture<Void> closeFuture;
  private final int nodeId;
  private final Set<FailureListener> failureListeners = new HashSet<>();
  private ActorFuture<LogStorageAppender> appenderFuture;
  private Sequencer sequencer;
  private LogStorageAppender appender;
  private Throwable closeError; // set if any error occurred during closeAsync
  private final String actorName;
  private HealthReport healthReport = HealthReport.healthy(this);

  LogStreamImpl(
      final ActorSchedulingService actorSchedulingService,
      final String logName,
      final int partitionId,
      final int nodeId,
      final int maxFragmentSize,
      final LogStorage logStorage) {
    this.actorSchedulingService = actorSchedulingService;
    this.logName = logName;

    this.partitionId = partitionId;
    this.nodeId = nodeId;
    actorName = buildActorName(nodeId, "LogStream", partitionId);

    this.maxFragmentSize = maxFragmentSize;
    this.logStorage = logStorage;
    closeFuture = new CompletableActorFuture<>();

    readers = new ArrayList<>();
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
  protected void onActorStarted() {
    logStorage.addCommitListener(this);
  }

  @Override
  protected void onActorClosing() {
    LOG.info("On closing logstream {} close {} readers", logName, readers.size());
    readers.forEach(LogStreamReader::close);
    logStorage.removeCommitListener(this);
  }

  @Override
  protected void onActorClosed() {
    if (closeError != null) {
      closeFuture.completeExceptionally(closeError);
    } else {
      closeFuture.complete(null);
    }
  }

  @Override
  public void close() {
    closeAsync().join();
  }

  @Override
  public ActorFuture<Void> closeAsync() {
    if (actor.isClosed()) {
      return closeFuture;
    }

    actor.run(
        () ->
            closeAppender()
                .onComplete(
                    (nothing, appenderError) -> {
                      closeError = appenderError;
                      actor.close();
                    }));
    return closeFuture;
  }

  @Override
  protected void handleFailure(final Throwable failure) {
    onFailure(failure);
  }

  private void onFailure(final Throwable failure) {
    LOG.error(
        "Unexpected error in Log Stream {} in phase {}.",
        getName(),
        actor.getLifecyclePhase(),
        failure);

    if (appenderFuture != null && !appenderFuture.isDone()) {
      // the appender future is not done yet ->
      // log stream was currently trying to
      // open the log storage appender but did
      // not succeed yet
      appenderFuture.completeExceptionally(failure);
    }

    if (failure instanceof UnrecoverableException) {
      onUnrecoverableFailure(HealthReport.dead(this).withIssue(failure));
    } else {
      onFailure(HealthReport.unhealthy(this).withIssue(failure));
    }
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public String getLogName() {
    return logName;
  }

  @Override
  public ActorFuture<LogStreamReader> newLogStreamReader() {
    return actor.call(this::createLogStreamReader);
  }

  @Override
  public ActorFuture<LogStreamWriter> newLogStreamWriter() {
    return createNewLogStreamWriter();
  }

  @Override
  public void registerRecordAvailableListener(final LogRecordAwaiter recordAwaiter) {
    actor.call(() -> recordAwaiters.add(recordAwaiter));
  }

  @Override
  public void removeRecordAvailableListener(final LogRecordAwaiter recordAwaiter) {
    actor.call(() -> recordAwaiters.remove(recordAwaiter));
  }

  public ActorFuture<LogStreamReader> newAuditReader() {
    return actor.call(() -> new AuditLogStreamReader(createLogStreamReader()));
  }

  private ActorFuture<LogStreamWriter> createNewLogStreamWriter() {
    // this should be replaced after refactoring the actor control
    if (actor.isClosed()) {
      return CompletableActorFuture.completedExceptionally(new RuntimeException("Actor is closed"));
    }

    final var writerFuture = new CompletableActorFuture<LogStreamWriter>();
    actor.run(() -> createWriter(writerFuture));
    return writerFuture;
  }

  private void notifyRecordAwaiters() {
    recordAwaiters.forEach(LogRecordAwaiter::onRecordAvailable);
  }

  @Override
  public void onCommit() {
    actor.call(this::notifyRecordAwaiters);
  }

  private LogStreamReader createLogStreamReader() {
    final LogStreamReader newReader = new LogStreamReaderImpl(logStorage.newReader());
    readers.add(newReader);
    return newReader;
  }

  private void createWriter(final CompletableActorFuture<LogStreamWriter> writerFuture) {

    final var onOpenAppenderConsumer = onOpenAppender(writerFuture);

    if (appender != null) {
      onOpenAppenderConsumer.accept(appender, null);
    } else {
      openAppender().onComplete(onOpenAppenderConsumer);
    }
  }

  private BiConsumer<LogStorageAppender, Throwable> onOpenAppender(
      final CompletableActorFuture<LogStreamWriter> writerFuture) {
    return (openedAppender, errorOnOpeningAppender) -> {
      if (errorOnOpeningAppender == null) {
        writerFuture.complete(sequencer);
      } else {
        writerFuture.completeExceptionally(errorOnOpeningAppender);
      }
    };
  }

  private ActorFuture<Void> closeAppender() {
    final var closeAppenderFuture = new CompletableActorFuture<Void>();

    LOG.info("Close appender for log stream {}", logName);

    final var toCloseAppender = appender;
    final var toCloseWriteBuffer = sequencer;
    final var toCompleteExceptionallyAppenderFuture = appenderFuture;

    appender = null;
    sequencer = null;
    appenderFuture = null;

    if (toCompleteExceptionallyAppenderFuture != null
        && !toCompleteExceptionallyAppenderFuture.isDone()) {
      // while opening the appender, a close signal is received
      toCompleteExceptionallyAppenderFuture.completeExceptionally(
          new LogStorageAppenderClosedException());
    }

    if (toCloseAppender == null) {
      closeAppenderFuture.complete(null);
      return closeAppenderFuture;
    }
    toCloseWriteBuffer.close();
    toCloseAppender.closeAsync().onComplete(closeAppenderFuture);

    return closeAppenderFuture;
  }

  private ActorFuture<LogStorageAppender> openAppender() {
    if (appenderFuture != null) {
      return appenderFuture;
    }

    appenderFuture = new CompletableActorFuture<>();
    actor.run(
        () -> {
          final var initialPosition = getWriteBuffersInitialPosition();
          sequencer = createAndScheduleWriteBuffer(initialPosition);
          createAndScheduleLogStorageAppender(sequencer)
              .onComplete(
                  (v, t) -> {
                    if (t != null) {
                      onFailure(t);
                    } else {
                      appenderFuture.complete(appender);
                      appender.addFailureListener(this);
                    }
                  });
        });

    return appenderFuture;
  }

  private long getWriteBuffersInitialPosition() {
    final var lastPosition = getLastCommittedPosition();
    long initialPosition = 1;

    if (lastPosition > 0) {
      initialPosition = lastPosition + 1;
    }

    return initialPosition;
  }

  private Sequencer createAndScheduleWriteBuffer(final long initialPosition) {
    return new Sequencer(initialPosition, maxFragmentSize, new SequencerMetrics(partitionId));
  }

  private ActorFuture<Void> createAndScheduleLogStorageAppender(final Sequencer sequencer) {
    appender =
        new LogStorageAppender(
            buildActorName(nodeId, "LogAppender", partitionId), partitionId, logStorage, sequencer);
    return actorSchedulingService.submitActor(appender);
  }

  private long getLastCommittedPosition() {
    try (final LogStorageReader storageReader = logStorage.newReader();
        final LogStreamReader logReader = new LogStreamReaderImpl(storageReader)) {
      return logReader.seekToEnd();
    }
  }

  @Override
  public HealthReport getHealthReport() {
    return healthReport;
  }

  @Override
  public void addFailureListener(final FailureListener failureListener) {
    actor.run(() -> failureListeners.add(failureListener));
  }

  @Override
  public void removeFailureListener(final FailureListener failureListener) {
    actor.run(() -> failureListeners.remove(failureListener));
  }

  @Override
  public void onFailure(final HealthReport report) {
    actor.run(
        () -> {
          healthReport = HealthReport.unhealthy(this).withIssue(report);
          failureListeners.forEach((l) -> l.onFailure(healthReport));
          closeAsync();
        });
  }

  @Override
  public void onRecovered() {
    actor.run(
        () -> {
          healthReport = HealthReport.healthy(this);
          failureListeners.forEach(FailureListener::onRecovered);
        });
  }

  @Override
  public void onUnrecoverableFailure(final HealthReport report) {
    actor.run(
        () -> {
          healthReport = HealthReport.dead(this).withIssue(report);
          failureListeners.forEach(l -> l.onUnrecoverableFailure(healthReport));
          closeAsync();
        });
  }

  private static final class LogStorageAppenderClosedException extends RuntimeException {
    private LogStorageAppenderClosedException() {
      super("LogStorageAppender was closed before opening succeeded");
    }
  }
}
