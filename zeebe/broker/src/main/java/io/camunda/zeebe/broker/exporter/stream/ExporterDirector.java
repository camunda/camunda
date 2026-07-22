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
import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirectorContext.ExporterMode;
import io.camunda.zeebe.broker.system.partitions.PartitionMessagingService;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.ScheduledTimer;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.retry.BackOffRetryStrategy;
import io.camunda.zeebe.stream.api.EventFilter;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.exception.UnrecoverableException;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthReport;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.agrona.LangUtil;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Coordinates exporting on a partition. Each configured exporter runs on its own {@link
 * ExporterActor}: its own {@link LogStreamReader}, its own retry/backoff, and its own health,
 * entirely decoupled from every other exporter. This class no longer reads the log or exports
 * records itself; it owns exporter lifecycle (configure/open/close, enable/disable, pause/resume),
 * aggregates the exporters' health into one report, and distributes their combined positions to
 * followers.
 */
public final class ExporterDirector extends Actor implements HealthMonitorable {

  private static final Logger LOG = Loggers.EXPORTER_LOGGER;
  private static final String LEGACY_EXPORTER_STATE_TOPIC_FORMAT = "exporterState-%d";
  private static final String EXPORTER_STATE_TOPIC_FORMAT = "%s-exporterState-%d";

  private final AtomicBoolean isOpened = new AtomicBoolean(false);

  // Use concrete type because it must be modifiable
  private final ArrayList<ExporterContainer> containers = new ArrayList<>();
  private final Map<ExporterDescriptor, ExporterInitializationInfo> initialDescriptors;
  private final LogStream logStream;
  private final ZeebeDb zeebeDb;
  private final ExporterMetrics metrics;
  private final Set<FailureListener> listeners = new HashSet<>();
  private final Function<RecordExporter, RecordExporter> recordExporterWrapper;
  private ExportersState state;

  @SuppressWarnings("java:S3077") // allow volatile here, health is immutable
  private volatile HealthReport healthReport;

  private ExporterPhase exporterPhase;
  private final PartitionMessagingService partitionMessagingService;
  private final String exporterPositionsSendingSubject;
  private final List<String> exporterPositionsReceivingSubjects;
  private final String clusterId;
  private final ExporterMode exporterMode;
  private final Duration distributionInterval;
  private ExporterStateDistributionService exporterDistributionService;
  private ScheduledTimer exporterDistributionTimer;
  private final PartitionId partitionId;
  private final EventFilter positionsToSkipFilter;
  private final MeterRegistry meterRegistry;
  private final @Nullable String licenseKey;
  // When idle, no exporters are configured. The actor is still running, but it is not actively
  // doing any work.
  private boolean idle;
  private final InstantSource clock;

  // one actor per configured exporter, keyed by exporter id
  private final ConcurrentHashMap<String, ExporterActor> exporterActors = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, HealthReport> exporterHealthReports =
      new ConcurrentHashMap<>();
  private ActorSchedulingService actorSchedulingService;
  private final Map<String, PendingContainer> pendingByExporterId = new HashMap<>();

  public ExporterDirector(
      final ExporterDirectorContext context, final ExporterPhase exporterPhase) {
    this(context, exporterPhase, Function.identity());
  }

