/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.log;

import com.netflix.concurrency.limits.Limit;
import io.camunda.zeebe.logstreams.impl.LogStreamMetricsImpl;
import io.camunda.zeebe.logstreams.impl.Loggers;
import io.camunda.zeebe.logstreams.impl.flowcontrol.FlowControl;
import io.camunda.zeebe.logstreams.impl.flowcontrol.RateLimit;
import io.camunda.zeebe.logstreams.log.LogRecordAwaiter;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.logstreams.storage.LogStorage.AppendedListener;
import io.camunda.zeebe.logstreams.storage.LogStorage.CommitListener;
import io.camunda.zeebe.logstreams.storage.LogStorage.CommittedPositionListener;
import io.camunda.zeebe.logstreams.storage.LogStorageReader;
import io.camunda.zeebe.util.VisibleForTesting;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.InstantSource;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class LogStreamImpl implements LogStream, CommitListener, AppendedListener {

  private static final Logger LOG = Loggers.LOGSTREAMS_LOGGER;

  private final Collection<LogStreamReader> readers = new CopyOnWriteArrayList<>();
  private final Collection<LogRecordAwaiter> committedRecordAwaiters = new CopyOnWriteArrayList<>();
  private final Collection<LogRecordAwaiter> appendedRecordAwaiters = new CopyOnWriteArrayList<>();

  private @Nullable final String logName;
  private final int partitionId;
  private final LogStorage logStorage;
  private final FlowControl flowControl;
  private final Sequencer sequencer;
  private volatile boolean closed;

  LogStreamImpl(
      @Nullable final String logName,
      final int partitionId,
      final int maxFragmentSize,
      final LogStorage logStorage,
      final InstantSource clock,
      @Nullable final Limit requestLimit,
      @Nullable final RateLimit writeRateLimit,
      final int inFlightCapacity,
      final MeterRegistry meterRegistry) {
    this.logName = logName;

    this.partitionId = partitionId;
    this.logStorage = logStorage;
    flowControl =
        new FlowControl(
            new LogStreamMetricsImpl(meterRegistry),
            requestLimit,
            writeRateLimit,
            inFlightCapacity);
    sequencer =
        new Sequencer(
            logStorage,
            getWriteBuffersInitialPosition(),
            maxFragmentSize,
            clock,
            new SequencerMetrics(meterRegistry),
            flowControl);
    logStorage.addCommitListener(this);
    logStorage.addAppendedListener(this);
  }

  @Override
  public void close() {
    closed = true;
    LOG.debug("Closing {} with {} readers", logName, readers.size());
    readers.forEach(LogStreamReader::close);
    logStorage.removeCommitListener(this);
    logStorage.removeAppendedListener(this);
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public @Nullable String getLogName() {
    return logName;
  }

  @Override
  public LogStreamReader newLogStreamReader() {
    ensureOpen();
    return createLogStreamReader(logStorage::newReader);
  }

  @Override
  public LogStreamReader newUncommittedLogStreamReader() {
    ensureOpen();
    return createLogStreamReader(logStorage::newUncommittedReader);
  }

  @Override
  public LogStreamWriter newLogStreamWriter() {
    ensureOpen();
    return sequencer;
  }

  @Override
  public FlowControl getFlowControl() {
    return flowControl;
  }

  @Override
  public void pauseWrites() {
    sequencer.pauseWrites();
  }

  @Override
  public void resumeWrites() {
    sequencer.resumeWrites();
  }

  @Override
  public void registerRecordAvailableListener(final LogRecordAwaiter recordAwaiter) {
    ensureOpen();
    committedRecordAwaiters.add(recordAwaiter);
  }

  @Override
  public void removeRecordAvailableListener(final LogRecordAwaiter recordAwaiter) {
    ensureOpen();
    committedRecordAwaiters.remove(recordAwaiter);
  }

  @Override
  public void registerAppendedRecordAvailableListener(final LogRecordAwaiter recordAwaiter) {
    ensureOpen();
    appendedRecordAwaiters.add(recordAwaiter);
  }

  @Override
  public void removeAppendedRecordAvailableListener(final LogRecordAwaiter recordAwaiter) {
    ensureOpen();
    appendedRecordAwaiters.remove(recordAwaiter);
  }

  @Override
  public void registerCommittedPositionListener(final CommittedPositionListener listener) {
    ensureOpen();
    logStorage.addCommittedPositionListener(listener);
  }

  @Override
  public void removeCommittedPositionListener(final CommittedPositionListener listener) {
    logStorage.removeCommittedPositionListener(listener);
  }

  @Override
  public void onCommit() {
    notifyRecordAvailable(committedRecordAwaiters);
  }

  @Override
  public void onAppend(final long highestPosition) {
    notifyRecordAvailable(appendedRecordAwaiters);
  }

  private void notifyRecordAvailable(final Collection<LogRecordAwaiter> awaiters) {
    if (closed) {
      // This can be called by the raft thread after we've already closed the log stream.
      // We can just ignore it in that case. Using `ensureOpen` would throw an exception that would
      // break the raft thread.
      return;
    }
    awaiters.forEach(LogRecordAwaiter::onRecordAvailable);
  }

  private void ensureOpen() {
    if (closed) {
      throw new IllegalStateException("%s is closed".formatted(logName));
    }
  }

  private LogStreamReader createLogStreamReader(final Supplier<LogStorageReader> readerSupplier) {
    // Deregister on close instead of holding onto every reader ever created for the life of this
    // LogStream -- a caller that opens and closes many short-lived readers (e.g. one per poll of a
    // diagnostic endpoint) would otherwise leak one entry here per call.
    final var newReader = new LogStreamReaderImpl(readerSupplier.get(), readers::remove);
    readers.add(newReader);
    return newReader;
  }

  @VisibleForTesting
  int openReaderCount() {
    return readers.size();
  }

  private long getWriteBuffersInitialPosition() {
    final long initialPosition;
    final long lastPosition = getLastCommittedPosition();
    if (lastPosition > 0) {
      initialPosition = lastPosition + 1;
    } else {
      initialPosition = 1;
    }

    return initialPosition;
  }

  private long getLastCommittedPosition() {
    try (final var storageReader = logStorage.newReader();
        final var logStreamReader = new LogStreamReaderImpl(storageReader)) {
      return logStreamReader.seekToEnd();
    }
  }
}
