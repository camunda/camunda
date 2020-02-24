/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.impl.log;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.dispatcher.impl.PositionUtil;
import io.zeebe.logstreams.impl.Loggers;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamBatchWriter;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.logstreams.log.LogStreamWriter;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorCondition;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.channel.ActorConditions;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import org.slf4j.Logger;

public final class LogStreamImpl extends Actor implements LogStream, AutoCloseable {
  public static final long INVALID_ADDRESS = -1L;

  private static final Logger LOG = Loggers.LOGSTREAMS_LOGGER;
  private static final String APPENDER_SUBSCRIPTION_NAME = "appender";

  private final ActorConditions onCommitPositionUpdatedConditions;
  private final String logName;
  private final int partitionId;
  private final int maxFrameLength;
  private final ActorScheduler actorScheduler;
  private final List<LogStreamReader> readers;
  private final LogStreamReaderImpl reader;
  private final LogStorage logStorage;
  private final CompletableActorFuture<Void> closeFuture;
  private final int nodeId;
  private ActorFuture<LogStorageAppender> appenderFuture;
  private Dispatcher writeBuffer;
  private LogStorageAppender appender;
  private long commitPosition;
  private Throwable closeError; // set if any error occurred during closeAsync
  private final String actorName;

  private final AtomicInteger openWriterCount = new AtomicInteger(0);

  public LogStreamImpl(
      final ActorScheduler actorScheduler,
      final ActorConditions onCommitPositionUpdatedConditions,
      final String logName,
      final int partitionId,
      final int nodeId,
      final int maxFrameLength,
      final LogStorage logStorage) {
    this.actorScheduler = actorScheduler;
    this.onCommitPositionUpdatedConditions = onCommitPositionUpdatedConditions;
    this.logName = logName;

    this.partitionId = partitionId;
    this.nodeId = nodeId;
    this.actorName = buildActorName(nodeId, "LogStream-" + partitionId);

    this.maxFrameLength = maxFrameLength;
    this.logStorage = logStorage;
    this.closeFuture = new CompletableActorFuture<>();

    try {
      logStorage.open();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    this.commitPosition = INVALID_ADDRESS;
    this.readers = new ArrayList<>();
    this.reader = new LogStreamReaderImpl(logStorage);
    this.readers.add(reader);

    internalSetCommitPosition(reader.seekToEnd());
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
  public String getName() {
    return actorName;
  }

  @Override
  public void close() {
    closeAsync().join();
  }

  @Override
  protected void onActorClosing() {
    LOG.info("On closing logstream {} close {} readers", logName, readers.size());
    readers.forEach(LogStreamReader::close);
    LOG.info("Close log storage with name {}", logName);
    logStorage.close();
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
  public ActorFuture<Void> closeAsync() {
    if (actor.isClosed()) {
      return closeFuture;
    }

    actor.call(
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
  public ActorFuture<Long> getCommitPositionAsync() {
    return actor.call(() -> commitPosition);
  }

  @Override
  public void setCommitPosition(final long commitPosition) {
    actor.call(() -> internalSetCommitPosition(commitPosition));
  }

  private void internalSetCommitPosition(final long commitPosition) {
    this.commitPosition = commitPosition;

    onCommitPositionUpdatedConditions.signalConsumers();
  }

  @Override
  public void delete(final long position) {
    actor.call(
        () -> {
          final long address = reader.lookupAddress(position);
          logStorage.delete(address);
        });
  }

  @Override
  public void registerOnCommitPositionUpdatedCondition(final ActorCondition condition) {
    actor.call(() -> onCommitPositionUpdatedConditions.registerConsumer(condition));
  }

  @Override
  public void removeOnCommitPositionUpdatedCondition(final ActorCondition condition) {
    actor.call(() -> onCommitPositionUpdatedConditions.removeConsumer(condition));
  }

  @Override
  public ActorFuture<LogStreamReader> newLogStreamReader() {
    return actor.call(
        () -> {
          final LogStreamReaderImpl reader = new LogStreamReaderImpl(logStorage);
          readers.add(reader);
          return reader;
        });
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

  private <T extends LogStreamWriter> void createWriter(
      final CompletableActorFuture<T> writerFuture, final WriterCreator<T> creator) {
    final var alreadyOpenWriters = openWriterCount.getAndIncrement();
    if (alreadyOpenWriters == 0) {
      openAppender().onComplete(onOpenAppender(writerFuture, creator));
    } else if (appender != null) {
      writerFuture.complete(creator.create(partitionId, writeBuffer, this::releaseWriter));
    } else if (appenderFuture != null) {
      appenderFuture.onComplete(onOpenAppender(writerFuture, creator));
    } else {
      final var errorMsg =
          String.format(
              "Expected to have an open appender, since we have already %d open writers",
              alreadyOpenWriters);
      writerFuture.completeExceptionally(new IllegalStateException(errorMsg));
    }
  }

  private <T extends LogStreamWriter> BiConsumer<LogStorageAppender, Throwable> onOpenAppender(
      final CompletableActorFuture<T> writerFuture, final WriterCreator<T> creator) {
    return (openedAppender, errorOnOpeningAppender) -> {
      if (errorOnOpeningAppender == null) {
        writerFuture.complete(creator.create(partitionId, writeBuffer, this::releaseWriter));
      } else {
        writerFuture.completeExceptionally(errorOnOpeningAppender);
      }
    };
  }

  private void releaseWriter() {
    actor.run(
        () -> {
          final var remainingOpenWriters = openWriterCount.decrementAndGet();
          if (remainingOpenWriters == 0) {
            closeAppender();
          }
        });
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
    if (appenderFuture != null) {
      return appenderFuture;
    }

    final var appenderOpenFuture = new CompletableActorFuture<LogStorageAppender>();

    appenderFuture = appenderOpenFuture;
    final String logName = getLogName();

    final int initialDispatcherPartitionId = determineInitialPartitionId();
    writeBuffer =
        Dispatchers.create(buildActorName(nodeId, "dispatcher-" + partitionId))
            .maxFragmentLength(maxFrameLength)
            .initialPartitionId(initialDispatcherPartitionId + 1)
            .name(logName + "-write-buffer")
            .actorScheduler(actorScheduler)
            .build();

    writeBuffer
        .openSubscriptionAsync(APPENDER_SUBSCRIPTION_NAME)
        .onComplete(
            (subscription, throwable) -> {
              if (throwable == null) {
                appender =
                    new LogStorageAppender(
                        buildActorName(nodeId, "LogAppender-" + partitionId),
                        partitionId,
                        logStorage,
                        subscription,
                        maxFrameLength);

                actorScheduler
                    .submitActor(appender)
                    .onComplete(
                        (v, t) -> {
                          if (t != null) {
                            appenderFuture.completeExceptionally(t);
                          } else {
                            appenderFuture.complete(appender);
                          }
                        });
              } else {
                appenderFuture.completeExceptionally(throwable);
              }
            });

    return appenderOpenFuture;
  }

  private int determineInitialPartitionId() {
    try (final LogStreamReaderImpl logReader = new LogStreamReaderImpl(logStorage)) {

      // Get position of last entry
      final long lastPosition = logReader.seekToEnd();

      // dispatcher needs to generate positions greater than the last position
      int partitionId = 0;

      if (lastPosition > 0) {
        partitionId = PositionUtil.partitionId(lastPosition);
      }

      return partitionId;
    }
  }

  @FunctionalInterface
  private interface WriterCreator<T extends LogStreamWriter> {
    T create(int partitionId, Dispatcher dispatcher, Runnable closeCallback);
  }
}