  @VisibleForTesting
  ExporterDirector(
      final ExporterDirectorContext context,
      final ExporterPhase exporterPhase,
      final Function<RecordExporter, RecordExporter> recordExporterWrapper) {
    super("Exporter", context.getPartitionId());
    logStream = Objects.requireNonNull(context.getLogStream());
    partitionId = context.getPartitionId();
    clusterId = context.getClusterId();
    licenseKey = context.getLicenseKey();
    meterRegistry = context.getMeterRegistry();
    clock = context.getClock();
    this.recordExporterWrapper = recordExporterWrapper;
    initialDescriptors = context.getDescriptors();
    metrics = new ExporterMetrics(meterRegistry);
    metrics.initializeExporterState(exporterPhase);
    zeebeDb = context.getZeebeDb();
    this.exporterPhase = exporterPhase;
    partitionMessagingService = context.getPartitionMessagingService();

    final var exporterPositionsLegacySubject =
        String.format(LEGACY_EXPORTER_STATE_TOPIC_FORMAT, partitionId.number());
    exporterPositionsSendingSubject =
        String.format(EXPORTER_STATE_TOPIC_FORMAT, partitionId.group(), partitionId.number());
    exporterPositionsReceivingSubjects =
        context.isReceiveOnLegacySubject()
            ? List.of(exporterPositionsLegacySubject, exporterPositionsSendingSubject)
            : List.of(exporterPositionsSendingSubject);

    exporterMode = context.getExporterMode();
    distributionInterval = context.getDistributionInterval();
    positionsToSkipFilter = context.getPositionsToSkipFilter();

    // needs name to be initialized
    healthReport = HealthReport.healthy(this);
  }

  public ActorFuture<Void> startAsync(final ActorSchedulingService actorSchedulingService) {
    this.actorSchedulingService = actorSchedulingService;
    return actorSchedulingService.submitActor(this, SchedulingHints.ioBound());
  }

  public ActorFuture<Void> stopAsync() {
    return closeAsync();
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
      return CompletableActorFuture.completed(null);
    }
    return actor.call(
        () -> {
          metrics.setExporterPaused();
          exporterPhase = ExporterPhase.PAUSED;
          exporterActors.values().forEach(ExporterActor::pauseExporting);
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
      return CompletableActorFuture.completed(null);
    }
    return actor.call(
        () -> {
          exporterPhase = ExporterPhase.SOFT_PAUSED;
          metrics.setExporterSoftPaused();
          exporterActors.values().forEach(ExporterActor::softPauseExporting);
        });
  }

  /**
   * This method enables us to resume the exporting of records after it has been paused. It works
   * both to resume the "soft pause" state and the "paused" state. Upon resuming, the exporter is
   * updated with the position and metadata of the last exported record.
   */
  public ActorFuture<Void> resumeExporting() {
    if (actor.isClosed()) {
      return CompletableActorFuture.completed(null);
    }
    return actor.call(
        () -> {
          exporterPhase = ExporterPhase.EXPORTING;
          metrics.setExporterActive();
          exporterActors.values().forEach(ExporterActor::resumeExporting);
        });
  }

  /**
   * Removes the given exporter if it exists. Once removed, no records will be sent to it, and
   * acknowledgments will no longer be awaited, allowing the log to be compacted.
   *
   * @param exporterId ID of the exporter to remove
   * @return a future completed when the removal is done
   */
  public ActorFuture<Void> removeExporter(final String exporterId) {
    if (actor.isClosed()) {
      return CompletableActorFuture.completed(null);
    }

    return actor.call(
        () -> {
          containers.stream()
              .filter(c -> c.getId().equals(exporterId))
              .findFirst()
              .ifPresentOrElse(
                  container -> removeExporter(exporterId, container),
                  () ->
                      LOG.debug(
                          "Exporter '{}' is not found. It may already be removed.", exporterId));
          return null;
        });
  }

  private void removeExporter(final String exporterId, final ExporterContainer container) {
    final var exporterActor = exporterActors.remove(exporterId);
    exporterHealthReports.remove(exporterId);
    containers.remove(container);

    if (exporterActor != null) {
      // The actor closes its own container itself, from within its own close sequence (see
      // ExporterActor#onActorCloseRequested) - closing it here too would close the exporter twice.
      // Wait for that close to finish, then hop back onto this actor's own thread before touching
      // the (not thread-safe) ExportersState.
      exporterActor
          .closeAsync()
          .onComplete(
              (ignored, error) -> actor.run(() -> finishRemovingExporter(exporterId)),
              Runnable::run);
    } else {
      container.close();
      finishRemovingExporter(exporterId);
    }
  }

