/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.util;

import io.zeebe.logstreams.impl.LogStorageAppender;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamBatchWriter;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.util.sched.ActorCondition;
import io.zeebe.util.sched.future.ActorFuture;

/**
 * This synchronous log stream interface is only for testing purposes.
 *
 * <p>We do not want to pollute the real log stream interface with synchronous method's. Synchronous
 * method's would be error prone and it is likely that we use these synchronous methods in
 * production then by mistake, which would fail due to our actor model.
 *
 * <p>*Note:* Actor's are not allowed to join on a future.
 */
public interface SynchronousLogStream extends AutoCloseable {

  LogStream getAsyncLogStream();

  /** @return the partition id of the log stream */
  int getPartitionId();

  /**
   * Returns the name of the log stream.
   *
   * @return the log stream name
   */
  String getLogName();

  /** Closes the log stream synchronously. This blocks until the log stream is closed. */
  @Override
  void close();

  /** @return the current commit position, or a negative value if no entry is committed. */
  long getCommitPosition();

  /** sets the new commit position * */
  void setCommitPosition(long position);

  /**
   * Returns the log storage, which is accessed by the LogStream.
   *
   * @return the log storage
   */
  LogStorage getLogStorage();

  /** @return a new created log stream record writer */
  LogStreamRecordWriter newLogStreamRecordWriter();

  /** @return a new created log stream batch writer */
  LogStreamBatchWriter newLogStreamBatchWriter();

  /**
   * Returns the log stream controller, which streams the logged events from the write buffer into
   * the log storage.
   *
   * @return the log stream controller
   */
  LogStorageAppender getLogStorageAppender();

  /** Stops the streaming to the log storage. New events are no longer append to the log storage. */
  ActorFuture<Void> closeAppender();

  /**
   * This method installs and opens the log storage appender.
   *
   * @return the future which contains the log storage appender on completion
   */
  ActorFuture<LogStorageAppender> openAppender();

  /**
   * Triggers deletion of data from the log stream, where the given position is used as upper bound.
   *
   * @param position the position as upper bound
   */
  void delete(long position);

  void registerOnCommitPositionUpdatedCondition(ActorCondition condition);

  void removeOnCommitPositionUpdatedCondition(ActorCondition condition);
}
