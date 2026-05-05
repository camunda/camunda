/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import static io.camunda.exporter.analytics.AnalyticsAttributes.BPMN_PROCESS_ID;
import static io.camunda.exporter.analytics.AnalyticsAttributes.PROCESS_DEFINITION_KEY;
import static io.camunda.exporter.analytics.AnalyticsAttributes.PROCESS_INSTANCE_KEY;
import static io.camunda.exporter.analytics.AnalyticsAttributes.PROCESS_VERSION;
import static io.camunda.exporter.analytics.AnalyticsAttributes.ROOT_PROCESS_INSTANCE_KEY;
import static io.camunda.exporter.analytics.AnalyticsAttributes.TENANT_ID;

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
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Exporter that ships Camunda process analytics to the Camunda Analytics backend. */
public class AnalyticsExporter implements Exporter {

  private static final Logger LOG =
      LoggerFactory.getLogger(AnalyticsExporter.class.getPackageName());

  private static final SampledLogger SAMPLED_LOG =
      new SampledLogger(LOG, Duration.ofMinutes(1).toMillis());
  private static final SampledLogger SAMPLED_ERROR_LOG =
      new SampledLogger(LOG, Duration.ofMinutes(1).toMillis());

  private final OtelSdkManager otelSdkManager;

  private AnalyticsExporterConfig config;
  private Controller controller;
  private EnumMap<ValueType, Consumer<Record<?>>> handlers;
  private int partitionId;
  private String clusterId;

  public AnalyticsExporter() {
    this(new OtelSdkManager());
  }

  AnalyticsExporter(final OtelSdkManager otelSdkManager) {
    this.otelSdkManager = otelSdkManager;
  }

  @Override
  public void configure(final Context context) {
    config = context.getConfiguration().instantiate(AnalyticsExporterConfig.class);
    partitionId = context.getPartitionId();
    clusterId = context.getClusterId();

    config.validate();

    handlers = new EnumMap<>(ValueType.class);
    registerHandler(ValueType.PROCESS_INSTANCE_CREATION, this::handleProcessInstanceCreation);

    LOG.info(
        "Analytics exporter configured: enabled={}, endpoint={}, clusterId={}",
        config.isEnabled(),
        config.getEndpoint(),
        clusterId);
  }

  @Override
  public void open(final Controller controller) {
    this.controller = controller;
    otelSdkManager.initialize(config, clusterId, partitionId);
    LOG.info("Analytics exporter opened");
  }

  @Override
  public void close() {
    otelSdkManager.shutdown();
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
      SAMPLED_ERROR_LOG.warn("Failed to handle record at position {}", record.getPosition(), e);
    }
  }

  private void handleProcessInstanceCreation(
      final Record<ProcessInstanceCreationRecordValue> record) {
    final var value = record.getValue();

    otelSdkManager.logEvent(
        "process_instance_created",
        record.getPosition(),
        log ->
            log.setAttribute(BPMN_PROCESS_ID, value.getBpmnProcessId())
                .setAttribute(PROCESS_VERSION, (long) value.getVersion())
                .setAttribute(PROCESS_DEFINITION_KEY, value.getProcessDefinitionKey())
                .setAttribute(PROCESS_INSTANCE_KEY, record.getKey())
                .setAttribute(ROOT_PROCESS_INSTANCE_KEY, value.getRootProcessInstanceKey())
                .setAttribute(TENANT_ID, value.getTenantId())
                .setTimestamp(record.getTimestamp(), TimeUnit.MILLISECONDS));

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
