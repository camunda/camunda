/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.log;

import io.camunda.zeebe.logstreams.impl.flowcontrol.FlowControl;
import io.camunda.zeebe.logstreams.impl.log.LogStreamBuilderImpl;
import io.camunda.zeebe.logstreams.storage.LogStorage.CommittedPositionListener;
import org.jspecify.annotations.Nullable;

/**
 * Represents a stream of events. New events are append to the end of the log. With {@link
 * LogStream#newLogStreamWriter()} new writers can be created, which can be used to append new
 * events to the log.
 *
 * <p>To read events, the {@link LogStream#newLogStreamReader()} ()} can be used.
 */
public interface LogStream extends AutoCloseable {

  @Override
  void close();

  /**
   * @return a new default LogStream builder
   */
  static LogStreamBuilder builder() {
    return new LogStreamBuilderImpl();
  }

  /**
   * @return the partition id of the log stream
   */
  int getPartitionId();

  /**
   * Returns the name of the log stream.
   *
   * @return the log stream name
   */
  @Nullable String getLogName();

  /**
   * @return a future, when successfully completed it returns a newly created log stream reader
   */
  LogStreamReader newLogStreamReader();

  /**
   * Returns a new log stream reader that also reads records that have not been committed yet. This
   * is only safe for consumers that can tolerate records being truncated again.
   *
   * @return a new log stream reader
   */
  LogStreamReader newUncommittedLogStreamReader();

  /**
   * @return a future, when successfully completed it returns a newly created log stream record
   *     writer
   */
  LogStreamWriter newLogStreamWriter();

  /**
   * @return a handle to the flow control used by this log stream.
   */
  FlowControl getFlowControl();

  /**
   * Freezes write admission and drains in-flight writers for a leadership transfer. Safe to call
   * repeatedly.
   */
  void pauseWrites();

  /** Resumes write admission after a leadership transfer. Safe to call repeatedly. */
  void resumeWrites();

  /**
   * Registers a listener that will be notified when new <em>committed</em> records are available to
   * read from the logstream. This is what a consumer reading through {@link #newLogStreamReader()}
   * wants: an append tells it nothing, because the record it appended is not yet visible to it.
   *
   * @param recordAwaiter the listener to be notified
   */
  void registerRecordAvailableListener(LogRecordAwaiter recordAwaiter);

  /**
   * Removes the listener.
   *
   * @param recordAwaiter the listener to remove
   */
  void removeRecordAvailableListener(LogRecordAwaiter recordAwaiter);

  /**
   * Registers a listener that will be notified as soon as new records are <em>appended</em>, before
   * they are committed. This is what a consumer reading through {@link
   * #newUncommittedLogStreamReader()} wants: it can read the record straight away, and the later
   * commit reveals nothing new to it.
   *
   * @param recordAwaiter the listener to be notified
   */
  void registerAppendedRecordAvailableListener(LogRecordAwaiter recordAwaiter);

  /**
   * Removes the listener.
   *
   * @param recordAwaiter the listener to remove
   */
  void removeAppendedRecordAvailableListener(LogRecordAwaiter recordAwaiter);

  /**
   * Registers a listener that is notified with the highest committed position of the records that
   * this node appended itself. See {@link CommittedPositionListener} for the cases in which it is
   * not notified.
   */
  void registerCommittedPositionListener(CommittedPositionListener listener);

  /** Removes a committed position listener. */
  void removeCommittedPositionListener(CommittedPositionListener listener);
}
