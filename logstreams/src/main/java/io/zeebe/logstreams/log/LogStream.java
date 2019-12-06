/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.log;

import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.util.sched.ActorCondition;
import io.zeebe.util.sched.future.ActorFuture;

/**
 * Represents a stream of events from a log storage.
 *
 * <p>The LogStream will append available events to the log storage with the help of an
 * LogController. The events are read from a given Dispatcher, if available. This can be stopped
 * with the {@link LogStream#closeAppender()} method and re-/started with {@link
 * LogStream#openAppender()} or {@link LogStream#closeAppender()}.
 *
 * <p>To access the current LogStorage the {@link LogStream#getLogStorage()} can be used. The {@link
 * #close()} method will close all LogController and the log storage.
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

  /** Closes the log stream synchronously. This blocks until the log stream is closed. */
  //  @Override
  //  void close();

  /** Closes the log stream asynchronous. */
  ActorFuture<Void> closeAsync();

  /** @return the current commit position, or a negative value if no entry is committed. */
  //  long getCommitPosition();

  ActorFuture<Long> getCommitPositionAsync();

  /** sets the new commit position * */
  void setCommitPosition(long position);

  /**
   * Returns the log storage, which is accessed by the LogStream.
   *
   * @return the log storage
   */
  //  LogStorage getLogStorage();

  ActorFuture<LogStorage> getLogStorageAsync();

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

  void registerOnCommitPositionUpdatedCondition(ActorCondition condition);

  void removeOnCommitPositionUpdatedCondition(ActorCondition condition);
}
