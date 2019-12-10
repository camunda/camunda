/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.log;

import io.zeebe.util.sched.ActorCondition;
import io.zeebe.util.sched.future.ActorFuture;

/**
 * Represents a stream of events. New events are append to the end of the log. With {@link
 * LogStream#newLogStreamRecordWriter()} or {@link LogStream#newLogStreamBatchWriter()} new writers
 * can be created, which can be used to append new events to the log.
 *
 * <p>To read events, the {@link LogStream#newLogStreamReader()} ()} can be used.
 */
public interface LogStream extends AutoCloseable {

  /** @return the partition id of the log stream */
  int getPartitionId();

  /**
   * Returns the name of the log stream.
   *
   * @return the log stream name
   */
  String getLogName();

  /** Closes the log stream asynchronous. */
  ActorFuture<Void> closeAsync();

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
   * Triggers deletion of data from the log stream, where the given position is used as upper bound.
   *
   * @param position the position as upper bound
   */
  void delete(long position);

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
