/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.logstreams.log;

import io.zeebe.logstreams.impl.log.LogStreamBuilderImpl;
import io.zeebe.util.health.HealthMonitorable;
import io.zeebe.util.sched.ActorCondition;
import io.zeebe.util.sched.AsyncClosable;
import io.zeebe.util.sched.future.ActorFuture;

/**
 * Represents a stream of events. New events are append to the end of the log. With {@link
 * LogStream#newLogStreamRecordWriter()} or {@link LogStream#newLogStreamBatchWriter()} new writers
 * can be created, which can be used to append new events to the log.
 *
 * <p>To read events, the {@link LogStream#newLogStreamReader()} ()} can be used.
 */
public interface LogStream extends AsyncClosable, AutoCloseable, HealthMonitorable {

  /** @return a new default LogStream builder */
  static LogStreamBuilder builder() {
    return new LogStreamBuilderImpl();
  }

  /** @return the partition id of the log stream */
  int getPartitionId();

  /**
   * Returns the name of the log stream.
   *
   * @return the log stream name
   */
  String getLogName();

  /**
   * @return a future, when successfully completed it returns the current commit position, or a
   *     negative value if no entry is committed
   */
  ActorFuture<Long> getCommitPositionAsync();

  /** sets the new commit position * */
  void setCommitPosition(long position);

  /** @return a future, when successfully completed it returns a newly created log stream reader */
  ActorFuture<LogStreamReader> newLogStreamReader();

  /**
   * @return a future, when successfully completed it returns a newly created log stream record
   *     writer
   */
  ActorFuture<LogStreamRecordWriter> newLogStreamRecordWriter();

  /**
   * @return a future, when successfully completed it returns a newly created log stream batch
   *     writer
   */
  ActorFuture<LogStreamBatchWriter> newLogStreamBatchWriter();

  /**
   * Registers for on commit updates.
   *
   * @param condition the condition which should be signalled.
   */
  void registerOnCommitPositionUpdatedCondition(ActorCondition condition);

  /**
   * Removes the registered condition.
   *
   * @param condition the condition which should be removed
   */
  void removeOnCommitPositionUpdatedCondition(ActorCondition condition);
}
