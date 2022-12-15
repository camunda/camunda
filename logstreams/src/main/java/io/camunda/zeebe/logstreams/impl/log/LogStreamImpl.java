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
import org.slf4j.Logger;

public final class LogStreamImpl extends Actor
    implements LogStream, FailureListener, CommitListener {

  private static final Logger LOG = Loggers.LOGSTREAMS_LOGGER;

  private final Set<LogRecordAwaiter> recordAwaiters = new HashSet<>();
  private final String logName;
  private final int partitionId;
  private final List<LogStreamReader> readers;
  private final LogStorage logStorage;
  private final CompletableActorFuture<Void> closeFuture;
  private final Set<FailureListener> failureListeners = new HashSet<>();
  private Sequencer sequencer;
  private final String actorName;
  private HealthReport healthReport = HealthReport.healthy(this);

  LogStreamImpl(
      final String logName,
      final int partitionId,
      final int nodeId,
      final int maxFragmentSize,
      final LogStorage logStorage) {
    this.logName = logName;

    this.partitionId = partitionId;
    actorName = buildActorName(nodeId, "LogStream", partitionId);

    this.logStorage = logStorage;
    this.sequencer =
        new Sequencer(partitionId, getWriteBuffersInitialPosition(), maxFragmentSize, logStorage);
    sequencer.addFailureListener(this);
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
    closeFuture.complete(null);
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
        () -> {
          closeAppender();
          actor.close();
        });
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

  private ActorFuture<LogStreamWriter> createNewLogStreamWriter() {
    // this should be replaced after refactoring the actor control
    if (actor.isClosed()) {
      return CompletableActorFuture.completedExceptionally(new RuntimeException("Actor is closed"));
    }
    return CompletableActorFuture.completed(sequencer);
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

  private void closeAppender() {
    LOG.info("Close appender for log stream {}", logName);
    sequencer.close();
    sequencer = null;
  }

  private long getWriteBuffersInitialPosition() {
    final var lastPosition = getLastCommittedPosition();
    long initialPosition = 1;

    if (lastPosition > 0) {
      initialPosition = lastPosition + 1;
    }

    return initialPosition;
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
}
