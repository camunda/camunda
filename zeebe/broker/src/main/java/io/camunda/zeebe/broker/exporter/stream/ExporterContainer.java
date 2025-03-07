/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.exporter.context.ExporterContext;
import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirector.ExporterInitializationInfo;
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
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.InstantSource;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

@SuppressWarnings("java:S112") // allow generic exception when calling Exporter#configure
public final class ExporterContainer implements Controller {

  private static final Logger LOG = Loggers.EXPORTER_LOGGER;

  private static final String SKIP_POSITION_UPDATE_ERROR_MESSAGE =
      "Failed to update exporter position when skipping filtered record, can be skipped, but may indicate an issue if it occurs often";

  private final ExporterContext context;
  private final Exporter exporter;
  private long position;
  private boolean exporterIsSoftPaused = false;
  private long lastUnacknowledgedPosition;
  private long lastAcknowledgedPosition;
  private byte[] lastExportedMetadata;
  private ExportersState exportersState;
  private ExporterMetrics metrics;
  private ActorControl actor;
  private final ExporterInitializationInfo initializationInfo;

  public ExporterContainer(
      final ExporterDescriptor descriptor,
      final int partitionId,
      final ExporterInitializationInfo initializationInfo,
      final MeterRegistry meterRegistry,
      final InstantSource clock) {
    this.initializationInfo = initializationInfo;
    context =
        new ExporterContext(
            Loggers.getExporterLogger(descriptor.getId()),
            descriptor.getConfiguration(),
            partitionId,
            meterRegistry,
            clock);

    exporter = descriptor.newInstance();
  }

  public void initContainer(
      final ActorControl actor,
      final ExporterMetrics metrics,
      final ExportersState state,
      final ExporterPhase phase) {
    this.actor = actor;
    this.metrics = metrics;
    exportersState = state;
    if (phase == ExporterPhase.SOFT_PAUSED) {
      softPauseExporter();
    }
  }

  private void initPosition() {
    position = exportersState.getPosition(getId());
    lastUnacknowledgedPosition = position;
    if (position == ExportersState.VALUE_NOT_FOUND) {
      exportersState.setPosition(getId(), -1L);
    }
  }

  private void initExporterState() {
    final var initializeFrom = initializationInfo.initializeFrom();
    if (initializeFrom != null) {
      final var metadata = exportersState.getExporterMetadata(initializeFrom);
      final var otherPosition = exportersState.getPosition(initializeFrom);
      exportersState.initializeExporterState(
          getId(), otherPosition, metadata, initializationInfo.metadataVersion());
    } else {
      // No need to initialize metadata, just set the version and initialize the position
      exportersState.initializeExporterState(
          getId(), -1L, null, initializationInfo.metadataVersion());
    }
  }

  void initMetadata() {
    final var curMetadata = exportersState.getMetadataVersion(getId());
    final var metadataVersion = initializationInfo.metadataVersion();

    if (metadataVersion > curMetadata) {
      initExporterState();
    }

    initPosition();
  }

  public void openExporter() {
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
   * Updates the exporter's position if it is up-to-date - that is, if it's last acknowledged
   * position is greater than or equal to its last unacknowledged position. This is safe to do when
   * skipping records as it means we passed no record to this exporter between both.
   *
   * @param eventPosition the new, up-to-date position
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
      lastAcknowledgedPosition = eventPosition;
      lastExportedMetadata = metadata;
      if (!exporterIsSoftPaused) {
        DirectBuffer metadataBuffer = null;
        if (metadata != null) {
          metadataBuffer = BufferUtil.wrapArray(metadata);
        }
        exportersState.setExporterState(getId(), eventPosition, metadataBuffer);
        metrics.setLastUpdatedExportedPosition(getId(), eventPosition);
        position = eventPosition;
      }
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
  public long getLastExportedRecordPosition() {
    return getPosition();
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

  public void configureExporter() throws Exception {
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

  void softPauseExporter() {
    exporterIsSoftPaused = true;
  }

  void undoSoftPauseExporter() {
    exporterIsSoftPaused = false;
    updateExporterState(lastAcknowledgedPosition, lastExportedMetadata);
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
    try {
      context.close();
    } catch (final Exception e) {
      context.getLogger().error("Error on context.close", e);
    }
  }
}
