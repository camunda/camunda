/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms;

import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.ExporterPositionModel;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.exporter.api.context.ScheduledTask;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.VisibleForTesting;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** https://docs.camunda.io/docs/next/components/zeebe/technical-concepts/process-lifecycles/ */
public class RdbmsExporter {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsExporter.class);

  private final Map<ValueType, List<RdbmsExportHandler>> registeredHandlers;
  private Controller controller;

  private final int partitionId;
  private final RdbmsWriter rdbmsWriter;

  // configuration
  private final Duration flushInterval;
  private final int maxQueueSize;

  // volatile runtime properties
  private ExporterPositionModel exporterRdbmsPosition;
  private long lastPosition = -1;
  private ScheduledTask currentFlushTask = null;
  private ScheduledTask currentCleanupTask = null;

  public RdbmsExporter(final RdbmsExporterConfig config) {
    rdbmsWriter = config.rdbmsWriter();
    registeredHandlers = config.handlers();

    partitionId = config.partitionId();
    flushInterval = config.flushInterval();
    maxQueueSize = config.maxQueueSize();

    LOG.info(
        "[RDBMS Exporter] RdbmsExporter created with Configuration: flushInterval={}, maxQueueSize={}",
        flushInterval,
        maxQueueSize);
  }

  public void open(final Controller controller) {
    this.controller = controller;

    if (!flushAfterEachRecord()) {
      currentFlushTask =
          controller.scheduleCancellableTask(flushInterval, this::flushAndReschedule);
    }

    initializeRdbmsPosition();
    lastPosition = controller.getLastExportedRecordPosition();
    if (exporterRdbmsPosition.lastExportedPosition() > -1
        && lastPosition <= exporterRdbmsPosition.lastExportedPosition()) {
      // This is needed since the brokers last exported position is from its last snapshot and can
      // be different from ours.
      lastPosition = exporterRdbmsPosition.lastExportedPosition();
      updatePositionInBroker();
    }

    rdbmsWriter.getExecutionQueue().registerPreFlushListener(this::updatePositionInRdbms);
    rdbmsWriter.getExecutionQueue().registerPostFlushListener(this::updatePositionInBroker);

    // schedule first cleanup in 1 second. Future intervals are given by the history cleanup service
    // itself
    currentCleanupTask =
        controller.scheduleCancellableTask(Duration.ofSeconds(1), this::cleanupHistory);

    LOG.info("[RDBMS Exporter] Exporter opened with last exported position {}", lastPosition);
  }

  public void close() {
    try {
      if (currentFlushTask != null) {
        currentFlushTask.cancel();
      }
      if (currentCleanupTask != null) {
        currentCleanupTask.cancel();
      }

      rdbmsWriter.flush();
    } catch (final Exception e) {
      LOG.warn("[RDBMS Exporter] Failed to flush records before closing exporter.", e);
    }

    LOG.info("[RDBMS Exporter] Exporter closed");
  }

  public void export(final Record<?> record) {
    LOG.trace(
        "[RDBMS Exporter] Process record {}-{} - {}:{}",
        record.getPartitionId(),
        record.getPosition(),
        record.getValueType(),
        record.getIntent());

    boolean exported = false;
    if (registeredHandlers.containsKey(record.getValueType())) {
      for (final var handler : registeredHandlers.get(record.getValueType())) {
        if (handler.canExport(record)) {
          LOG.debug(
              "[RDBMS Exporter] Exporting record {} with handler {}",
              record.getValue(),
              handler.getClass());
          handler.export(record);
          exported = true;
        } else {
          LOG.trace(
              "[RDBMS Exporter] Handler {} can not export record {}",
              handler.getClass(),
              record.getValueType());
        }

        lastPosition = record.getPosition();

        if (flushAfterEachRecord()) {
          rdbmsWriter.flush();
        }
      }
    } else {
      LOG.trace("[RDBMS Exporter] No registered handler found for {}", record.getValueType());
    }

    if (!exported) {
      LOG.trace("[RDBMS Exporter] Record with key {} and original partitionId {} could not be exported {}.",
          record.getKey(),
          Protocol.decodePartitionId(record.getKey()),
          record);
    }
  }

  public void purge() {
    if (currentFlushTask != null) {
      currentFlushTask.cancel();
    }
    if (currentCleanupTask != null) {
      currentCleanupTask.cancel();
    }

    rdbmsWriter.getRdbmsPurger().purgeRdbms();
  }

  private void updatePositionInBroker() {
    LOG.debug("[RDBMS Exporter] Updating position to {} in broker", lastPosition);
    controller.updateLastExportedRecordPosition(lastPosition);
  }

  private void updatePositionInRdbms() {
    if (lastPosition > exporterRdbmsPosition.lastExportedPosition()) {
      LOG.debug("[RDBMS Exporter] Updating position to {} in rdbms", lastPosition);
      exporterRdbmsPosition =
          new ExporterPositionModel(
              exporterRdbmsPosition.partitionId(),
              exporterRdbmsPosition.exporter(),
              lastPosition,
              exporterRdbmsPosition.created(),
              LocalDateTime.now());
      rdbmsWriter.getExporterPositionService().update(exporterRdbmsPosition);
    }
  }

  private void initializeRdbmsPosition() {
    try {
      exporterRdbmsPosition = rdbmsWriter.getExporterPositionService().findOne(partitionId);
    } catch (final Exception e) {
      LOG.warn(
          "[RDBMS Exporter] Failed to initialize exporter position because Database is not ready, retrying ... {}",
          e.getMessage());
      throw e;
    }

    if (exporterRdbmsPosition == null) {
      exporterRdbmsPosition =
          new ExporterPositionModel(
              partitionId,
              getClass().getSimpleName(),
              lastPosition,
              LocalDateTime.now(),
              LocalDateTime.now());
      rdbmsWriter.getExporterPositionService().createWithoutQueue(exporterRdbmsPosition);
      LOG.debug("[RDBMS Exporter] Initialize position in rdbms");
    } else {
      LOG.debug(
          "[RDBMS Exporter] Found position in rdbms for this exporter: {}", exporterRdbmsPosition);
    }
  }

  private boolean flushAfterEachRecord() {
    return flushInterval.isZero() || maxQueueSize <= 0;
  }

  private void flushAndReschedule() {
    flushExecutionQueue();
    currentFlushTask = controller.scheduleCancellableTask(flushInterval, this::flushAndReschedule);
  }

  private void cleanupHistory() {
    final var newDuration =
        rdbmsWriter.getHistoryCleanupService().cleanupHistory(partitionId, OffsetDateTime.now());
    currentCleanupTask = controller.scheduleCancellableTask(newDuration, this::cleanupHistory);
  }

  @VisibleForTesting(
      "Each exporter creates it's own executionQueue, so we need an accessible flush method for tests")
  public void flushExecutionQueue() {
    if (flushAfterEachRecord()) {
      LOG.warn("Unnecessary flush called, since flush interval is zero or max queue size is zero");
      return;
    }
    rdbmsWriter.flush();
  }
}
