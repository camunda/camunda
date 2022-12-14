/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.util;

import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;

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

  /**
   * @return the partition id of the log stream
   */
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

  /**
   * @return the current commit position, or a negative value if no entry is committed.
   */
  long getLastWrittenPosition();

  /** sets the new commit position * */
  void setLastWrittenPosition(long position);

  LogStreamReader newLogStreamReader();

  /**
   * @return a new created log stream record writer
   */
  LogStreamWriter newLogStreamWriter();

  /**
   * Returns a wrapped {@link #newLogStreamWriter()} which ensures that every write returns only
   * when the entry has been added to the underlying storage.
   */
  SynchronousLogStreamWriter newSyncLogStreamWriter();

  /**
   * Force waiting until the given position has been persisted in the underlying storage.
   *
   * @param position the written position to wait for
   */
  void awaitPositionWritten(final long position);

  /**
   * Marker interface for a {@link LogStreamWriter} implementation which only returns when the entry
   * has been written to the underlying storage.
   */
  interface SynchronousLogStreamWriter extends LogStreamWriter {}
}
