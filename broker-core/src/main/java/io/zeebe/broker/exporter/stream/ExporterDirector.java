/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.exporter.stream;

import static io.zeebe.engine.processor.TypedEventRegistry.EVENT_REGISTRY;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.exporter.context.ExporterContext;
import io.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.zeebe.db.ZeebeDb;
import io.zeebe.engine.processor.CopiedRecords;
import io.zeebe.engine.processor.EventFilter;
import io.zeebe.exporter.api.Exporter;
import io.zeebe.exporter.api.context.Context;
import io.zeebe.exporter.api.context.Controller;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.LangUtil;
import io.zeebe.util.retry.BackOffRetryStrategy;
import io.zeebe.util.retry.EndlessRetryStrategy;
import io.zeebe.util.retry.RetryStrategy;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorCondition;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.SchedulingHints;
import io.zeebe.util.sched.future.ActorFuture;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public class ExporterDirector extends Actor implements Service<ExporterDirector> {

  private static final String ERROR_MESSAGE_EXPORTING_ABORTED =
      "Expected to export record '{}' successfully, but exception was thrown.";
  private static final String ERROR_MESSAGE_RECOVER_FROM_SNAPSHOT_FAILED =
      "Expected to find event with the snapshot position %s in log stream, but nothing was found. Failed to recover '%s'.";

  private static final Logger LOG = Loggers.EXPORTER_LOGGER;
  private final AtomicBoolean isOpened = new AtomicBoolean(false);
  private final List<ExporterContainer> containers;
  private final int partitionId;
  private final LogStream logStream;
  private final LogStreamReader logStreamReader;
  private final RecordExporter recordExporter;
  private final ZeebeDb zeebeDb;
  private final ExporterMetrics metrics;
  private final String name;
  private final RetryStrategy exportingRetryStrategy;
  private final RetryStrategy recordWrapStrategy;
  private ActorScheduler actorScheduler;
  private EventFilter eventFilter;
  private ExportersState state;

  private ActorCondition onCommitPositionUpdatedCondition;
  private boolean inExportingPhase;

  public ExporterDirector(ExporterDirectorContext context) {
    this.name = context.getName();
    this.containers =
        context.getDescriptors().stream().map(ExporterContainer::new).collect(Collectors.toList());

    this.logStream = context.getLogStream();
    this.partitionId = logStream.getPartitionId();
    this.recordExporter = new RecordExporter(containers, partitionId);
    this.logStreamReader = context.getLogStreamReader();
    this.exportingRetryStrategy = new BackOffRetryStrategy(actor, Duration.ofSeconds(10));
    this.recordWrapStrategy = new EndlessRetryStrategy(actor);

    this.zeebeDb = context.getZeebeDb();

    this.metrics = new ExporterMetrics(partitionId);
  }

  @Override
  public void start(ServiceStartContext startContext) {
    actorScheduler = startContext.getScheduler();
    startContext.async(actorScheduler.submitActor(this, SchedulingHints.ioBound()));
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    stopContext.async(actor.close());
  }

  @Override
  public ExporterDirector get() {
    return this;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  protected void onActorStarting() {
    this.logStreamReader.wrap(logStream);
  }

  @Override
  protected void onActorStarted() {
    try {
      LOG.debug("Recovering exporter from snapshot");
      recoverFromSnapshot();

      for (final ExporterContainer container : containers) {
        LOG.debug("Configure exporter with id '{}'", container.getId());
        container.exporter.configure(container.context);
      }

      eventFilter = createEventFilter(containers);
      LOG.debug("Set event filter for exporters: {}", eventFilter);

    } catch (final Throwable e) {
      onFailure();
      LangUtil.rethrowUnchecked(e);
    }

    isOpened.set(true);
    onSnapshotRecovered();
  }

  @Override
  protected void onActorClosing() {
    logStreamReader.close();
    if (onCommitPositionUpdatedCondition != null) {
      logStream.removeOnCommitPositionUpdatedCondition(onCommitPositionUpdatedCondition);
      onCommitPositionUpdatedCondition = null;
    }
  }

  @Override
  protected void onActorClosed() {
    LOG.debug("Closed exporter director '{}'.", getName());
  }

  @Override
  protected void onActorCloseRequested() {
    isOpened.set(false);
    for (final ExporterContainer container : containers) {
      try {
        container.exporter.close();
      } catch (final Exception e) {
        container.context.getLogger().error("Error on close", e);
      }
    }
  }

  private void recoverFromSnapshot() {
    this.state = new ExportersState(zeebeDb, zeebeDb.createContext());

    final long snapshotPosition = getLowestExporterPosition();
    final boolean failedToRecoverReader = !logStreamReader.seekToNextEvent(snapshotPosition);
    if (failedToRecoverReader) {
      throw new IllegalStateException(
          String.format(ERROR_MESSAGE_RECOVER_FROM_SNAPSHOT_FAILED, snapshotPosition, getName()));
    }

    LOG.debug(
        "Recovered exporter '{}' from snapshot at lastExportedPosition {}",
        getName(),
        snapshotPosition);
  }

  public long getLowestExporterPosition() {
    return state.getLowestPosition();
  }

  private ExporterEventFilter createEventFilter(List<ExporterContainer> containers) {

    final List<Context.RecordFilter> recordFilters =
        containers.stream().map(c -> c.context.getFilter()).collect(Collectors.toList());

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

  private void onSnapshotRecovered() {
    onCommitPositionUpdatedCondition =
        actor.onCondition(
            getName() + "-on-commit-lastExportedPosition-updated", this::readNextEvent);
    logStream.registerOnCommitPositionUpdatedCondition(onCommitPositionUpdatedCondition);

    // start reading
    for (final ExporterContainer container : containers) {
      container.position = state.getPosition(container.getId());
      if (container.position == ExportersState.VALUE_NOT_FOUND) {
        state.setPosition(container.getId(), -1L);
      }
      LOG.debug("Open exporter with id '{}'", container.getId());
      container.exporter.open(container);
    }

    clearExporterState();

    actor.submit(this::readNextEvent);
  }

  private void skipRecord() {
    metrics.eventSkipped();
    actor.submit(this::readNextEvent);
  }

  private void readNextEvent() {
    if (isOpened.get() && logStreamReader.hasNext() && !inExportingPhase) {
      final LoggedEvent currentEvent = logStreamReader.next();
      if (eventFilter == null || eventFilter.applies(currentEvent)) {
        inExportingPhase = true;
        exportEvent(currentEvent);
      } else {
        skipRecord();
      }
    }
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
                  metrics.eventExported();
                  inExportingPhase = false;
                  actor.submit(this::readNextEvent);
                }
              });
        });
  }

  public ExportersState getState() {
    return state;
  }

  private void clearExporterState() {
    final List<String> exporterIds =
        containers.stream().map(ExporterContainer::getId).collect(Collectors.toList());

    state.visitPositions(
        (exporterId, position) -> {
          if (!exporterIds.contains(exporterId)) {
            state.removePosition(exporterId);
            LOG.info(
                "The exporter '{}' is not configured anymore. Its lastExportedPosition is removed from the state.",
                exporterId);
          }
        });
  }

  public boolean isClosed() {
    return !isOpened.get();
  }

  private static class RecordExporter {

    private final RecordMetadata rawMetadata = new RecordMetadata();
    private final List<ExporterContainer> containers;
    private final int partitionId;

    private Record record;
    private boolean shouldExport;
    private int exporterIndex;

    RecordExporter(List<ExporterContainer> containers, int partitionId) {
      this.containers = containers;
      this.partitionId = partitionId;
    }

    void wrap(LoggedEvent rawEvent) {
      rawEvent.readMetadata(rawMetadata);

      shouldExport = EVENT_REGISTRY.containsKey(rawMetadata.getValueType());
      if (shouldExport) {
        record = CopiedRecords.createCopiedRecord(partitionId, rawEvent);
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

        try {
          if (container.position < record.getPosition() && container.acceptRecord(rawMetadata)) {
            container.exporter.export(record);
          }

          exporterIndex++;
        } catch (final Exception ex) {
          container
              .context
              .getLogger()
              .error("Error on exporting record with key {}", record.getKey(), ex);
          return false;
        }
      }

      return true;
    }
  }

  private static class ExporterEventFilter implements EventFilter {

    private final RecordMetadata metadata = new RecordMetadata();
    private final Map<RecordType, Boolean> acceptRecordTypes;
    private final Map<ValueType, Boolean> acceptValueTypes;

    ExporterEventFilter(
        Map<RecordType, Boolean> acceptRecordTypes, Map<ValueType, Boolean> acceptValueTypes) {
      this.acceptRecordTypes = acceptRecordTypes;
      this.acceptValueTypes = acceptValueTypes;
    }

    @Override
    public boolean applies(LoggedEvent event) {
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

  private class ExporterContainer implements Controller {
    private final ExporterContext context;
    private final Exporter exporter;
    private long position;

    ExporterContainer(ExporterDescriptor descriptor) {
      context =
          new ExporterContext(
              Loggers.getExporterLogger(descriptor.getId()), descriptor.getConfiguration());

      exporter = descriptor.newInstance();
    }

    @Override
    public void updateLastExportedRecordPosition(final long position) {
      actor.run(
          () -> {
            state.setPosition(getId(), position);
            this.position = position;
          });
    }

    @Override
    public void scheduleTask(final Duration delay, final Runnable task) {
      actor.runDelayed(delay, task);
    }

    private String getId() {
      return context.getConfiguration().getId();
    }

    private boolean acceptRecord(RecordMetadata metadata) {
      final Context.RecordFilter filter = context.getFilter();
      return filter.acceptType(metadata.getRecordType())
          && filter.acceptValue(metadata.getValueType());
    }
  }
}
