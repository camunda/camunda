/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.exporter.stream;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.exporter.context.ExporterContext;
import io.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.exporter.api.Exporter;
import io.zeebe.exporter.api.context.Context;
import io.zeebe.exporter.api.context.Controller;
import io.zeebe.exporter.api.context.ScheduledTask;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.record.Record;
import io.zeebe.util.sched.ActorControl;
import java.time.Duration;
import org.slf4j.Logger;

final class ExporterContainer implements Controller {

  private static final Logger LOG = Loggers.EXPORTER_LOGGER;

  private static final String SKIP_POSITION_UPDATE_ERROR_MESSAGE =
      "Failed to update exporter position when skipping filtered record, can be skipped, but may indicate an issue if it occurs often";

  private final ExporterContext context;
  private final Exporter exporter;
  private long position;
  private long lastUnacknowledgedPosition;
  private ExportersState exportersState;
  private ExporterMetrics metrics;
  private ActorControl actor;

  ExporterContainer(final ExporterDescriptor descriptor) {
    context =
        new ExporterContext(
            Loggers.getExporterLogger(descriptor.getId()), descriptor.getConfiguration());

    exporter = descriptor.newInstance();
  }

  void initContainer(
      final ActorControl actor, final ExporterMetrics metrics, final ExportersState state) {
    this.actor = actor;
    this.metrics = metrics;
    exportersState = state;
  }

  void initPosition() {
    position = exportersState.getPosition(getId());
    lastUnacknowledgedPosition = position;
    if (position == ExportersState.VALUE_NOT_FOUND) {
      exportersState.setPosition(getId(), -1L);
    }
  }

  void openExporter() {
    LOG.debug("Open exporter with id '{}'", getId());
    exporter.open(this);
  }

  public ExporterContext getContext() {
    return context;
  }

  public Exporter getExporter() {
    return exporter;
  }

  public long getPosition() {
    return position;
  }

  long getLastUnacknowledgedPosition() {
    return lastUnacknowledgedPosition;
  }

  /**
   * Updates the exporter's position if it is up to date - that is, if it's last acknowledged
   * position is greater than or equal to its last unacknowledged position. This is safe to do when
   * skipping records as it means we passed no record to this exporter between both.
   *
   * @param eventPosition the new, up to date position
   */
  void updatePositionOnSkipIfUpToDate(final long eventPosition) {
    if (position >= lastUnacknowledgedPosition && position < eventPosition) {
      try {
        updateExporterLastExportedRecordPosition(eventPosition);
      } catch (final Exception e) {
        LOG.warn(SKIP_POSITION_UPDATE_ERROR_MESSAGE, e);
      }
    }
  }

  private void updateExporterLastExportedRecordPosition(final long eventPosition) {
    if (position < eventPosition) {
      exportersState.setPosition(getId(), eventPosition);
      metrics.setLastUpdatedExportedPosition(getId(), eventPosition);
      position = eventPosition;
    }
  }

  @Override
  public void updateLastExportedRecordPosition(final long position) {
    actor.run(() -> updateExporterLastExportedRecordPosition(position));
  }

  @Override
  public ScheduledTask scheduleCancellableTask(final Duration delay, final Runnable task) {
    final var scheduledTimer = actor.runDelayed(delay, task);
    return scheduledTimer::cancel;
  }

  public String getId() {
    return context.getConfiguration().getId();
  }

  private boolean acceptRecord(final RecordMetadata metadata) {
    final Context.RecordFilter filter = context.getFilter();
    return filter.acceptType(metadata.getRecordType())
        && filter.acceptValue(metadata.getValueType());
  }

  void configureExporter() throws Exception {
    LOG.debug("Configure exporter with id '{}'", getId());
    exporter.configure(context);
  }

  boolean exportRecord(final RecordMetadata rawMetadata, final TypedRecord typedEvent) {
    try {
      if (position < typedEvent.getPosition()) {
        if (acceptRecord(rawMetadata)) {
          export(typedEvent);
        } else {
          updatePositionOnSkipIfUpToDate(typedEvent.getPosition());
        }
      }
      return true;
    } catch (final Exception ex) {
      context.getLogger().warn("Error on exporting record with key {}", typedEvent.getKey(), ex);
      return false;
    }
  }

  private void export(final Record<?> record) {
    exporter.export(record);
    lastUnacknowledgedPosition = record.getPosition();
  }

  public void close() {
    try {
      exporter.close();
    } catch (final Exception e) {
      context.getLogger().error("Error on close", e);
    }
  }
}
