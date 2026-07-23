/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.logstreams.log.LogRecordAwaiter;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.protocol.impl.encoding.RecordMetadataBlock;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.retry.BackOffRetryStrategy;
import io.camunda.zeebe.scheduler.retry.RetryStrategy;
import io.camunda.zeebe.stream.api.EventFilter;
import io.camunda.zeebe.util.exception.UnrecoverableException;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthReport;
import java.time.Duration;
import java.time.InstantSource;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.slf4j.Logger;

/**
 * Drives a single exporter: owns its own {@link LogStreamReader}, its own retry strategy and its
 * own health, entirely independent of any other exporter on the same partition. A slow, stuck, or
 * failing exporter therefore cannot block or take down any other exporter.
 *
 * <p>The corresponding {@link ExporterContainer} is constructed, configured and opened by {@link
 * ExporterDirector} before this actor is scheduled; likewise the {@link LogStreamReader} passed in
 * is already created by the director so that a replay request from within {@code exporter.open()}
 * (which runs on the director's thread) can seek it immediately. This actor takes ownership of the
 * reader from construction on and owns the read-export-retry loop.
 */
final class ExporterActor extends Actor implements HealthMonitorable, LogRecordAwaiter {

  private static final String ERROR_MESSAGE_DESERIALIZATION_ERROR_EXPORTING_ABORTED =
      "Expected to export record '{}' successfully, but exception was thrown when deserializing the record.";
  private static final String ERROR_MESSAGE_EXPORTING_ABORTED =
      "Expected to export record '{}' successfully, but exception was thrown.";
  private static final String ERROR_MESSAGE_RECOVER_FROM_SNAPSHOT_FAILED =
      "Expected to find event with the snapshot position %s in log stream, but nothing was found. Failed to recover '%s'.";

  private static final Logger LOG = Loggers.EXPORTER_LOGGER;

  private final AtomicBoolean isOpened = new AtomicBoolean(false);
  private final ExporterContainer container;
  private final LogStream logStream;
  private final LogStreamReader logStreamReader;
  private final AtomicBoolean hasStartedExporting;
  private final ExporterMetrics metrics;
  private final RetryStrategy exportingRetryStrategy;
  private final Set<FailureListener> listeners = new HashSet<>();
  private final EventFilter eventFilter;
  private final InstantSource clock;
  private final RecordMetadataBlock skipRecordDecoder = new RecordMetadataBlock();
  private final Function<RecordExporter, RecordExporter> recordExporterWrapper;
  private final int partitionIdNumber;
  private final ZeebeDb zeebeDb;

  private RecordExporter recordExporter;
  private boolean inExportingPhase;
  private volatile ExporterPhase phase;

  @SuppressWarnings("java:S3077") // allow volatile here, health is immutable
  private volatile HealthReport healthReport;

  ExporterActor(
      final PartitionId partitionId,
      final LogStream logStream,
      final ExporterContainer container,
      final LogStreamReader logStreamReader,
      final AtomicBoolean hasStartedExporting,
      final ExporterMetrics metrics,
      final EventFilter positionsToSkipFilter,
      final InstantSource clock,
      final ExporterPhase initialPhase,
      final ZeebeDb zeebeDb,
      final Function<RecordExporter, RecordExporter> recordExporterWrapper) {
    super("Exporter-" + container.getId(), partitionId);
    this.logStream = logStream;
    this.container = container;
    this.logStreamReader = logStreamReader;
    this.hasStartedExporting = hasStartedExporting;
    this.metrics = metrics;
    this.clock = clock;
    this.zeebeDb = zeebeDb;
    this.recordExporterWrapper = recordExporterWrapper;
    partitionIdNumber = partitionId.number();
    phase = initialPhase;
    exportingRetryStrategy = new BackOffRetryStrategy(actor, Duration.ofSeconds(10));
    eventFilter = positionsToSkipFilter.and(ExporterEventFilter.forSingleContainer(container));
    healthReport = HealthReport.healthy(this);
  }

  ActorFuture<Void> startAsync(final ActorSchedulingService actorSchedulingService) {
    return actorSchedulingService.submitActor(this, SchedulingHints.ioBound());
  }

  @Override
  protected void onActorStarted() {
    isOpened.set(true);
    recordExporter =
        recordExporterWrapper.apply(
            new RecordExporter(metrics, List.of(container), partitionIdNumber, clock));

    // Rebind the container's Controller (scheduleCancellableTask, updateLastExportedRecordPosition,
    // etc.) from this director's actor/state - used while the container was being configured and
    // opened on the director's own thread - to this actor's own ActorControl and its own
    // independent ExportersState/TransactionContext. ActorControl.schedule(...) and friends require
    // being called from their owning actor's own thread, and RocksDB TransactionContexts are not
    // thread-safe for concurrent use, so from this point on the exporter must be driven exclusively
    // through this actor's own resources.
    final var ownState = new ExportersState(zeebeDb, zeebeDb.createContext());
    container.initContainer(actor, metrics, ownState, phase);

    try {
      startExportingFrom(container.getPosition());
    } catch (final Exception e) {
      updateHealthStatusWithError(new UnrecoverableException(e));
      onFailure();
    }
  }

  private void startExportingFrom(final long position) {
    final boolean failedToRecoverReader = !logStreamReader.seekToNextEvent(position);
    if (failedToRecoverReader) {
      throw new IllegalStateException(
          String.format(ERROR_MESSAGE_RECOVER_FROM_SNAPSHOT_FAILED, position, getName()));
    }

    hasStartedExporting.set(true);
    logStream.registerRecordAvailableListener(this);
    if (!phase.equals(ExporterPhase.PAUSED)) {
      actor.submit(this::readNextEvent);
    }
  }

