/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.util;

import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamBatchWriter;
import io.camunda.zeebe.logstreams.log.LogStreamBuilder;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.LogStreamRecordWriter;

public class SyncLogStream implements SynchronousLogStream {

  private final LogStream logStream;
  private long lastWrittenPosition = -1;

  public SyncLogStream(final LogStream logStream) {
    this.logStream = logStream;
  }

  public static SyncLogStreamBuilder builder() {
    return new SyncLogStreamBuilder();
  }

  public static SyncLogStreamBuilder builder(final LogStreamBuilder builder) {
    return new SyncLogStreamBuilder(builder);
  }

  @Override
  public LogStream getAsyncLogStream() {
    return logStream;
  }

  @Override
  public int getPartitionId() {
    return logStream.getPartitionId();
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
  public long getLastWrittenPosition() {
    return lastWrittenPosition;
  }

  @Override
  public void setLastWrittenPosition(final long position) {
    lastWrittenPosition = position;
  }

  @Override
  public LogStreamReader newLogStreamReader() {
    return logStream.newLogStreamReader().join();
  }

  @Override
  public LogStreamRecordWriter newLogStreamRecordWriter() {
    return logStream.newLogStreamRecordWriter().join();
  }

  @Override
  public LogStreamBatchWriter newLogStreamBatchWriter() {
    return logStream.newLogStreamBatchWriter().join();
  }
}
