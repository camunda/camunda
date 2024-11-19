/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.ExporterPositionModel;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.VisibleForTesting;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** https://docs.camunda.io/docs/next/components/zeebe/technical-concepts/process-lifecycles/ */
public class RdbmsExporter implements Exporter {

  /** The partition on which all process deployments are published */
  public static final long PROCESS_DEFINITION_PARTITION = 1L;

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsExporter.class);

  private final HashMap<ValueType, List<RdbmsExportHandler>> registeredHandlers = new HashMap<>();

  private Controller controller;

  private long partitionId;
  private final RdbmsService rdbmsService;
  private RdbmsWriter rdbmsWriter;

  private ExporterPositionModel exporterRdbmsPosition;
  private long lastPosition = -1;

  public RdbmsExporter(final RdbmsService rdbmsService) {
    this.rdbmsService = rdbmsService;
  }

  @Override
  public void configure(final Context context) {
    partitionId = context.getPartitionId();

    rdbmsWriter = rdbmsService.createWriter(partitionId);
    registerHandler();

    LOG.info("[RDBMS Exporter] RDBMS Exporter configured!");
  }

  @Override
  public void open(final Controller controller) {
    this.controller = controller;
    this.controller.scheduleCancellableTask(Duration.ofSeconds(5), this::flushAndReschedule);

    initializeRdbmsPosition();
    lastPosition = controller.getLastExportedRecordPosition();
    if (exporterRdbmsPosition.lastExportedPosition() > -1
        && lastPosition <= exporterRdbmsPosition.lastExportedPosition()) {
      // This is needed since the brokers last exported position is from it's last snapshot and kann
      // be different than ours.
      lastPosition = exporterRdbmsPosition.lastExportedPosition();
      updatePositionInBroker();
    }

    rdbmsWriter.getExecutionQueue().registerPreFlushListener(this::updatePositionInRdbms);
    rdbmsWriter.getExecutionQueue().registerPostFlushListener(this::updatePositionInBroker);
    LOG.info("[RDBMS Exporter] Exporter opened with last exported position {}", lastPosition);
  }

  @Override
  public void close() {
    try {
      rdbmsWriter.flush();
    } catch (final Exception e) {
      LOG.warn("[RDBMS Exporter] Failed to flush records before closing exporter.", e);
    }

    LOG.info("[RDBMS Exporter] Exporter closed");
  }

  @Override
  public void export(final Record<?> record) {
    LOG.debug(
        "[RDBMS Exporter] Process record {}-{} - {}:{}",
        record.getPartitionId(),
        record.getPosition(),
        record.getValueType(),
        record.getIntent());

    if (registeredHandlers.containsKey(record.getValueType())) {
      for (final var handler : registeredHandlers.get(record.getValueType())) {
        if (handler.canExport(record)) {
          LOG.debug(
              "[RDBMS Exporter] Exporting record {} with handler {}",
              record.getValue(),
              handler.getClass());
          handler.export(record);
        } else {
          LOG.trace(
              "[RDBMS Exporter] Handler {} can not export record {}",
              handler.getClass(),
              record.getValueType());
        }
      }
    } else {
      LOG.trace("[RDBMS Exporter] No registered handler found for {}", record.getValueType());
    }

    lastPosition = record.getPosition();
  }

  private void registerHandler() {
    if (partitionId == PROCESS_DEFINITION_PARTITION) {
      registeredHandlers.put(
          ValueType.PROCESS,
          List.of(new ProcessExportHandler(rdbmsWriter.getProcessDefinitionWriter())));
    }
    registeredHandlers.put(
        ValueType.PROCESS_INSTANCE,
        List.of(
            new ProcessInstanceExportHandler(rdbmsWriter.getProcessInstanceWriter()),
            new FlowNodeExportHandler(rdbmsWriter.getFlowNodeInstanceWriter())));
    registeredHandlers.put(
        ValueType.VARIABLE, List.of(new VariableExportHandler(rdbmsWriter.getVariableWriter())));
    registeredHandlers.put(
        ValueType.USER_TASK, List.of(new UserTaskExportHandler(rdbmsWriter.getUserTaskWriter())));
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

  private void flushAndReschedule() {
    flushExecutionQueue();
    controller.scheduleCancellableTask(Duration.ofSeconds(5), this::flushAndReschedule);
  }

  @VisibleForTesting(
      "Each exporter creates it's own executionQueue, so we need an accessible flush method for tests")
  public void flushExecutionQueue() {
    LOG.debug("[RDBMS Exporter] flushing queue");
    rdbmsWriter.flush();
  }
}
