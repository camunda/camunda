/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirectorContext.ExporterMode;
import io.camunda.zeebe.broker.system.partitions.PartitionMessagingService;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.logstreams.log.LogRecordAwaiter;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.ScheduledTimer;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.retry.BackOffRetryStrategy;
import io.camunda.zeebe.scheduler.retry.EndlessRetryStrategy;
import io.camunda.zeebe.scheduler.retry.RetryStrategy;
import io.camunda.zeebe.stream.api.EventFilter;
import io.camunda.zeebe.util.exception.UnrecoverableException;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthReport;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.InstantSource;
import java.util.ArrayList;
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

  private static final String ERROR_MESSAGE_EXPORTING_ABORTED =
      "Expected to export record '{}' successfully, but exception was thrown.";
  private static final String ERROR_MESSAGE_RECOVER_FROM_SNAPSHOT_FAILED =
      "Expected to find event with the snapshot position %s in log stream, but nothing was found. Failed to recover '%s'.";
  private static final String EXPORTER_STATE_TOPIC_FORMAT = "exporterState-%d";

  private static final Logger LOG = Loggers.EXPORTER_LOGGER;
  private final AtomicBoolean isOpened = new AtomicBoolean(false);

  // Use concrete type because it must be modifiable
  private final ArrayList<ExporterContainer> containers;
  private final LogStream logStream;
  private final RecordExporter recordExporter;
  private final ZeebeDb zeebeDb;
  private final ExporterMetrics metrics;
  private final String name;
  private final RetryStrategy exportingRetryStrategy;
  private final RetryStrategy recordWrapStrategy;
  private final Set<FailureListener> listeners = new HashSet<>();
  private LogStreamReader logStreamReader;
  private EventFilter eventFilter;
  private ExportersState state;

  @SuppressWarnings("java:S3077") // allow volatile here, health is immutable
  private volatile HealthReport healthReport;

  private boolean inExportingPhase;
  private ExporterPhase exporterPhase;
  private final PartitionMessagingService partitionMessagingService;
  private final String exporterPositionsTopic;
  private final ExporterMode exporterMode;
  private final Duration distributionInterval;
  private ExporterStateDistributionService exporterDistributionService;
  private ScheduledTimer exporterDistributionTimer;
  private final int partitionId;
  private final EventFilter positionsToSkipFilter;
  private final MeterRegistry meterRegistry;
  // When idle, exporter director is not exporting any records because no exporters are configured.
  // The actor is still running, but it is not actively doing any work.
  private boolean idle;
  private final InstantSource clock;

  public ExporterDirector(
      final ExporterDirectorContext context, final ExporterPhase exporterPhase) {
    name = context.getName();
    logStream = Objects.requireNonNull(context.getLogStream());
    partitionId = logStream.getPartitionId();
    meterRegistry = context.getMeterRegistry();
    clock = context.getClock();
    containers =
        context.getDescriptors().entrySet().stream()
            .map(
                descriptorEntry ->
                    new ExporterContainer(
                        descriptorEntry.getKey(),
                        partitionId,
                        descriptorEntry.getValue(),
                        meterRegistry,
                        clock))
            .collect(Collectors.toCollection(ArrayList::new));
    metrics = new ExporterMetrics(meterRegistry);
    metrics.initializeExporterState(exporterPhase);
    recordExporter = new RecordExporter(metrics, containers, partitionId, clock);
    exportingRetryStrategy = new BackOffRetryStrategy(actor, Duration.ofSeconds(10));
    recordWrapStrategy = new EndlessRetryStrategy(actor);
    zeebeDb = context.getZeebeDb();
    this.exporterPhase = exporterPhase;
    partitionMessagingService = context.getPartitionMessagingService();
    exporterPositionsTopic = String.format(EXPORTER_STATE_TOPIC_FORMAT, partitionId);
    exporterMode = context.getExporterMode();
    distributionInterval = context.getDistributionInterval();
    positionsToSkipFilter = context.getPositionsToSkipFilter();

    // needs name to be initialized
    healthReport = HealthReport.healthy(this);
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
          metrics.setExporterPaused();
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
          containers.stream().forEach(ExporterContainer::softPauseExporter);
          exporterPhase = ExporterPhase.SOFT_PAUSED;
          metrics.setExporterSoftPaused();
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
          if (exporterPhase == ExporterPhase.SOFT_PAUSED) {
            containers.stream().forEach(ExporterContainer::undoSoftPauseExporter);
          }
          exporterPhase = ExporterPhase.EXPORTING;
          metrics.setExporterActive();
          if (exporterMode == ExporterMode.ACTIVE) {
            actor.submit(this::readNextEvent);
          }
        });
  }

  /**
   * Disables an already configured exporter. No records will be exported to this exporter anymore.
   * We will not wait for acknowledgments for this exporter, allowing the log to be compacted.
   *
   * @param exporterId id of the exporter to disabled
   * @return future which will be completed after the exporter is disabled.
   */
  public ActorFuture<Void> disableExporter(final String exporterId) {
    if (actor.isClosed()) {
      return CompletableActorFuture.completed(null);
    }

    return actor.call(() -> removeExporter(exporterId));
  }

  private void removeExporter(final String exporterId) {
    containers.stream()
        .filter(c -> c.getId().equals(exporterId))
        .findFirst()
        .ifPresentOrElse(
            container -> removeExporter(exporterId, container),
            () -> LOG.debug("Exporter '{}' is not found. It may be already removed.", exporterId));
  }

  private void removeExporter(final String exporterId, final ExporterContainer container) {
    container.close();
    containers.remove(container);
    state.removeExporterState(exporterId);
    // After removing this exporter, the exporter index has changed. Reset it so that we don't
    // miss to export the record to any of the exporters whose index has changed.
    recordExporter.resetExporterIndex();
    LOG.debug("Exporter '{}' is removed.", exporterId);

    if (containers.isEmpty()) {
      becomeIdle();
    }
  }

  /**
   * Enables an exporter with the given id and descriptor. The exporter will start exporting records
   * after this operation completes.
   *
   * <p>It is expected that the exporter to initialize from the metadata is of same type as the
   * exporter to enable. The caller of this method must verify that.
   *
   * @param exporterId id of the exporter to enable
   * @param initializationInfo the info required to initialize the exporter state
   * @param descriptor the descriptor of the exporter to enable
   * @return future which will be completed after the exporter is enabled.
   */
  public ActorFuture<Void> enableExporter(
      final String exporterId,
      final ExporterInitializationInfo initializationInfo,
      final ExporterDescriptor descriptor) {
    if (actor.isClosed()) {
      return CompletableActorFuture.completed(null);
    }

    return actor.call(() -> addExporter(exporterId, initializationInfo, descriptor));
  }

  /**
   * Enables an exporter with the given id and descriptor. The exporter will start exporting records
   * after this operation completes. This operation will be retried until successful or the exporter
   * director closes.
   *
   * <p>It is expected that the exporter to initialize from the metadata is of same type as the
   * exporter to enable. The caller of this method must verify that.
   *
   * @param exporterId id of the exporter to enable
   * @param initializationInfo the info required to initialize the exporter state
   * @param descriptor the descriptor of the exporter to enable
   * @return future which will be completed after the exporter is enabled.
   */
  public ActorFuture<Boolean> enableExporterWithRetry(
      final String exporterId,
      final ExporterInitializationInfo initializationInfo,
      final ExporterDescriptor descriptor) {
    return new BackOffRetryStrategy(actor, Duration.ofSeconds(10))
        .runWithRetry(
            () -> {
              try {
                addExporter(exporterId, initializationInfo, descriptor);
                return true;
              } catch (final Exception e) {
                LOG.error("Failed to add exporter '{}'. Retrying...", exporterId, e);
                return false;
              }
            },
            this::isClosed);
  }

  private void addExporter(
      final String exporterId,
      final ExporterInitializationInfo initializationInfo,
      final ExporterDescriptor descriptor) {

    final var exporterEnabled =
        containers.stream().map(ExporterContainer::getId).anyMatch(exporterId::equals);

    if (exporterEnabled) {
      LOG.debug("Exporter '{}' is already enabled. Skipping the enabling operation.", exporterId);
      return;
    }

    final ExporterContainer container =
        new ExporterContainer(descriptor, partitionId, initializationInfo, meterRegistry, clock);
    container.initContainer(actor, metrics, state, exporterPhase);
    try {
      container.configureExporter();
    } catch (final Exception e) {
      LOG.error("Failed to configure exporter '{}'", exporterId, e);
      LangUtil.rethrowUnchecked(e);
    }
    // initializes metadata and position in the runtime state
    container.initMetadata();
    if (exporterMode == ExporterMode.ACTIVE) {
      container.openExporter();
    }
    containers.add(container);
    LOG.debug("Exporter '{}' is enabled.", exporterId);

    if (idle) {
      becomeLive();
    }
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
      logStreamReader = logStream.newLogStreamReader();
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
      healthReport = HealthReport.dead(this).withIssue(failure, clock.instant());

      for (final var listener : listeners) {
        listener.onUnrecoverableFailure(healthReport);
      }
    } else {
      healthReport = HealthReport.unhealthy(this).withIssue(failure, clock.instant());
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
      container.initContainer(actor, metrics, state, exporterPhase);
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
        containers.stream().map(c -> c.getContext().getFilter()).toList();

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

  private void becomeIdle() {
    idle = true;
    LOG.debug("No exporters are configured. Going idle.");
    logStream.removeRecordAvailableListener(this);
    exporterDistributionService.close();
    if (exporterDistributionTimer != null) {
      // closing the service do not stop the repeated timer task scheduled in this actor
      exporterDistributionTimer.cancel();
      exporterDistributionTimer = null;
    }
    if (logStreamReader != null) {
      // We have to close it, otherwise it will prevent journal segment deletion
      logStreamReader.close();
      logStreamReader = null;
    }
  }

  private void becomeLive() {
    LOG.debug("New exporters are configured. Restart exporting.");
    if (exporterMode == ExporterMode.ACTIVE) {
      restartActiveExportingMode();
    } else {
      restartPassiveExportingMode();
    }
    idle = false;
  }

  private void startActiveExportingMode() {
    final var containerOpenFutures = new ArrayList<ActorFuture<Boolean>>();
    for (final ExporterContainer container : containers) {
      container.initMetadata();
      final var openFuture =
          new BackOffRetryStrategy(actor, Duration.ofSeconds(10), Duration.ofMillis(150))
              .runWithRetry(
                  () -> {
                    try {
                      container.openExporter();
                      return true;
                    } catch (final Exception e) {
                      LOG.error("Failed to open exporter '{}'. Retrying...", container.getId(), e);
                      return false;
                    }
                  },
                  this::isClosed);

      containerOpenFutures.add(openFuture);
    }

    // Don't need to handle error as any are caught within the runWithRetry try catch
    actor.runOnCompletion(
        containerOpenFutures,
        (error) -> {
          if (state.hasExporters()) {
            final long snapshotPosition = state.getLowestPosition();
            // start reading and exporting
            startActiveExportingFrom(snapshotPosition);
          } else {
            becomeIdle();
          }
        });
  }

  private void restartActiveExportingMode() {
    logStreamReader = logStream.newLogStreamReader();
    startActiveExportingFrom(-1);
  }

  private void startActiveExportingFrom(final long snapshotPosition) {
    final boolean failedToRecoverReader = !logStreamReader.seekToNextEvent(snapshotPosition);
    if (failedToRecoverReader) {
      throw new IllegalStateException(
          String.format(ERROR_MESSAGE_RECOVER_FROM_SNAPSHOT_FAILED, -1, getName()));
    }
    logStream.registerRecordAvailableListener(this);
    if (!exporterPhase.equals(ExporterPhase.PAUSED)) {
      actor.submit(this::readNextEvent);
    }

    exporterDistributionTimer =
        actor.runAtFixedRate(distributionInterval, this::distributeExporterState);
  }

  private void startPassiveExportingMode() {
    // Only initialize the positions, do not open and start exporting
    for (final ExporterContainer container : containers) {
      container.initMetadata();
    }

    if (state.hasExporters()) {
      exporterDistributionService.subscribeForExporterState(actor::run);
    } else {
      becomeIdle();
    }
  }

  private void restartPassiveExportingMode() {
    exporterDistributionService.subscribeForExporterState(actor::run);
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
    return isOpened.get()
        && !idle
        && logStreamReader.hasNext()
        && !inExportingPhase
        && !exporterPhase.equals(ExporterPhase.PAUSED);
  }

  private void exportEvent(final LoggedEvent event) {
    final ActorFuture<Boolean> wrapRetryFuture =
        recordWrapStrategy.runWithRetry(
            () -> {
              recordExporter.wrap(event);
              return true;
            },
            this::isClosed);

    actor.runOnCompletion(
        wrapRetryFuture,
        (b, t) -> {
          assert t == null : "Throwable must be null";

          final ActorFuture<Boolean> retryFuture =
              exportingRetryStrategy.runWithRetry(recordExporter::export, this::isClosed);

          actor.runOnCompletion(
              retryFuture,
              (bool, throwable) -> {
                if (throwable != null) {
                  LOG.error(ERROR_MESSAGE_EXPORTING_ABORTED, event, throwable);
                  onFailure();
                } else {
                  logStream
                      .getFlowControl()
                      .onExported(recordExporter.getTypedEvent().getPosition());
                  metrics.eventExported(recordExporter.getTypedEvent().getValueType());
                  inExportingPhase = false;
                  actor.submit(this::readNextEvent);
                }
              });
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
  public String componentName() {
    return name;
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

  /**
   * @param metadataVersion the version of the metadata to initialize the exporter with
   * @param initializeFrom the id of the exporter to initialize the metadata of the exporter from
   */
  public record ExporterInitializationInfo(long metadataVersion, String initializeFrom) {}

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
