/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.log;

import io.camunda.zeebe.logstreams.impl.Loggers;
import io.camunda.zeebe.logstreams.log.LogRecordAwaiter;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.logstreams.storage.LogStorage.CommitListener;
import io.camunda.zeebe.logstreams.storage.LogStorageReader;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthReport;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;

public final class LogStreamImpl implements LogStream, FailureListener, CommitListener {

  private static final Logger LOG = Loggers.LOGSTREAMS_LOGGER;

  private final Set<LogRecordAwaiter> recordAwaiters = new HashSet<>();
  private final Set<FailureListener> failureListeners = new HashSet<>();

  private final String logName;
  private final int partitionId;
  private final List<LogStreamReader> readers;
  private final LogStorage logStorage;
  private Sequencer sequencer;
  private boolean isClosed = false;
  private HealthReport healthReport = HealthReport.healthy(this);

  LogStreamImpl(
      final String logName,
      final int partitionId,
      final int maxFragmentSize,
      final LogStorage logStorage) {
    this.logName = logName;
    this.partitionId = partitionId;

    this.logStorage = logStorage;
    this.sequencer =
        new Sequencer(partitionId, getWriteBuffersInitialPosition(), maxFragmentSize, logStorage);
    sequencer.addFailureListener(this);

    readers = new ArrayList<>();
    logStorage.addCommitListener(this);
  }

  @Override
  public synchronized void close() {
    LOG.info("On closing logstream {} close {} readers", logName, readers.size());
    isClosed = true;
    readers.forEach(LogStreamReader::close);
    readers.clear();
    logStorage.removeCommitListener(this);
    sequencer.close();
    sequencer = null;
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public String getLogName() {
    return logName;
  }

  @Override
  public LogStreamReader newLogStreamReader() {
    if (isClosed) {
      throw new IllegalStateException("LogStream is closed");
    }
    final var reader = new LogStreamReaderImpl(logStorage.newReader());
    readers.add(reader);
    return reader;
  }

  @Override
  public LogStreamWriter newLogStreamWriter() {
    if (isClosed) {
      throw new IllegalStateException("LogStream is closed");
    }
    return sequencer;
  }

  @Override
  public synchronized void registerRecordAvailableListener(final LogRecordAwaiter recordAwaiter) {
    recordAwaiters.add(recordAwaiter);
  }

  @Override
  public synchronized void removeRecordAvailableListener(final LogRecordAwaiter recordAwaiter) {
    recordAwaiters.remove(recordAwaiter);
  }

  @Override
  public synchronized void onCommit() {
    recordAwaiters.forEach(LogRecordAwaiter::onRecordAvailable);
  }

  private long getWriteBuffersInitialPosition() {
    final var lastPosition = getLastCommittedPosition();
    long initialPosition = 1;

    if (lastPosition > 0) {
      initialPosition = lastPosition + 1;
    }

    return initialPosition;
  }

  private long getLastCommittedPosition() {
    try (final LogStorageReader storageReader = logStorage.newReader();
        final LogStreamReader logReader = new LogStreamReaderImpl(storageReader)) {
      return logReader.seekToEnd();
    }
  }

  @Override
  public synchronized HealthReport getHealthReport() {
    return healthReport;
  }

  @Override
  public synchronized void addFailureListener(final FailureListener failureListener) {
    failureListeners.add(failureListener);
  }

  @Override
  public synchronized void removeFailureListener(final FailureListener failureListener) {
    failureListeners.remove(failureListener);
  }

  @Override
  public synchronized void onFailure(final HealthReport report) {
    healthReport = HealthReport.unhealthy(this).withIssue(report);
    failureListeners.forEach((l) -> l.onFailure(healthReport));
    close();
  }

  @Override
  public synchronized void onRecovered() {
    healthReport = HealthReport.healthy(this);
    failureListeners.forEach(FailureListener::onRecovered);
  }

  @Override
  public synchronized void onUnrecoverableFailure(final HealthReport report) {
    healthReport = HealthReport.dead(this).withIssue(report);
    failureListeners.forEach(l -> l.onUnrecoverableFailure(healthReport));
    close();
  }
}
