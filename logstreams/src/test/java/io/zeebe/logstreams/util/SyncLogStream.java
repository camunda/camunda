/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.util;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.logstreams.impl.LogStorageAppender;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.util.sched.ActorCondition;
import io.zeebe.util.sched.future.ActorFuture;

public class SyncLogStream implements SynchronousLogStream {

  private final LogStream logStream;

  public SyncLogStream(LogStream logStream) {
    this.logStream = logStream;
  }

  @Override
  public LogStorageAppender getLogStorageAppender() {
    return logStream.getLogStorageAppender();
  }

  @Override
  public int getPartitionId() {
    return logStream.getPartitionId();
  }

  @Override
  public LogStream getAsyncLogStream() {
    return logStream;
  }

  @Override
  public String getLogName() {
    return logStream.getLogName();
  }

  @Override
  public void close() {
    logStream.closeAsync().join();
  }

  @Override
  public long getCommitPosition() {
    return logStream.getCommitPositionAsync().join();
  }

  @Override
  public void setCommitPosition(long position) {
    logStream.setCommitPosition(position);
  }

  @Override
  public LogStorage getLogStorage() {
    return logStream.getLogStorageAsync().join();
  }

  public Dispatcher getWriteBuffer() {
    return logStream.getWriteBufferAsync().join();
  }

  @Override
  public ActorFuture<Void> closeAppender() {
    return logStream.closeAppender();
  }

  @Override
  public ActorFuture<LogStorageAppender> openAppender() {
    return logStream.openAppender();
  }

  @Override
  public void delete(long position) {
    logStream.delete(position);
  }

  @Override
  public void registerOnCommitPositionUpdatedCondition(ActorCondition condition) {
    logStream.registerOnCommitPositionUpdatedCondition(condition);
  }

  @Override
  public void removeOnCommitPositionUpdatedCondition(ActorCondition condition) {
    logStream.removeOnCommitPositionUpdatedCondition(condition);
  }
}
