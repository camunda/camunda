/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.exporter.stream;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.exporter.ExporterObjectMapper;
import io.zeebe.broker.exporter.context.ExporterContext;
import io.zeebe.broker.exporter.record.RecordMetadataImpl;
import io.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.zeebe.db.ZeebeDb;
import io.zeebe.engine.processor.AsyncSnapshotDirector;
import io.zeebe.engine.processor.EventFilter;
import io.zeebe.exporter.api.context.Controller;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.spi.Exporter;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.spi.SnapshotController;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.LangUtil;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.metrics.MetricsManager;
import io.zeebe.util.retry.AbortableRetryStrategy;
import io.zeebe.util.retry.EndlessRetryStrategy;
import io.zeebe.util.retry.RetryStrategy;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorCondition;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public class ExporterDirector extends Actor implements Service<ExporterDirector> {

  private static final String ERROR_MESSAGE_EXPORTING_ABORTED =
      "Expected to export record '{}' successfully, but exception was thrown.";
  private static final String ERROR_MESSAGE_RECOVER_FROM_SNAPSHOT_FAILED =
      "Expected to find event with the snapshot position %s in log stream, but nothing was found. Failed to recover '%s'.";

  private static final Logger LOG = Loggers.EXPORTER_LOGGER;
  private static final long NO_LAST_WRITTEN_EVENT_POSITION = -1L;
  private static final Consumer<Long> NO_DATA_REMOVER = pos -> {};

  private final RecordMetadata rawMetadata = new RecordMetadata();

  private ActorScheduler actorScheduler;
  private final AtomicBoolean isOpened = new AtomicBoolean(false);

  private final List<ExporterContainer> containers;
  private final int partitionId;

  private final LogStream logStream;
  private final LogStreamReader logStreamReader;
  private final RecordExporter recordExporter = new RecordExporter();

  private final SnapshotController snapshotController;
  private final String name;
  private final ExporterDirectorContext context;
  private final EventFilter eventFilter;
  private final RetryStrategy exportingRetryStrategy;
  private final RetryStrategy recordWrapStrategy;
  private ExportersState state;

  private ExporterMetrics metrics;
  private AsyncSnapshotDirector asyncSnapshotDirector;
  private ActorCondition onCommitPositionUpdatedCondition;
  private long lastExportedPosition;
  private boolean inExportingPhase;

  public ExporterDirector(ExporterDirectorContext context) {
    this.name = context.getName();
    this.context = context;

    this.containers =
        context.getDescriptors().stream().map(ExporterContainer::new).collect(Collectors.toList());

    this.logStream = context.getLogStream();
    this.partitionId = logStream.getPartitionId();
    this.logStreamReader = context.getLogStreamReader();
    this.eventFilter = context.getEventFilter();
    this.exportingRetryStrategy = new AbortableRetryStrategy(actor);
    this.recordWrapStrategy = new EndlessRetryStrategy(actor);

    this.snapshotController = context.getSnapshotController();
  }

  @Override
  public void start(ServiceStartContext startContext) {
    actorScheduler = startContext.getScheduler();
    startContext.async(actorScheduler.submitActor(this));
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    stopContext.async(actor.close());
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public ExporterDirector get() {
    return this;
  }

  @Override
  protected void onActorStarting() {
    final MetricsManager metricsManager = actorScheduler.getMetricsManager();
    metrics = new ExporterMetrics(metricsManager, getName(), Integer.toString(partitionId));

    this.logStreamReader.wrap(logStream);
  }

  @Override
  protected void onActorStarted() {
    try {
      LOG.info("Recovering exporter '{}' from snapshot", getName());
      recoverFromSnapshot();

      for (final ExporterContainer container : containers) {
        container.exporter.configure(container.context);
      }
    } catch (final Throwable e) {
      onFailure();
      LangUtil.rethrowUnchecked(e);
    }

    isOpened.set(true);
    onSnapshotRecovered();
  }

  private void recoverFromSnapshot() throws Exception {
    snapshotController.recover();
    final ZeebeDb zeebeDb = snapshotController.openDb();
    this.state = new ExportersState(zeebeDb, zeebeDb.createContext());

    final long snapshotPosition = getLowestExporterPosition();
    final boolean failedToRecoverReader = !logStreamReader.seekToNextEvent(snapshotPosition);
    if (failedToRecoverReader) {
      throw new IllegalStateException(
          String.format(ERROR_MESSAGE_RECOVER_FROM_SNAPSHOT_FAILED, snapshotPosition, getName()));
    }

    LOG.info(
        "Recovered exporter '{}' from snapshot at lastExportedPosition {}",
        getName(),
        snapshotPosition);
  }

  public long getLowestExporterPosition() {
    return state.getLowestPosition();
  }

  private void onFailure() {
    isOpened.set(false);
    actor.close();
  }

  private void onSnapshotRecovered() {
    installSnapshotting();

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
      container.exporter.open(container);
    }

    clearExporterState();

    actor.submit(this::readNextEvent);
  }

  private void installSnapshotting() {
    this.asyncSnapshotDirector =
        new AsyncSnapshotDirector(
            getName(),
            context.getSnapshotPeriod(),
            () -> actor.call(() -> lastExportedPosition),
            () -> CompletableActorFuture.completed(NO_LAST_WRITTEN_EVENT_POSITION),
            snapshotController,
            logStream::registerOnCommitPositionUpdatedCondition,
            logStream::removeOnCommitPositionUpdatedCondition,
            logStream::getCommitPosition,
            metrics.getSnapshotMetrics(),
            context.getMaxSnapshots(),
            NO_DATA_REMOVER);

    actorScheduler.submitActor(asyncSnapshotDirector);
  }

  private void skipRecord() {
    actor.submit(this::readNextEvent);
    metrics.incrementEventsSkippedCount();
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
              event.readMetadata(rawMetadata);
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
                  lastExportedPosition = event.getPosition();
                  metrics.incrementEventsExportedCount();
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

  @Override
  protected void onActorClosing() {
    metrics.close();

    actor.run(
        () -> {
          if (asyncSnapshotDirector != null) {
            actor.runOnCompletionBlockingCurrentPhase(
                asyncSnapshotDirector.enforceSnapshotCreation(
                    NO_LAST_WRITTEN_EVENT_POSITION, lastExportedPosition),
                (v, ex) -> {
                  try {
                    asyncSnapshotDirector.close();
                    snapshotController.close();
                  } catch (Exception e) {
                    LOG.error("Error on closing snapshotController.", e);
                  }
                });
          } else {
            try {
              snapshotController.close();
            } catch (Exception e) {
              LOG.error("Error on closing snapshotController.", e);
            }
          }
        });

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

  public boolean isClosed() {
    return !isOpened.get();
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
  }

  private class RecordExporter {
    private final ExporterObjectMapper objectMapper = new ExporterObjectMapper();
    private final ExporterRecordMapper recordMapper = new ExporterRecordMapper(objectMapper);
    private Record record;
    private boolean shouldExecuteSideEffects;
    private int exporterIndex;

    void wrap(LoggedEvent rawEvent) {
      final RecordMetadataImpl metadata =
          new RecordMetadataImpl(
              objectMapper,
              partitionId,
              rawMetadata.getIntent(),
              rawMetadata.getRecordType(),
              rawMetadata.getRejectionType(),
              BufferUtil.bufferAsString(rawMetadata.getRejectionReason()),
              rawMetadata.getValueType());

      record = recordMapper.map(rawEvent, metadata);
      exporterIndex = 0;
      shouldExecuteSideEffects = record != null;
    }

    public boolean export() {
      if (!shouldExecuteSideEffects) {
        return true;
      }

      final int exportersCount = containers.size();

      // current error handling strategy is simply to repeat forever until the record can be
      // successfully exported.
      while (exporterIndex < exportersCount) {
        final ExporterContainer container = containers.get(exporterIndex);

        try {
          if (container.position < record.getPosition()) {
            container.exporter.export(record);
          }

          exporterIndex++;
        } catch (final Exception ex) {
          container.context.getLogger().error("Error exporting record {}", record, ex);
          return false;
        }
      }

      return true;
    }
  }
}