  private void finishRemovingExporter(final String exporterId) {
    state.removeExporterState(exporterId);
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

  private PendingContainer createContainer(
      final ExporterDescriptor descriptor, final ExporterInitializationInfo initializationInfo) {
    final LogStreamReader reader =
        exporterMode == ExporterMode.ACTIVE ? logStream.newLogStreamReader() : null;
    final AtomicBoolean hasStartedExporting = new AtomicBoolean(false);

    final ExporterContainer container =
        new ExporterContainer(
            descriptor,
            partitionId,
            clusterId,
            licenseKey,
            initializationInfo,
            meterRegistry,
            clock,
            position -> {
              // Replay may be requested at any time once the reader exists - both during the
              // initial open handshake and while exporting is already under way, e.g. when
              // ExporterContainer#reopenExporter reopens the exporter mid-stream to let it re-sync
              // a wrong position. It is only ever invoked from this exporter's own actor thread
              // (synchronously, as part of ExporterContainer#exportRecord), so seeking the reader
              // here never races with that same actor's own read loop.
              if (reader == null) {
                return false;
              }
              return reader.seek(position);
            });
    return new PendingContainer(container, reader, hasStartedExporting);
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

    final var pending = createContainer(descriptor, initializationInfo);
    final ExporterContainer container = pending.container();
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
      if (container.getPosition() == ExportersState.VALUE_NOT_FOUND) {
        // A brand new exporter enabled at runtime (no initializeFrom) must start from now, not
        // from the beginning of the log: unlike an exporter configured at broker startup, this one
        // was never meant to see history that predates it. Fast-forward its own reader to the log's
        // current tail and persist that as its starting position, reusing the same
        // seek+persist mechanism as an exporter-requested replay (just moving forward, not back).
        final long tailPosition = pending.reader().seekToEnd();
        container.requestReplay(tailPosition);
      }
      container.openExporter();
    }
    containers.add(container);
    LOG.debug("Exporter '{}' is enabled.", exporterId);

    if (exporterMode == ExporterMode.ACTIVE) {
      scheduleExporterActor(pending);
    }

    if (idle) {
      becomeLive();
    }
  }

  private void scheduleExporterActor(final PendingContainer pending) {
    final ExporterContainer container = pending.container();
    final var exporterActor =
        new ExporterActor(
            partitionId,
            logStream,
            container,
            pending.reader(),
            pending.hasStartedExporting(),
            metrics,
            positionsToSkipFilter,
            clock,
            exporterPhase,
            zeebeDb,
            recordExporterWrapper);
    exporterActors.put(container.getId(), exporterActor);
    exporterHealthReports.put(container.getId(), HealthReport.healthy(exporterActor));
    // addFailureListener does actor.run(...) under the hood, which needs the actor to already be
    // submitted to the scheduler - so this must come after startAsync, not before.
    exporterActor.startAsync(actorSchedulingService);
    exporterActor.addFailureListener(
        new FailureListener() {
          @Override
          public void onFailure(final HealthReport report) {
            handleExporterHealthChange(container.getId(), report);
          }

          @Override
          public void onRecovered(final HealthReport report) {
            handleExporterHealthChange(container.getId(), report);
          }

          @Override
          public void onUnrecoverableFailure(final HealthReport report) {
            handleExporterHealthChange(container.getId(), report);
          }
        });
  }

  private void handleExporterHealthChange(final String exporterId, final HealthReport report) {
    exporterHealthReports.put(exporterId, report);
    actor.run(this::recomputeAndNotifyHealth);
  }

  private void recomputeAndNotifyHealth() {
    final var aggregated =
        HealthReport.fromChildrenStatus(getName(), exporterHealthReports)
            .orElse(HealthReport.healthy(this));
    healthReport = aggregated;
    listeners.forEach(l -> l.onHealthReport(aggregated));
  }

