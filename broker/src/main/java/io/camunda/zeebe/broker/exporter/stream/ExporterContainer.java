/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.exporter.stream;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.exporter.context.ExporterContext;
import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.exporter.api.context.ScheduledTask;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.jar.ThreadContextUtil;
import java.time.Duration;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

@SuppressWarnings("java:S112") // allow generic exception when calling Exporter#configure
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
    ThreadContextUtil.runWithClassLoader(
        () -> exporter.open(this), exporter.getClass().getClassLoader());
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
        updateExporterState(eventPosition);
      } catch (final Exception e) {
        LOG.warn(SKIP_POSITION_UPDATE_ERROR_MESSAGE, e);
      }
    }
  }

  private void updateExporterState(final long eventPosition) {
    updateExporterState(eventPosition, null);
  }

  private void updateExporterState(final long eventPosition, final byte[] metadata) {
    if (position < eventPosition) {

      DirectBuffer metadataBuffer = null;
      if (metadata != null) {
        metadataBuffer = BufferUtil.wrapArray(metadata);
      }
      exportersState.setExporterState(getId(), eventPosition, metadataBuffer);

      metrics.setLastUpdatedExportedPosition(getId(), eventPosition);
      position = eventPosition;
    }
  }

  @Override
  public void updateLastExportedRecordPosition(final long position) {
    actor.run(() -> updateExporterState(position));
  }

  @Override
  public void updateLastExportedRecordPosition(final long position, final byte[] metadata) {
    actor.run(() -> updateExporterState(position, metadata));
  }

  @Override
  public ScheduledTask scheduleCancellableTask(final Duration delay, final Runnable task) {
    final var scheduledTimer = actor.schedule(delay, task);
    return scheduledTimer::cancel;
  }

  @Override
  public Optional<byte[]> readMetadata() {
    return Optional.ofNullable(exportersState.getExporterMetadata(getId()))
        .filter(metadata -> metadata.capacity() > 0)
        .map(BufferUtil::bufferAsArray);
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
    ThreadContextUtil.runCheckedWithClassLoader(
        () -> exporter.configure(context), exporter.getClass().getClassLoader());
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
    ThreadContextUtil.runWithClassLoader(
        () -> exporter.export(record), exporter.getClass().getClassLoader());
    lastUnacknowledgedPosition = record.getPosition();
  }

  public void close() {
    try {
      ThreadContextUtil.runCheckedWithClassLoader(
          exporter::close, exporter.getClass().getClassLoader());
    } catch (final Exception e) {
      context.getLogger().error("Error on close", e);
    }
  }
}
