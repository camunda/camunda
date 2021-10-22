/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.log;

import io.camunda.zeebe.dispatcher.Dispatcher;
import io.camunda.zeebe.dispatcher.Dispatchers;
import io.camunda.zeebe.logstreams.impl.Loggers;
import io.camunda.zeebe.logstreams.log.LogRecordAwaiter;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamBatchWriter;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.LogStreamRecordWriter;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.logstreams.storage.LogStorage.CommitListener;
import io.camunda.zeebe.logstreams.storage.LogStorageReader;
import io.camunda.zeebe.util.exception.UnrecoverableException;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthStatus;
import io.camunda.zeebe.util.sched.Actor;
import io.camunda.zeebe.util.sched.ActorSchedulingService;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
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
  private static final String APPENDER_SUBSCRIPTION_NAME = "appender";

  private final Set<LogRecordAwaiter> recordAwaiters = new HashSet<>();
  private final String logName;
  private final int partitionId;
  private final int maxFrameLength;
  private final ActorSchedulingService actorSchedulingService;
  private final List<LogStreamReader> readers;
  private final LogStorage logStorage;
  private final CompletableActorFuture<Void> closeFuture;
  private final int nodeId;
  private final Set<FailureListener> failureListeners = new HashSet<>();
  private ActorFuture<LogStorageAppender> appenderFuture;
  private Dispatcher writeBuffer;
  private LogStorageAppender appender;
  private Throwable closeError; // set if any error occurred during closeAsync
  private final String actorName;
  private volatile HealthStatus healthStatus = HealthStatus.HEALTHY;

  LogStreamImpl(
      final ActorSchedulingService actorSchedulingService,
      final String logName,
      final int partitionId,
      final int nodeId,
      final int maxFrameLength,
      final LogStorage logStorage) {
    this.actorSchedulingService = actorSchedulingService;
    this.logName = logName;

    this.partitionId = partitionId;
    this.nodeId = nodeId;
    actorName = buildActorName(nodeId, "LogStream", partitionId);

    this.maxFrameLength = maxFrameLength;
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
  protected void handleFailure(final Exception failure) {
    if (failure instanceof UnrecoverableException) {
      onUnrecoverableFailure();
    }

    super.handleFailure(failure);
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
  public ActorFuture<LogStreamRecordWriter> newLogStreamRecordWriter() {
    // this should be replaced after refactoring the actor control
    if (actor.isClosed()) {
      return CompletableActorFuture.completedExceptionally(new RuntimeException("Actor is closed"));
    }

    final var writerFuture = new CompletableActorFuture<LogStreamRecordWriter>();
    actor.run(() -> createWriter(writerFuture, LogStreamWriterImpl::new));
    return writerFuture;
  }

  @Override
  public ActorFuture<LogStreamBatchWriter> newLogStreamBatchWriter() {
    // this should be replaced after refactoring the actor control
    if (actor.isClosed()) {
      return CompletableActorFuture.completedExceptionally(new RuntimeException("Actor is closed"));
    }

    final var writerFuture = new CompletableActorFuture<LogStreamBatchWriter>();
    actor.run(() -> createWriter(writerFuture, LogStreamBatchWriterImpl::new));
    return writerFuture;
  }

  @Override
  public void registerRecordAvailableListener(final LogRecordAwaiter recordAwaiter) {
    actor.call(() -> recordAwaiters.add(recordAwaiter));
  }

  @Override
  public void removeRecordAvailableListener(final LogRecordAwaiter recordAwaiter) {
    actor.call(() -> recordAwaiters.remove(recordAwaiter));
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

  private <T extends LogStreamWriter> void createWriter(
      final CompletableActorFuture<T> writerFuture, final WriterCreator<T> creator) {
    if (appender != null) {
      writerFuture.complete(creator.create(partitionId, writeBuffer));
    } else if (appenderFuture != null) {
      appenderFuture.onComplete(onOpenAppender(writerFuture, creator));
    } else {
      appenderFuture = new CompletableActorFuture<>();
      openAppender().onComplete(onOpenAppender(writerFuture, creator));
    }
  }

  private <T extends LogStreamWriter> BiConsumer<LogStorageAppender, Throwable> onOpenAppender(
      final CompletableActorFuture<T> writerFuture, final WriterCreator<T> creator) {
    return (openedAppender, errorOnOpeningAppender) -> {
      if (errorOnOpeningAppender == null) {
        writerFuture.complete(creator.create(partitionId, writeBuffer));
      } else {
        writerFuture.completeExceptionally(errorOnOpeningAppender);
      }
    };
  }

  private ActorFuture<Void> closeAppender() {
    final var closeAppenderFuture = new CompletableActorFuture<Void>();
    if (appender == null) {
      closeAppenderFuture.complete(null);
      return closeAppenderFuture;
    }

    appenderFuture = null;
    LOG.info("Close appender for log stream {}", logName);
    final var toCloseAppender = appender;
    final var toCloseWriteBuffer = writeBuffer;
    appender = null;
    writeBuffer = null;
    toCloseAppender
        .closeAsync()
        .onComplete(
            (v, t) -> {
              if (t == null) {
                toCloseWriteBuffer.closeAsync().onComplete(closeAppenderFuture);
              } else {
                closeAppenderFuture.completeExceptionally(t);
              }
            });
    return closeAppenderFuture;
  }

  private ActorFuture<LogStorageAppender> openAppender() {
    try {
      tryOpenAppender();
    } catch (final Exception error) {
      onOpenAppenderFailed(error);
    }
    return appenderFuture;
  }

  private void tryOpenAppender() {
    final long lastPosition;
    try {
      lastPosition = getLastPosition();
    } catch (final UnrecoverableException error) {
      LOG.error("Unexpected error when opening appender", error);
      onUnrecoverableFailure();
      appenderFuture.completeExceptionally(error);
      return;
    }

    final long initialPosition;
    if (lastPosition > 0) {
      initialPosition = lastPosition + 1;
    } else {
      initialPosition = 1;
    }

    writeBuffer =
        Dispatchers.create(buildActorName(nodeId, "dispatcher", partitionId))
            .maxFragmentLength(maxFrameLength)
            .initialPosition(initialPosition)
            .name(logName + "-write-buffer")
            .actorSchedulingService(actorSchedulingService)
            .build();

    writeBuffer
        .openSubscriptionAsync(APPENDER_SUBSCRIPTION_NAME)
        .onComplete(
            (subscription, throwable) -> {
              if (throwable == null) {
                appender =
                    new LogStorageAppender(
                        buildActorName(nodeId, "LogAppender", partitionId),
                        partitionId,
                        logStorage,
                        subscription,
                        maxFrameLength);

                actorSchedulingService
                    .submitActor(appender)
                    .onComplete(
                        (v, t) -> {
                          if (t != null) {
                            onOpenAppenderFailed(t);
                          } else {
                            appenderFuture.complete(appender);
                            appender.addFailureListener(this);
                          }
                        });
              } else {
                onOpenAppenderFailed(throwable);
              }
            });
  }

  private void onOpenAppenderFailed(final Throwable error) {
    LOG.error("Unexpected error when opening appender", error);
    appenderFuture.completeExceptionally(error);
    onFailure();
  }

  private long getLastPosition() {
    try (final LogStorageReader storageReader = logStorage.newReader();
        final LogStreamReader logReader = new LogStreamReaderImpl(storageReader)) {
      return logReader.seekToEnd();
    }
  }

  @Override
  public HealthStatus getHealthStatus() {
    return healthStatus;
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
  public void onFailure() {
    actor.run(
        () -> {
          healthStatus = HealthStatus.UNHEALTHY;
          failureListeners.forEach(FailureListener::onFailure);
          closeAsync();
        });
  }

  @Override
  public void onRecovered() {
    actor.run(
        () -> {
          healthStatus = HealthStatus.HEALTHY;
          failureListeners.forEach(FailureListener::onRecovered);
        });
  }

  @Override
  public void onUnrecoverableFailure() {
    actor.run(
        () -> {
          healthStatus = HealthStatus.DEAD;
          failureListeners.forEach(FailureListener::onUnrecoverableFailure);
          closeAsync();
        });
  }

  @FunctionalInterface
  private interface WriterCreator<T extends LogStreamWriter> {

    T create(int partitionId, Dispatcher dispatcher);
  }
}