  private void skipRecord(final LoggedEvent currentEvent) {
    final long eventPosition = currentEvent.getPosition();

    skipRecordDecoder.wrap(currentEvent.getMetadata(), currentEvent.getMetadataOffset());
    metrics.eventSkipped(skipRecordDecoder.valueType());
    container.updatePositionOnSkipIfUpToDate(eventPosition);

    actor.submit(this::readNextEvent);
  }

  private void readNextEvent() {
    if (shouldExport()) {
      final LoggedEvent currentEvent = logStreamReader.next();
      if (eventFilter.applies(currentEvent)) {
        inExportingPhase = true;
        exportEvent(currentEvent);
      } else {
        skipRecord(currentEvent);
      }
    }
  }

  private boolean shouldExport() {
    return isOpened.get()
        && hasStartedExporting.get()
        && logStreamReader.hasNext()
        && !inExportingPhase
        && !phase.equals(ExporterPhase.PAUSED);
  }

  private void exportEvent(final LoggedEvent event) {
    try {
      recordExporter.wrap(event);
    } catch (final Exception exception) {
      LOG.warn(ERROR_MESSAGE_DESERIALIZATION_ERROR_EXPORTING_ABORTED, event, exception);
      updateHealthStatusWithError(new UnrecoverableException(exception));
      onFailure();
      return;
    }

    final AtomicReference<ExportOutcome> lastOutcome = new AtomicReference<>();
    final ActorFuture<Boolean> retryFuture =
        exportingRetryStrategy.runWithRetry(
            () -> {
              final ExportOutcome outcome = recordExporter.export();
              lastOutcome.set(outcome);
              return outcome != ExportOutcome.RETRY;
            },
            this::isClosed);

    actor.runOnCompletion(
        retryFuture,
        (bool, throwable) -> {
          if (throwable != null) {
            LOG.error(ERROR_MESSAGE_EXPORTING_ABORTED, event, throwable);
            onFailure();
          } else if (lastOutcome.get() == ExportOutcome.ABORT_REPLAY) {
            // the record was abandoned because this exporter's own reopen-triggered replay
            // request rewound its reader; it will be redelivered, in order, once reading resumes
            inExportingPhase = false;
            actor.submit(this::readNextEvent);
          } else {
            logStream.getFlowControl().onExported(recordExporter.getTypedEvent().getPosition());
            metrics.eventExported(recordExporter.getTypedEvent().getValueType());
            inExportingPhase = false;
            actor.submit(this::readNextEvent);
          }
        });
  }

  private void updateHealthStatusWithError(final Throwable failure) {
    if (failure instanceof UnrecoverableException) {
      healthReport = HealthReport.dead(this).withIssue(failure, clock.instant());
      listeners.forEach(l -> l.onUnrecoverableFailure(healthReport));
    } else {
      healthReport = HealthReport.unhealthy(this).withIssue(failure, clock.instant());
      listeners.forEach(l -> l.onFailure(healthReport));
    }
  }

  private void onFailure() {
    isOpened.set(false);
    actor.close();
  }

  private boolean isClosed() {
    return !isOpened.get();
  }

  ActorFuture<Void> pauseExporting() {
    if (actor.isClosed()) {
      return CompletableActorFuture.completed(null);
    }
    return actor.call(
        () -> {
          phase = ExporterPhase.PAUSED;
        });
  }

  ActorFuture<Void> softPauseExporting() {
    if (actor.isClosed()) {
      return CompletableActorFuture.completed(null);
    }
    return actor.call(
        () -> {
          container.softPauseExporter();
          phase = ExporterPhase.SOFT_PAUSED;
          // the read loop is dormant if we were hard-paused before; soft-pause still exports
          // records (just without persisting position), so it must resume reading too.
          if (hasStartedExporting.get()) {
            actor.submit(this::readNextEvent);
          }
        });
  }

  ActorFuture<Void> resumeExporting() {
    if (actor.isClosed()) {
      return CompletableActorFuture.completed(null);
    }
    return actor.call(
        () -> {
          if (phase == ExporterPhase.SOFT_PAUSED) {
            container.undoSoftPauseExporter();
          }
          phase = ExporterPhase.EXPORTING;
          if (hasStartedExporting.get()) {
            actor.submit(this::readNextEvent);
          }
        });
  }

  @Override
  protected void handleFailure(final Throwable failure) {
    LOG.error(
        "Actor '{}' failed in phase {} with: {} .",
        getName(),
        actor.getLifecyclePhase(),
        failure,
        failure);
    actor.fail(failure);
    updateHealthStatusWithError(failure);
  }

  @Override
  protected void onActorClosing() {
    logStreamReader.close();
    logStream.removeRecordAvailableListener(this);
  }

  @Override
  protected void onActorCloseRequested() {
    isOpened.set(false);
    // Close the exporter itself from within this actor's own close sequence (not from the
    // director, after this actor has already fully terminated): exporter.close() may call
    // controller.updateLastExportedRecordPosition(...), which does actor.run(...) against this
    // same actor - that only still works while this actor is mid-close, not once it is dead.
    container.close();
  }

  @Override
  protected void onActorClosed() {
    LOG.debug("Closed exporter actor '{}'.", getName());
    phase = ExporterPhase.CLOSED;
  }

  @Override
  public void onRecordAvailable() {
    actor.run(this::readNextEvent);
  }

  @Override
  public String componentName() {
    return getName();
  }

  @Override
  public HealthReport getHealthReport() {
    return healthReport;
  }

  @Override
  public void addFailureListener(final FailureListener listener) {
    actor.run(() -> listeners.add(listener));
  }

  @Override
  public void removeFailureListener(final FailureListener listener) {
    actor.run(() -> listeners.remove(listener));
  }
}
