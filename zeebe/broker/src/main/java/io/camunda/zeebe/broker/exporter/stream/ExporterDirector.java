/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.exporter.stream;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirectorContext.ExporterMode;
import io.camunda.zeebe.broker.system.partitions.PartitionMessagingService;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.logstreams.log.LogRecordAwaiter;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.retry.BackOffRetryStrategy;
import io.camunda.zeebe.scheduler.retry.RetryStrategy;
import io.camunda.zeebe.stream.api.EventFilter;
import io.camunda.zeebe.stream.impl.records.RecordValues;
import io.camunda.zeebe.stream.impl.records.TypedRecordImpl;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.exception.UnrecoverableException;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthReport;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.agrona.LangUtil;
import org.slf4j.Logger;

public final class ExporterDirector extends Actor implements HealthMonitorable, LogRecordAwaiter {

  private static final String ERROR_MESSAGE_DESERIALIZATION_ERROR_EXPORTING_ABORTED =
      "Expected to export record '{}' successfully, but exception was thrown when deserializing the record.";
  private static final String ERROR_MESSAGE_EXPORTING_ABORTED =
      "Expected to export record '{}' successfully, but exception was thrown.";
  private static final String ERROR_MESSAGE_RECOVER_FROM_SNAPSHOT_FAILED =
      "Expected to find event with the snapshot position %s in log stream, but nothing was found. Failed to recover '%s'.";
  private static final String EXPORTER_STATE_TOPIC_FORMAT = "exporterState-%d";

  private static final Logger LOG = Loggers.EXPORTER_LOGGER;
  private final AtomicBoolean isOpened = new AtomicBoolean(false);
  private final List<ExporterContainer> containers;
  private final LogStream logStream;
  private final RecordExporter recordExporter;
  private final ZeebeDb zeebeDb;
  private final ExporterMetrics metrics;
  private final String name;
  private final RetryStrategy exportingRetryStrategy;
  private final Set<FailureListener> listeners = new HashSet<>();
  private LogStreamReader logStreamReader;
  private EventFilter eventFilter;
  private ExportersState state;

  @SuppressWarnings("java:S3077") // allow volatile here, health is immutable
  private volatile HealthReport healthReport = HealthReport.healthy(this);

  private boolean inExportingPhase;
  private boolean isHardPaused;
  private ExporterPhase exporterPhase;
  private final PartitionMessagingService partitionMessagingService;
  private final String exporterPositionsTopic;
  private final ExporterMode exporterMode;
  private final Duration distributionInterval;
  private ExporterStateDistributionService exporterDistributionService;
  private final int partitionId;
  private final EventFilter positionsToSkipFilter;
  private final MeterRegistry meterRegistry;

  public ExporterDirector(final ExporterDirectorContext context, final boolean shouldPauseOnStart) {
    this(context, shouldPauseOnStart, Function.identity());
  }

  @VisibleForTesting
  ExporterDirector(
      final ExporterDirectorContext context,
      final boolean shouldPauseOnStart,
      final Function<RecordExporter, RecordExporter> recorderExporter) {
    name = context.getName();

    logStream = Objects.requireNonNull(context.getLogStream());
    partitionId = logStream.getPartitionId();
    meterRegistry = context.getMeterRegistry();
    containers =
        context.getDescriptors().stream()
            .map(descriptor -> new ExporterContainer(descriptor, partitionId, meterRegistry))
            .collect(Collectors.toList());
    metrics = new ExporterMetrics(meterRegistry);
    recordExporter = recorderExporter.apply(new RecordExporter(metrics, containers, partitionId));
    exportingRetryStrategy = new BackOffRetryStrategy(actor, Duration.ofSeconds(10));
    zeebeDb = context.getZeebeDb();
    isHardPaused = shouldPauseOnStart;
    partitionMessagingService = context.getPartitionMessagingService();
    exporterPositionsTopic = String.format(EXPORTER_STATE_TOPIC_FORMAT, partitionId);
    exporterMode = context.getExporterMode();
    distributionInterval = context.getDistributionInterval();
    positionsToSkipFilter = context.getPositionsToSkipFilter();
  }

  public ActorFuture<Void> startAsync(final ActorSchedulingService actorSchedulingService) {
    return actorSchedulingService.submitActor(this, SchedulingHints.ioBound());
  }

  public ActorFuture<Void> stopAsync() {
    return actor.close();
  }

  /**
   * This method enables us to pause the exporting of records. No records are exported until resume
   * exporting is invoked.
   *
   * <p>If the exporter is soft paused and pauseExporting is called, the exporter will be "hard"
   * paused.
   */
  public ActorFuture<Void> pauseExporting() {
    if (actor.isClosed()) {
      // Actor can be closed when there are no exporters. In that case pausing is a no-op.
      // This is safe because the pausing state is persisted and will be applied later if exporters
      // are added.
      return CompletableActorFuture.completed(null);
    }
    return actor.call(
        () -> {
          isHardPaused = true;
          exporterPhase = ExporterPhase.PAUSED;
        });
  }

