/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import io.camunda.exporter.analytics.utils.SampledLogger;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue;
import java.time.Duration;
import java.util.EnumMap;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Exporter that ships Camunda process analytics to the Camunda Analytics backend. */
public class AnalyticsExporter implements Exporter {

  private static final Logger LOG =
      LoggerFactory.getLogger(AnalyticsExporter.class.getPackageName());

  private static final SampledLogger SAMPLED_LOG =
      new SampledLogger(LOG, Duration.ofMinutes(1).toMillis());

  private AnalyticsExporterConfig config;
  private Controller controller;
  private EnumMap<ValueType, Consumer<Record<?>>> handlers;

  public AnalyticsExporter() {}

  @Override
  public void configure(final Context context) {
    config = context.getConfiguration().instantiate(AnalyticsExporterConfig.class);

    config.validate();

    handlers = new EnumMap<>(ValueType.class);
    registerHandler(ValueType.PROCESS_INSTANCE_CREATION, this::handleProcessInstanceCreation);

    LOG.info(
        "Analytics exporter configured: enabled={}, endpoint={}",
        config.isEnabled(),
        config.getEndpoint());
  }

  @Override
  public void open(final Controller controller) {
    this.controller = controller;
    LOG.info("Analytics exporter opened");
  }

  @Override
  public void close() {
    LOG.info("Analytics exporter closed");
  }

  @Override
  public void export(final Record<?> record) {
    controller.updateLastExportedRecordPosition(record.getPosition());

    if (!config.isEnabled()) {
      return;
    }

    try {
      final var handler = handlers.get(record.getValueType());
      if (handler != null) {
        handler.accept(record);
      }
    } catch (final Exception e) {
      SAMPLED_LOG.warn(
          "Failed to handle record at position {}: {}", record.getPosition(), e.getMessage());
    }
  }

  private void handleProcessInstanceCreation(
      final Record<ProcessInstanceCreationRecordValue> record) {
    final var value = record.getValue();
    SAMPLED_LOG.info(
        "Process instance created: bpmnProcessId={}, processInstanceKey={}, position={}",
        value.getBpmnProcessId(),
        record.getKey(),
        record.getPosition());
  }

  @SuppressWarnings("unchecked")
  private <T extends RecordValue> void registerHandler(
      final ValueType valueType, final Consumer<Record<T>> handler) {
    handlers.put(
        valueType,
        record -> {
          if (record.getRecordType() == RecordType.EVENT) {
            handler.accept((Record<T>) record);
          }
        });
  }
}
