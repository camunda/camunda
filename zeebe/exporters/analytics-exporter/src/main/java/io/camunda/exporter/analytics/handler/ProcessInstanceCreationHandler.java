/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics.handler;

import static io.camunda.exporter.analytics.AnalyticsAttributes.BPMN_PROCESS_ID;
import static io.camunda.exporter.analytics.AnalyticsAttributes.PROCESS_DEFINITION_KEY;
import static io.camunda.exporter.analytics.AnalyticsAttributes.PROCESS_INSTANCE_KEY;
import static io.camunda.exporter.analytics.AnalyticsAttributes.PROCESS_VERSION;
import static io.camunda.exporter.analytics.AnalyticsAttributes.ROOT_PROCESS_INSTANCE_KEY;
import static io.camunda.exporter.analytics.AnalyticsAttributes.TENANT_ID;

import io.camunda.exporter.analytics.AnalyticsHandler;
import io.camunda.exporter.analytics.OtelSdkManager;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue;
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
  }
}
