/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics.handler;

import static io.camunda.exporter.analytics.AnalyticsAttributes.Process.BPMN_PROCESS_ID;
import static io.camunda.exporter.analytics.AnalyticsAttributes.Process.DEFINITION_KEY;
import static io.camunda.exporter.analytics.AnalyticsAttributes.Process.INSTANCE_KEY;
import static io.camunda.exporter.analytics.AnalyticsAttributes.Process.VERSION;
import static io.camunda.exporter.analytics.AnalyticsAttributes.Tenant.ID;

import io.camunda.exporter.analytics.AnalyticsAttributes;
import io.camunda.exporter.analytics.AnalyticsHandler;
import io.camunda.exporter.analytics.OtelSdkManager;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue;
import io.opentelemetry.api.common.Attributes;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Emits a {@code process_instance_created} OTel event for each process instance creation. */
public final class ProcessInstanceCreationHandler
    implements AnalyticsHandler<ProcessInstanceCreationRecordValue> {

  private final OtelSdkManager otelSdkManager;

  public ProcessInstanceCreationHandler(final OtelSdkManager otelSdkManager) {
    this.otelSdkManager = Objects.requireNonNull(otelSdkManager);
  }

  @Override
  public void handle(final Record<ProcessInstanceCreationRecordValue> record) {
    final var value = record.getValue();

    otelSdkManager.logEvent(
        AnalyticsAttributes.Event.PROCESS_INSTANCE_CREATED,
        record.getPosition(),
        log ->
            log.setAttribute(BPMN_PROCESS_ID, value.getBpmnProcessId())
                .setAttribute(VERSION, (long) value.getVersion())
                .setAttribute(DEFINITION_KEY, value.getProcessDefinitionKey())
                .setAttribute(INSTANCE_KEY, record.getKey())
                // TODO: re-enable once 8.8 brokers are no longer supported —
                //  getRootProcessInstanceKey() does not exist on 8.8
                // .setAttribute(AnalyticsAttributes.Process.ROOT_INSTANCE_KEY,
                // value.getRootProcessInstanceKey())
                .setAttribute(ID, value.getTenantId())
                .setTimestamp(record.getTimestamp(), TimeUnit.MILLISECONDS));

    otelSdkManager.incrementMetric(
        AnalyticsAttributes.Metric.PROCESS_INSTANCE_CREATED,
        record.getPosition(),
        record.getTimestamp(),
        Attributes.of(
            BPMN_PROCESS_ID, value.getBpmnProcessId(),
            VERSION, (long) value.getVersion(),
            ID, value.getTenantId()));
  }
}
