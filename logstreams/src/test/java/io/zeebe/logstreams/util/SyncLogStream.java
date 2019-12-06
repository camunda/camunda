/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.util;

import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamBatchWriter;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.util.sched.ActorCondition;

public class SyncLogStream implements SynchronousLogStream {

  private final LogStream logStream;

  public SyncLogStream(LogStream logStream) {
    this.logStream = logStream;
  }

  @Override
  public LogStreamRecordWriter newLogStreamRecordWriter() {
    return logStream.newLogStreamRecordWriter().join();
  }

  @Override
  public LogStreamBatchWriter newLogStreamBatchWriter() {
    return logStream.newLogStreamBatchWriter().join();
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
