/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.domain.ExporterPositionModel;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import org.apache.commons.lang3.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * https://docs.camunda.io/docs/next/components/zeebe/technical-concepts/process-lifecycles/
 */
public class RdbmsExporter implements Exporter {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsExporter.class);

  private final HashMap<ValueType, RdbmsExportHandler> registeredHandlers = new HashMap<>();

  private Controller controller;

  private long partitionId;
  private final RdbmsService rdbmsService;

  private ExporterPositionModel exporterRdbmsPosition;
  private long lastPosition = -1;

  public RdbmsExporter(final RdbmsService rdbmsService) {
    this.rdbmsService = rdbmsService;
  }

  @Override
  public void configure(final Context context) {
    partitionId = context.getPartitionId();

          registerHandler();


    LOG.info("[RDBMS Exporter] RDBMS Exporter configured!");
  }

  @Override
  public void open(final Controller controller) {
    this.controller = controller;

    initializeRdbmsPosition();
    lastPosition = controller.getLastExportedRecordPosition();
    if (exporterRdbmsPosition.lastExportedPosition() > -1 && lastPosition <= exporterRdbmsPosition.lastExportedPosition()) {
      // This is needed since the brokers last exported position is from it's last snapshot and kann be different than ours.
      lastPosition = exporterRdbmsPosition.lastExportedPosition();
      updatePositionInBroker();
    }

    rdbmsService.executionQueue().registerPreFlushListener(this::updatePositionInRdbms);
    rdbmsService.executionQueue().registerPostFlushListener(this::updatePositionInBroker);
    LOG.info("[RDBMS Exporter] Exporter opened with last exported position {}", lastPosition);
  }

  @Override
  public void close() {
    try {
      rdbmsService.executionQueue().flush();
    } catch (final Exception e) {
      LOG.warn("[RDBMS Exporter] Failed to flush records before closing exporter.", e);
    }

    LOG.info("[RDBMS Exporter] Exporter closed");
  }

  @Override
  public void export(final Record<?> record) {
    LOG.debug("[RDBMS Exporter] Process record {}-{} - {}:{}", record.getPartitionId(),
        record.getPosition(),
        record.getValueType(),
        record.getIntent());

    if (registeredHandlers.containsKey(record.getValueType())) {
      final var handler = registeredHandlers.get(record.getValueType());
      if (handler.canExport(record)) {
        LOG.debug("[RDBMS Exporter] Exporting record {} with handler {}", record.getValue(),
            handler.getClass());
        handler.export(record);
      } else {
        LOG.trace("[RDBMS Exporter] Handler {} can not export record {}", handler.getClass(),
            record.getValueType());
      }
    } else {
      LOG.trace("[RDBMS Exporter] No registered handler found for {}", record.getValueType());
    }

    lastPosition = record.getPosition();
  }

  private void registerHandler() {
    registeredHandlers.put(ValueType.PROCESS, new ProcessExportHandler(rdbmsService.getProcessDeploymentRdbmsService()));
    registeredHandlers.put(ValueType.PROCESS_INSTANCE, new ProcessInstanceExportHandler(rdbmsService.getProcessInstanceRdbmsService()));
    registeredHandlers.put(ValueType.VARIABLE, new VariableExportHandler(rdbmsService.getVariableRdbmsService()));
  }

  private void updatePositionInBroker() {
    LOG.debug("[RDBMS Exporter] Updating position to {} in broker", lastPosition);
    controller.updateLastExportedRecordPosition(lastPosition);
  }

  private void updatePositionInRdbms() {
    if (lastPosition > exporterRdbmsPosition.lastExportedPosition()) {
      LOG.debug("[RDBMS Exporter] Updating position to {} in rdbms", lastPosition);
      exporterRdbmsPosition = new ExporterPositionModel(
          exporterRdbmsPosition.partitionId(),
          exporterRdbmsPosition.exporter(),
          lastPosition,
          exporterRdbmsPosition.created(),
          LocalDateTime.now()
      );
      rdbmsService.getExporterPositionRdbmsService().update(
          exporterRdbmsPosition
      );
    }
  }

  private void initializeRdbmsPosition() {

    // TODO ... DIRTY we need to find a way that exports get opened AFTER the application is ready
    while (true) {
      try {
        exporterRdbmsPosition = rdbmsService.getExporterPositionRdbmsService().findOne(partitionId);
        break;
      } catch (final Exception e) {
        LOG.warn("[RDBMS Exporter] Failed to initialize exporter position because Database is not ready, retrying ... {}", e.getMessage());
        ThreadUtils.sleepQuietly(Duration.ofMillis(1000));
      }
    }

    if (exporterRdbmsPosition == null) {
      exporterRdbmsPosition = new ExporterPositionModel(
          partitionId,
          getClass().getSimpleName(),
          lastPosition,
          LocalDateTime.now(),
          LocalDateTime.now()
      );
      rdbmsService.getExporterPositionRdbmsService().createWithoutQueue(exporterRdbmsPosition);
      LOG.debug("[RDBMS Exporter] Initialize position in rdbms");
    } else {
      LOG.debug("[RDBMS Exporter] Found position in rdbms for this exporter: {}", exporterRdbmsPosition);
    }
  }
}
