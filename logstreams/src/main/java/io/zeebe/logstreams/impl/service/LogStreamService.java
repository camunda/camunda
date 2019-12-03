/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.impl.service;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.dispatcher.impl.PositionUtil;
import io.zeebe.logstreams.impl.LogStorageAppender;
import io.zeebe.logstreams.impl.Loggers;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.util.ByteValue;
import io.zeebe.util.sched.ActorCondition;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.channel.ActorConditions;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.agrona.concurrent.status.Position;
import org.slf4j.Logger;

public class LogStreamService implements LogStream, AutoCloseable {
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
    logStorage.close();
  }

  @Override
  public ActorFuture<Void> closeAsync() {
    close();
    return CompletableActorFuture.completed(null);
  }

  @Override
  public long getCommitPosition() {
    return commitPosition.get();
  }

  @Override
  public void setCommitPosition(final long commitPosition) {
    this.commitPosition.setOrdered(commitPosition);

    onCommitPositionUpdatedConditions.signalConsumers();
  }

  @Override
  public LogStorage getLogStorage() {
    return logStorage;
  }

  @Override
  public Dispatcher getWriteBuffer() {
    if (writeBuffer == null && writeBufferFuture != null) {
      writeBuffer = writeBufferFuture.join();
    }
    return writeBuffer;
  }

  @Override
  public LogStorageAppender getLogStorageAppender() {
    if (appender == null && appenderFuture != null) {
      appender = appenderFuture.join();
    }
    return appender;
  }

  @Override
  public ActorFuture<Void> closeAppender() {
    appenderFuture = null;
    writeBufferFuture = null;

    appender.close();
    writeBuffer.close();
    appender = null;
    writeBuffer = null;
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<LogStorageAppender> openAppender() {
    final String logName = getLogName();

    final int partitionId = determineInitialPartitionId();
    writeBuffer =
        Dispatchers.create(logName + "-write-buffer")
            .maxFragmentLength(maxFrameLength)
            .initialPartitionId(partitionId + 1)
            .name(logName + "-write-buffer")
            .actorScheduler(actorScheduler)
            .build();

    final var subscription = writeBuffer.openSubscription(APPENDER_SUBSCRIPTION_NAME);
    appender =
        new LogStorageAppender(
            logName + "-appender",
            partitionId,
            logStorage,
            subscription,
            (int) maxFrameLength.toBytes());
    appenderFuture = CompletableActorFuture.completed(appender);
    return appenderFuture;
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
    final boolean positionNotExist = !reader.seek(position);
    if (positionNotExist) {
      LOG.debug(
          "Tried to delete from log stream, but found no corresponding address in the log block index for the given position {}.",
          position);
      return;
    }

    final long blockAddress = reader.lastReadAddress();
    LOG.debug(
        "Delete data from log stream until position '{}' (address: '{}').", position, blockAddress);

    logStorage.delete(blockAddress);
  }

  @Override
  public void registerOnCommitPositionUpdatedCondition(final ActorCondition condition) {
    onCommitPositionUpdatedConditions.registerConsumer(condition);
  }

  @Override
  public void removeOnCommitPositionUpdatedCondition(final ActorCondition condition) {
    onCommitPositionUpdatedConditions.removeConsumer(condition);
  }
}
