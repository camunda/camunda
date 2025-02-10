/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.log;

import com.netflix.concurrency.limits.Limit;
import io.camunda.zeebe.logstreams.impl.LogStreamMetrics;
import io.camunda.zeebe.logstreams.impl.Loggers;
import io.camunda.zeebe.logstreams.impl.flowcontrol.FlowControl;
import io.camunda.zeebe.logstreams.impl.flowcontrol.RateLimit;
import io.camunda.zeebe.logstreams.log.LogRecordAwaiter;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.logstreams.storage.LogStorage.CommitListener;
<<<<<<< HEAD
import java.time.InstantSource;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
=======
import io.camunda.zeebe.logstreams.storage.LogStorageReader;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.exception.UnrecoverableException;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthReport;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
>>>>>>> df85a699 (refactor: migrate sequencer metrics to micrometer)
import org.slf4j.Logger;

public final class LogStreamImpl implements LogStream, CommitListener {

  private static final Logger LOG = Loggers.LOGSTREAMS_LOGGER;

  private final Collection<LogStreamReader> readers = new CopyOnWriteArrayList<>();
  private final Collection<LogRecordAwaiter> recordAwaiters = new CopyOnWriteArrayList<>();

  private final String logName;
  private final int partitionId;
  private final LogStorage logStorage;
<<<<<<< HEAD
  private final LogStreamMetrics logStreamMetrics;
  private final FlowControl flowControl;
  private final Sequencer sequencer;
  private volatile boolean closed;
=======
  private final CompletableActorFuture<Void> closeFuture;
  private final int nodeId;
  private final Set<FailureListener> failureListeners = new HashSet<>();
  private ActorFuture<LogStorageAppender> appenderFuture;
  private Sequencer sequencer;
  private LogStorageAppender appender;
  private Throwable closeError; // set if any error occurred during closeAsync
  private final String actorName;
  private HealthReport healthReport = HealthReport.healthy(this);
  private final MeterRegistry meterRegistry;
>>>>>>> df85a699 (refactor: migrate sequencer metrics to micrometer)

  LogStreamImpl(
      final String logName,
      final int partitionId,
      final int maxFragmentSize,
      final LogStorage logStorage,
<<<<<<< HEAD
      final InstantSource clock,
      final Limit requestLimit,
      final RateLimit writeRateLimit) {
    this.logName = logName;
=======
      final MeterRegistry meterRegistry) {
    this.actorSchedulingService = actorSchedulingService;
    this.logName = logName;
    this.meterRegistry = meterRegistry;

>>>>>>> df85a699 (refactor: migrate sequencer metrics to micrometer)
    this.partitionId = partitionId;
    this.logStorage = logStorage;
    logStreamMetrics = new LogStreamMetrics(partitionId);
    flowControl = new FlowControl(logStreamMetrics, requestLimit, writeRateLimit);
    sequencer =
        new Sequencer(
            logStorage,
            getWriteBuffersInitialPosition(),
            maxFragmentSize,
            clock,
            new SequencerMetrics(partitionId),
            flowControl);
    logStorage.addCommitListener(this);
  }

  @Override
  public void close() {
    closed = true;
    LOG.info("Closing {} with {} readers", logName, readers.size());
    readers.forEach(LogStreamReader::close);
    logStorage.removeCommitListener(this);
    logStreamMetrics.remove();
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
    ensureOpen();
    return createLogStreamReader();
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
  public void registerRecordAvailableListener(final LogRecordAwaiter recordAwaiter) {
    ensureOpen();
    recordAwaiters.add(recordAwaiter);
  }

  @Override
  public void removeRecordAvailableListener(final LogRecordAwaiter recordAwaiter) {
    ensureOpen();
    recordAwaiters.remove(recordAwaiter);
  }

  @Override
  public void onCommit() {
    if (closed) {
      // This can be called by the raft thread after we've already closed the log stream.
      // We can just ignore it in that case. Using `ensureOpen` would throw an exception that would
      // break the raft thread.
      return;
    }
    recordAwaiters.forEach(LogRecordAwaiter::onRecordAvailable);
  }

  private void ensureOpen() {
    if (closed) {
      throw new IllegalStateException("%s is closed".formatted(logName));
    }
  }

  private LogStreamReader createLogStreamReader() {
    final var newReader = new LogStreamReaderImpl(logStorage.newReader());
    readers.add(newReader);
    return newReader;
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

<<<<<<< HEAD
=======
  private Sequencer createAndScheduleWriteBuffer(final long initialPosition) {
    return new Sequencer(initialPosition, maxFragmentSize, new SequencerMetrics(meterRegistry));
  }

  private ActorFuture<Void> createAndScheduleLogStorageAppender(final Sequencer sequencer) {
    appender =
        new LogStorageAppender(
            buildActorName("LogAppender", partitionId), partitionId, logStorage, sequencer);
    return actorSchedulingService.submitActor(appender);
  }

>>>>>>> df85a699 (refactor: migrate sequencer metrics to micrometer)
  private long getLastCommittedPosition() {
    try (final var storageReader = logStorage.newReader();
        final var logStreamReader = new LogStreamReaderImpl(storageReader)) {
      return logStreamReader.seekToEnd();
    }
  }
}