  /**
   * When the exporter is soft paused, we keep exporting the records without updating the exporter
   * state. Upon resuming, the exporter is updated with the position and metadata of the last
   * exported record.
   *
   * <p>If the exporter is hard paused and softPauseExporting is called, the exporter will be soft
   * paused.
   */
  public ActorFuture<Void> softPauseExporting() {
    if (actor.isClosed()) {
      // Actor can be closed when there are no exporters. In that case pausing is a no-op.
      // This is safe because the pausing state is persisted and will be applied later if exporters
      // are added.
      return CompletableActorFuture.completed(null);
    }
    return actor.call(
        () -> {
          isHardPaused = false;
          containers.stream().forEach(ExporterContainer::softPauseExporter);
          exporterPhase = ExporterPhase.SOFT_PAUSED;
        });
  }

  /**
   * This method enables us to resume the exporting of records after it has been paused. It works
   * both to resume the "soft pause" state and the "paused" state. Upon resuming, the exporter is
   * updated with the position and metadata of the last exported record.
   */
  public ActorFuture<Void> resumeExporting() {
    if (actor.isClosed()) {
      // Actor can be closed when there are no exporters. In that case resuming is a no-op.
      // This is safe because adding exporters requires a restart where the persisted non-pause
      // state will be applied and exporting "resumes".
      return CompletableActorFuture.completed(null);
    }

    return actor.call(
        () -> {
          isHardPaused = false;
          if (exporterPhase == ExporterPhase.SOFT_PAUSED) {
            containers.stream().forEach(ExporterContainer::undoSoftPauseExporter);
          }
          exporterPhase = ExporterPhase.EXPORTING;
          if (exporterMode == ExporterMode.ACTIVE) {
            actor.submit(this::readNextEvent);
          }
        });
  }

  public ActorFuture<ExporterPhase> getPhase() {
    if (actor.isClosed()) {
      return CompletableActorFuture.completed(ExporterPhase.CLOSED);
    }
    return actor.call(() -> exporterPhase);
  }

