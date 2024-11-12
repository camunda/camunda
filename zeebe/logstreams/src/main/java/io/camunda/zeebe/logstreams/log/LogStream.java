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
  String getLogName();

  /**
   * @return a future, when successfully completed it returns a newly created log stream reader
   */
  LogStreamReader newLogStreamReader();

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
   * Registers a listener that will be notified when new records are available to read from the
   * logstream.
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
}
