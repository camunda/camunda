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
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.util.ByteValue;
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
import org.slf4j.Logger;

public final class LogStreamImpl extends Actor implements LogStream, AutoCloseable {
  public static final long INVALID_ADDRESS = -1L;

  private static final Logger LOG = Loggers.LOGSTREAMS_LOGGER;
  private static final String APPENDER_SUBSCRIPTION_NAME = "appender";

  private final ActorConditions onCommitPositionUpdatedConditions;
  private final String logName;
  private final int partitionId;
  private final ByteValue maxFrameLength;
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

  public LogStreamImpl(
      final ActorScheduler actorScheduler,
      final ActorConditions onCommitPositionUpdatedConditions,
      final String logName,
      final int partitionId,
      final int nodeId,
      final ByteValue maxFrameLength,
      final LogStorage logStorage) {
    this.actorScheduler = actorScheduler;
    this.onCommitPositionUpdatedConditions = onCommitPositionUpdatedConditions;
    this.logName = logName;
    this.partitionId = partitionId;
    this.nodeId = nodeId;
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
    return actorNamePattern(nodeId, "LogStream-" + partitionId);
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
    return actor.call(() -> new LogStreamWriterImpl(partitionId, writeBuffer));
  }

  @Override
  public ActorFuture<LogStreamBatchWriter> newLogStreamBatchWriter() {
    return actor.call(() -> new LogStreamBatchWriterImpl(partitionId, writeBuffer));
  }

  private ActorFuture<Void> closeAppender() {
    final var closeAppenderFuture = new CompletableActorFuture<Void>();
    if (appender == null) {
      closeAppenderFuture.complete(null);
      return closeAppenderFuture;
    }

    appenderFuture = null;
    LOG.info("Close appender for log stream {}", logName);
    appender
        .closeAsync()
        .onComplete(
            (v, t) -> {
              if (t == null) {
                writeBuffer.closeAsync().onComplete(closeAppenderFuture);
                appender = null;
                writeBuffer = null;
              } else {
                closeAppenderFuture.completeExceptionally(t);
              }
            });
    return closeAppenderFuture;
  }

  @Override
  protected void onActorStarting() {
    actor.runOnCompletionBlockingCurrentPhase(
        openAppender(),
        (appender, errorOnOpeningAppender) -> {
          if (errorOnOpeningAppender != null) {
            actor.close();
          }
        });
  }

  private ActorFuture<LogStorageAppender> openAppender() {
    final var appenderOpenFuture = new CompletableActorFuture<LogStorageAppender>();

    appenderFuture = appenderOpenFuture;
    final String logName = getLogName();

    final int initialDispatcherPartitionId = determineInitialPartitionId();
    writeBuffer =
        Dispatchers.create(actorNamePattern(nodeId, "dispatcher-" + partitionId))
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
                        actorNamePattern(nodeId, "LogAppender-" + partitionId),
                        partitionId,
                        logStorage,
                        subscription,
                        (int) maxFrameLength.toBytes());

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

  @Override
  public void delete(final long position) {
    actor.call(
        () -> {
          final boolean positionNotExist = !reader.seek(position);
          if (positionNotExist) {
            LOG.debug(
                "Tried to delete from log stream, but found no corresponding address for the given position {}.",
                position);
            return;
          }

          final long blockAddress = reader.lastReadAddress();
          LOG.debug(
              "Delete data from log stream until position '{}' (address: '{}').",
              position,
              blockAddress);

          logStorage.delete(blockAddress);
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
}
