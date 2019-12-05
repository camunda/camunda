/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.impl.service;

import static io.zeebe.util.sched.future.CompletableActorFuture.completed;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.dispatcher.impl.PositionUtil;
import io.zeebe.logstreams.impl.LogStorageAppender;
import io.zeebe.logstreams.impl.Loggers;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
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
import org.agrona.concurrent.status.Position;
import org.slf4j.Logger;

public class LogStreamService extends Actor implements LogStream, AutoCloseable {
  public static final long INVALID_ADDRESS = -1L;

  private static final Logger LOG = Loggers.LOGSTREAMS_LOGGER;
  private static final String APPENDER_SUBSCRIPTION_NAME = "appender";

  private final ActorConditions onCommitPositionUpdatedConditions;
  private final String logName;
  private final int partitionId;
  private final ByteValue maxFrameLength;
  private final Position commitPosition;
  private final ActorScheduler actorScheduler;

  private final BufferedLogStreamReader reader;
  private final LogStorage logStorage;
  private ActorFuture<Dispatcher> writeBufferFuture;
  private ActorFuture<LogStorageAppender> appenderFuture;
  private Dispatcher writeBuffer;
  private LogStorageAppender appender;

  public LogStreamService(
      final ActorScheduler actorScheduler,
      final ActorConditions onCommitPositionUpdatedConditions,
      final String logName,
      final int partitionId,
      final ByteValue maxFrameLength,
      final Position commitPosition,
      final LogStorage logStorage) {
    this.actorScheduler = actorScheduler;
    this.onCommitPositionUpdatedConditions = onCommitPositionUpdatedConditions;
    this.logName = logName;
    this.partitionId = partitionId;
    this.maxFrameLength = maxFrameLength;
    this.commitPosition = commitPosition;
    this.logStorage = logStorage;

    try {
      logStorage.open();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    commitPosition.setVolatile(INVALID_ADDRESS);
    this.reader = new BufferedLogStreamReader(logStorage);
    setCommitPosition(reader.seekToEnd());
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
  public void close() {
    closeAsync().join();
  }

  @Override
  public ActorFuture<Void> closeAsync() {
    if (actor.isClosed())
    {
      return CompletableActorFuture.completed(null);
    }

    final var closeFuture = new CompletableActorFuture<Void>();
    actor.call(
        () -> {
          closeAppender()
              .onComplete(
                  (v, t) -> {
                    if (t == null) {
                      LOG.info("Close log storage with name {}", logName);
                      logStorage.close();
                      closeFuture.complete(null);
                      actor.close();
                    } else {
                      closeFuture.completeExceptionally(t);
                    }
                  });
        });
    return closeFuture;
  }

  @Override
  public long getCommitPosition() {
    return actor.call(commitPosition::get).join();
  }

  @Override
  public void setCommitPosition(final long commitPosition) {
    actor.call(
        () -> {
          this.commitPosition.setOrdered(commitPosition);

          onCommitPositionUpdatedConditions.signalConsumers();
        });
  }

  @Override
  public LogStorage getLogStorage() {
    return actor.call(() -> logStorage).join();
  }

  @Override
  public Dispatcher getWriteBuffer() {
    final var getWriteBufferFuture = new CompletableActorFuture<Dispatcher>();

    actor.call(
        () -> {
          if (writeBuffer == null && writeBufferFuture != null) {
            writeBufferFuture.onComplete(getWriteBufferFuture);
            return;
          }
          getWriteBufferFuture.complete(writeBuffer);
        });
    return getWriteBufferFuture.join();
  }

  @Override
  public LogStorageAppender getLogStorageAppender() {
    final var getAppenderFuture = new CompletableActorFuture<LogStorageAppender>();

    actor.call(
        () -> {
          if (appender == null && appenderFuture != null) {
            appenderFuture.onComplete(getAppenderFuture);
            return;
          }
          getAppenderFuture.complete(appender);
        });
    return getAppenderFuture.join();
  }

  @Override
  public ActorFuture<Void> closeAppender() {
    final var closeAppenderFuture = new CompletableActorFuture<Void>();
    actor.run(
        () -> {
          if (appender == null) {
            closeAppenderFuture.complete(null);
            return;
          }

          appenderFuture = null;
          writeBufferFuture = null;

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
        });
    return closeAppenderFuture;
  }

  @Override
  public ActorFuture<LogStorageAppender> openAppender() {
    final var appenderOpenFuture = new CompletableActorFuture<LogStorageAppender>();

    actor.call(
        () -> {
          appenderFuture = appenderOpenFuture;
          final String logName = getLogName();

          final int partitionId = determineInitialPartitionId();
          writeBuffer =
              Dispatchers.create(logName + "-write-buffer")
                  .maxFragmentLength(maxFrameLength)
                  .initialPartitionId(partitionId + 1)
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
                              logName + "-appender",
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
        });

    return appenderOpenFuture;
  }

  private int determineInitialPartitionId() {
    try (BufferedLogStreamReader logReader = new BufferedLogStreamReader()) {
      logReader.wrap(logStorage);

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
                "Tried to delete from log stream, but found no corresponding address in the log block index for the given position {}.",
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