  @Override
  protected Map<String, String> createContext() {
    final var context = super.createContext();
    context.put(ACTOR_PROP_PARTITION_ID, Integer.toString(partitionId));
    return context;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  protected void onActorStarting() {
    if (exporterMode == ExporterMode.ACTIVE) {
      final ActorFuture<LogStreamReader> newReaderFuture = logStream.newLogStreamReader();
      actor.runOnCompletionBlockingCurrentPhase(
          newReaderFuture,
          (reader, errorOnReceivingReader) -> {
            if (errorOnReceivingReader == null) {
              logStreamReader = reader;
            } else {
              // TODO https://github.com/zeebe-io/zeebe/issues/3499
              // ideally we could fail the actor start future such that we are able to propagate the
              // error
              LOG.error(
                  "Unexpected error on retrieving reader from log {}",
                  logStream.getLogName(),
                  errorOnReceivingReader);
              actor.close();
            }
          });
    }
  }

  @Override
  protected void onActorStarted() {
    try {
      LOG.debug("Recovering exporter from snapshot");
      recoverFromSnapshot();
      exporterDistributionService =
          new ExporterStateDistributionService(
              this::consumeExporterStateFromLeader,
              partitionMessagingService,
              exporterPositionsTopic);

      // Initialize containers irrespective of if it is Active or Passive mode
      initContainers();
    } catch (final Exception e) {
      onFailure();
      LangUtil.rethrowUnchecked(e);
    }

    isOpened.set(true);

    // remove exporters from state
    // which are no longer in our configuration
    clearExporterState();
    if (exporterMode == ExporterMode.ACTIVE) {
      startActiveExportingMode();
    } else { // PASSIVE, we consume the messages and set it in our state
      startPassiveExportingMode();
    }
  }

  @Override
  protected void onActorClosing() {
    if (logStreamReader != null) {
      logStreamReader.close();
    }
    logStream.removeRecordAvailableListener(this);
  }

  @Override
  protected void onActorClosed() {
    LOG.debug("Closed exporter director '{}'.", getName());
    exporterPhase = ExporterPhase.CLOSED;
  }

  @Override
  protected void onActorCloseRequested() {
    isOpened.set(false);
    containers.forEach(ExporterContainer::close);
    exporterDistributionService.close();
  }

  @Override
  protected void handleFailure(final Throwable failure) {
    LOG.error(
        "Actor '{}' failed in phase {} with: {} .",
        name,
        actor.getLifecyclePhase(),
        failure,
        failure);
    actor.fail(failure);

    if (failure instanceof UnrecoverableException) {
      healthReport = HealthReport.dead(this).withIssue(failure);

      for (final var listener : listeners) {
        listener.onUnrecoverableFailure(healthReport);
      }
    } else {
      healthReport = HealthReport.unhealthy(this).withIssue(failure);
      for (final var listener : listeners) {
        listener.onFailure(healthReport);
      }
    }
  }

  private void consumeExporterStateFromLeader(
      final String exporterId,
      final ExporterStateDistributeMessage.ExporterStateEntry exporterState) {

    if (state.getPosition(exporterId) < exporterState.position()) {
      state.setExporterState(exporterId, exporterState.position(), exporterState.metadata());
    }
  }

  private void initContainers() throws Exception {
    for (final ExporterContainer container : containers) {
      container.initContainer(actor, metrics, state);
      container.configureExporter();
    }

    eventFilter = positionsToSkipFilter.and(createEventFilter(containers));
    LOG.debug("Set event filter for exporters: {}", eventFilter);
  }

  private void recoverFromSnapshot() {
    state = new ExportersState(zeebeDb, zeebeDb.createContext());
    final long snapshotPosition = state.getLowestPosition();
    LOG.debug(
        "Recovered exporter '{}' from snapshot at lastExportedPosition {}",
        getName(),
        snapshotPosition);
  }

  private ExporterEventFilter createEventFilter(final List<ExporterContainer> containers) {

    final List<Context.RecordFilter> recordFilters =
        containers.stream().map(c -> c.getContext().getFilter()).collect(Collectors.toList());

    final Map<RecordType, Boolean> acceptRecordTypes =
        Arrays.stream(RecordType.values())
            .collect(
                Collectors.toMap(
                    Function.identity(),
                    type -> recordFilters.stream().anyMatch(f -> f.acceptType(type))));

    final Map<ValueType, Boolean> acceptValueTypes =
        Arrays.stream(ValueType.values())
            .collect(
                Collectors.toMap(
                    Function.identity(),
                    type -> recordFilters.stream().anyMatch(f -> f.acceptValue(type))));

    return new ExporterEventFilter(acceptRecordTypes, acceptValueTypes);
  }

  private void onFailure() {
    isOpened.set(false);
    actor.close();
  }

  private void startActiveExportingMode() {
    logStream.registerRecordAvailableListener(this);

    // start reading
    for (final ExporterContainer container : containers) {
      container.initPosition();
      container.openExporter();
    }

    if (state.hasExporters()) {
      final long snapshotPosition = state.getLowestPosition();
      final boolean failedToRecoverReader = !logStreamReader.seekToNextEvent(snapshotPosition);
      if (failedToRecoverReader) {
        throw new IllegalStateException(
            String.format(ERROR_MESSAGE_RECOVER_FROM_SNAPSHOT_FAILED, snapshotPosition, getName()));
      }
      if (!isHardPaused) {
        exporterPhase = ExporterPhase.EXPORTING;
        actor.submit(this::readNextEvent);
      } else {
        exporterPhase = ExporterPhase.PAUSED;
      }

      actor.runAtFixedRate(distributionInterval, this::distributeExporterState);

    } else {
      actor.close();
    }
  }

  private void startPassiveExportingMode() {
    // Only initialize the positions, do not open and start exporting
    for (final ExporterContainer container : containers) {
      container.initPosition();
    }

    if (state.hasExporters()) {
      exporterDistributionService.subscribeForExporterState(actor::run);
    } else {
      actor.close();
    }
  }

  private void distributeExporterState() {
    final var exporterStateMessage = new ExporterStateDistributeMessage();
    state.visitExporterState(
        (exporterId, exporterStateEntry) ->
            exporterStateMessage.putExporter(
                exporterId, exporterStateEntry.getPosition(), exporterStateEntry.getMetadata()));
    exporterDistributionService.distributeExporterState(exporterStateMessage);
  }

  private void skipRecord(final LoggedEvent currentEvent) {
    final RecordMetadata metadata = new RecordMetadata();
    final long eventPosition = currentEvent.getPosition();

    currentEvent.readMetadata(metadata);
    metrics.eventSkipped(metadata.getValueType());

    // increase position of all up to date exporters - an up to date exporter is one which has
    // acknowledged the last record we passed to it
    for (final ExporterContainer container : containers) {
      container.updatePositionOnSkipIfUpToDate(eventPosition);
    }

    actor.submit(this::readNextEvent);
  }

  private void readNextEvent() {
    if (shouldExport()) {
      final LoggedEvent currentEvent = logStreamReader.next();
      if (eventFilter == null || eventFilter.applies(currentEvent)) {
        inExportingPhase = true;
        exportEvent(currentEvent);
      } else {
        skipRecord(currentEvent);
      }
    }
  }

  private boolean shouldExport() {
    return isOpened.get() && logStreamReader.hasNext() && !inExportingPhase && !isHardPaused;
  }

  private void exportEvent(final LoggedEvent event) {
    try {
      recordExporter.wrap(event);
    } catch (final Exception exception) {
      LOG.warn(ERROR_MESSAGE_DESERIALIZATION_ERROR_EXPORTING_ABORTED, event, exception);
      onFailure();
      return;
    }

    final ActorFuture<Boolean> retryFuture =
        exportingRetryStrategy.runWithRetry(recordExporter::export, this::isClosed);

    actor.runOnCompletion(
        retryFuture,
        (bool, throwable) -> {
          if (throwable != null) {
            LOG.error(ERROR_MESSAGE_EXPORTING_ABORTED, event, throwable);
            onFailure();
          } else {
            metrics.eventExported(recordExporter.getTypedEvent().getValueType());
            inExportingPhase = false;
            actor.submit(this::readNextEvent);
          }
        });
  }

  private void clearExporterState() {
    final List<String> exporterIds =
        containers.stream().map(ExporterContainer::getId).collect(Collectors.toList());

    state.visitExporterState(
        (exporterId, exporterStateEntry) -> {
          if (!exporterIds.contains(exporterId)) {
            state.removeExporterState(exporterId);
            LOG.info(
                "The exporter '{}' is not configured anymore. Its lastExportedPosition is removed from the state.",
                exporterId);
          }
        });
  }

  private boolean isClosed() {
    return !isOpened.get();
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
  public void removeFailureListener(final FailureListener failureListener) {
    actor.run(() -> listeners.remove(failureListener));
  }

  @Override
  public void onRecordAvailable() {
    actor.run(this::readNextEvent);
  }

  public ActorFuture<Long> getLowestPosition() {
    if (actor.isClosed()) {
      return CompletableActorFuture.completed(ExportersState.VALUE_NOT_FOUND);
    }
    return actor.call(() -> state.getLowestPosition());
  }

  @VisibleForTesting
  public static class RecordExporter {

    private final RecordValues recordValues = new RecordValues();
    private final RecordMetadata rawMetadata = new RecordMetadata();
    private final List<ExporterContainer> containers;
    private final TypedRecordImpl typedEvent;
    private final ExporterMetrics exporterMetrics;

    private boolean shouldExport;
    private int exporterIndex;

    RecordExporter(
        final ExporterMetrics exporterMetrics,
        final List<ExporterContainer> containers,
        final int partitionId) {
      this.containers = containers;
      typedEvent = new TypedRecordImpl(partitionId);
      this.exporterMetrics = exporterMetrics;
    }

    void wrap(final LoggedEvent rawEvent) {
      rawEvent.readMetadata(rawMetadata);

      final UnifiedRecordValue recordValue =
          recordValues.readRecordValue(rawEvent, rawMetadata.getValueType());

      shouldExport = recordValue != null;
      if (shouldExport) {
        typedEvent.wrap(rawEvent, rawMetadata, recordValue);
        exporterIndex = 0;
      }
    }

    public boolean export() {
      if (!shouldExport) {
        return true;
      }

      final int exportersCount = containers.size();

      // current error handling strategy is simply to repeat forever until the record can be
      // successfully exported.
      while (exporterIndex < exportersCount) {
        final ExporterContainer container = containers.get(exporterIndex);

        if (container.exportRecord(rawMetadata, typedEvent)) {
          exporterIndex++;
          exporterMetrics.setLastExportedPosition(container.getId(), typedEvent.getPosition());
        } else {
          return false;
        }
      }

      return true;
    }

    TypedRecordImpl getTypedEvent() {
      return typedEvent;
    }
  }

  private static class ExporterEventFilter implements EventFilter {

    private final RecordMetadata metadata = new RecordMetadata();
    private final Map<RecordType, Boolean> acceptRecordTypes;
    private final Map<ValueType, Boolean> acceptValueTypes;

    ExporterEventFilter(
        final Map<RecordType, Boolean> acceptRecordTypes,
        final Map<ValueType, Boolean> acceptValueTypes) {
      this.acceptRecordTypes = acceptRecordTypes;
      this.acceptValueTypes = acceptValueTypes;
    }

    @Override
    public boolean applies(final LoggedEvent event) {
      event.readMetadata(metadata);

      final RecordType recordType = metadata.getRecordType();
      final ValueType valueType = metadata.getValueType();

      return acceptRecordTypes.get(recordType) && acceptValueTypes.get(valueType);
    }

    @Override
    public String toString() {
      return "ExporterEventFilter{"
          + "acceptRecordTypes="
          + acceptRecordTypes
          + ", acceptValueTypes="
          + acceptValueTypes
          + '}';
    }
  }
}