  public ActorFuture<ExporterPhase> getPhase() {
    if (actor.isClosed()) {
      return CompletableActorFuture.completed(ExporterPhase.CLOSED);
    }
    return actor.call(() -> exporterPhase);
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
              exporterPositionsSendingSubject,
              exporterPositionsReceivingSubjects);

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
    // nothing to do here; exporter actors and containers are torn down in closeAsync()
  }

  @Override
  protected void onActorClosed() {
    LOG.debug("Closed exporter director '{}'.", getName());
    exporterPhase = ExporterPhase.CLOSED;
  }

  @Override
  protected void onActorCloseRequested() {
    isOpened.set(false);
  }

  @Override
  public ActorFuture<Void> closeAsync() {
    // This orchestrates the close sequence across multiple actors (this director's own actor plus
    // every child ExporterActor). Every callback here MUST use the explicit two-argument
    // onComplete(consumer, executor) form: the single-argument overload auto-detects "am I
    // currently running on an actor thread" and, if so, silently drops the registration once that
    // specific actor is already CLOSE_REQUESTED/CLOSED - which is exactly the state a just-closed
    // child actor's thread is in when it runs the next step of this chain. Using Runnable::run
    // makes every step run synchronously wherever the previous step completed, avoiding that trap.
    final ActorFuture<Void> result = new CompletableActorFuture<>();
    final List<ActorFuture<Void>> childCloseFutures =
        exporterActors.values().stream().map(ExporterActor::closeAsync).toList();

    final Runnable finishClosing =
        () -> {
          final ActorFuture<Void> closeContainersFuture =
              actor.isClosed()
                  ? CompletableActorFuture.completed(null)
                  : actor.call(
                      () -> {
                        // Containers with their own ExporterActor already closed themselves (from
                        // within that actor's own close sequence, so that
                        // Controller#updateLastExportedRecordPosition still works during close).
                        // Only containers without an actor (e.g. passive-mode, or one that never
                        // finished opening) need to be closed here.
                        containers.stream()
                            .filter(c -> !exporterActors.containsKey(c.getId()))
                            .forEach(ExporterContainer::close);
                        if (exporterDistributionService != null) {
                          exporterDistributionService.close();
                        }
                      });
          closeContainersFuture.onComplete(
              (ignored, ignoredError) ->
                  super.closeAsync()
                      .onComplete(
                          (v, closeErr) -> {
                            if (closeErr != null) {
                              result.completeExceptionally(closeErr);
                            } else {
                              result.complete(null);
                            }
                          },
                          Runnable::run),
              Runnable::run);
        };

    if (childCloseFutures.isEmpty()) {
      finishClosing.run();
    } else {
      final var remaining = new AtomicInteger(childCloseFutures.size());
      childCloseFutures.forEach(
          f ->
              f.onComplete(
                  (v, err) -> {
                    if (remaining.decrementAndGet() == 0) {
                      finishClosing.run();
                    }
                  },
                  Runnable::run));
    }
    return result;
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

  private void updateHealthStatusWithError(final Throwable failure) {
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

    if (!isExporterConfigured(exporterId)) {
      // The exporter is not configured (anymore) on this node, e.g. it was disabled or deleted.
      // Accepting its state would re-introduce a removed exporter into the runtime state, where its
      // stale position is never advanced and only removed again on the next restart via
      // clearExporterState(). Since getLowestPosition() is the minimum across all exporter states,
      // such a stale entry pins the snapshot/compaction position and prevents log compaction.
      LOG.trace(
          "Ignoring distributed state for exporter '{}' which is not configured anymore.",
          exporterId);
      return;
    }

    if (state.getPosition(exporterId) < exporterState.position()) {
      state.setExporterState(exporterId, exporterState.position(), exporterState.metadata());
      metrics.setLastUpdatedExportedPosition(exporterId, exporterState.position());
    }
  }

  private boolean isExporterConfigured(final String exporterId) {
    return containers.stream().anyMatch(container -> container.getId().equals(exporterId));
  }

  private void initContainers() throws Exception {
    for (final var entry : initialDescriptors.entrySet()) {
      final var pending = createContainer(entry.getKey(), entry.getValue());
      final ExporterContainer container = pending.container();
      container.initContainer(actor, metrics, state, exporterPhase);
      container.configureExporter();
      containers.add(container);
      pendingByExporterId.put(container.getId(), pending);
    }
  }

  private void recoverFromSnapshot() {
    state = new ExportersState(zeebeDb, zeebeDb.createContext());
    final long snapshotPosition = state.getLowestPosition();
    LOG.debug(
        "Recovered exporter '{}' from snapshot at lastExportedPosition {}",
        getName(),
        snapshotPosition);
  }

  private void onFailure() {
    isOpened.set(false);
    actor.close();
  }

  private void becomeIdle() {
    idle = true;
    LOG.debug("No exporters are configured. Going idle.");
    if (exporterDistributionService != null) {
      exporterDistributionService.close();
    }
    if (exporterDistributionTimer != null) {
      // closing the service do not stop the repeated timer task scheduled in this actor
      exporterDistributionTimer.cancel();
      exporterDistributionTimer = null;
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
    for (final ExporterContainer container : containers) {
      container.initMetadata();
    }

    if (!state.hasExporters()) {
      becomeIdle();
      return;
    }

    for (final ExporterContainer container : containers) {
      final var pending = pendingByExporterId.remove(container.getId());
      openWithRetryAndSchedule(container, pending);
    }

    exporterDistributionTimer =
        actor.runAtFixedRate(distributionInterval, this::distributeExporterState);
  }

  /**
   * Opens the given container's exporter, retrying indefinitely (with backoff) until it succeeds or
   * this exporter is disabled/removed while opening. Once open, schedules an {@link ExporterActor}
   * for it that reads and exports independently of every other exporter.
   */
  private void openWithRetryAndSchedule(
      final ExporterContainer container, final PendingContainer pending) {
    final var openFuture =
        new BackOffRetryStrategy(actor, Duration.ofSeconds(10), Duration.ofMillis(150))
            .runWithRetry(
                () -> {
                  try {
                    // If the exporter was disabled or removed concurrently, don't retry opening.
                    if (containers.contains(container)) {
                      container.openExporter();
                    } else {
                      LOG.debug(
                          "Exporter '{}' was disabled or removed before it could be opened.",
                          container.getId());
                    }
                    return true;
                  } catch (final Exception e) {
                    LOG.warn("Failed to open exporter '{}'. Retrying...", container.getId());
                    LOG.debug("Failed to open exporter '{}' => Stacktrace:", container.getId(), e);
                    return false;
                  }
                },
                this::isClosed);

    actor.runOnCompletion(
        openFuture,
        (ok, error) -> {
          if (error != null || isClosed() || !containers.contains(container)) {
            if (pending.reader() != null) {
              pending.reader().close();
            }
            return;
          }
          scheduleExporterActor(pending);
        });
  }

  private void restartActiveExportingMode() {
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
  public void removeFailureListener(final FailureListener failureListener) {
    actor.run(() -> listeners.remove(failureListener));
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

  /**
   * Bundles a freshly-created container together with the reader (if any, i.e. in {@link
   * ExporterMode#ACTIVE}) and the flag that tracks whether {@link ExporterActor} has begun its own
   * read loop. The reader/flag are handed off to the new {@link ExporterActor} by {@link
   * #scheduleExporterActor}, which then owns them for the rest of the exporter's lifetime.
   */
  private record PendingContainer(
      ExporterContainer container,
      @Nullable LogStreamReader reader,
      AtomicBoolean hasStartedExporting) {}
}
