/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics.handler;

import io.camunda.exporter.analytics.AnalyticsAttributes;
import io.camunda.exporter.analytics.AnalyticsCategory;
import io.camunda.exporter.analytics.AnalyticsHandler;
import io.camunda.exporter.analytics.OtelSdkManager;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Emits a {@code camunda.process.definition.created} OTel event for each deployed process
 * definition. Emits only definition metadata — never the BPMN resource, resource name, or version
 * tag, all of which are author-supplied free text.
 */
public final class ProcessDefinitionCreatedHandler implements AnalyticsHandler<Process> {

  private final OtelSdkManager otelSdkManager;

  public ProcessDefinitionCreatedHandler(final OtelSdkManager otelSdkManager) {
    this.otelSdkManager = Objects.requireNonNull(otelSdkManager);
  }

  @Override
  public AnalyticsCategory category() {
    return AnalyticsCategory.OPTIONAL;
  }

  @Override
  public void handle(final Record<Process> record) {
    final var value = record.getValue();

    otelSdkManager.logEvent(
        AnalyticsAttributes.Event.PROCESS_DEFINITION_CREATED,
        record.getPosition(),
        log ->
            log.setAttribute(AnalyticsAttributes.Process.BPMN_PROCESS_ID, value.getBpmnProcessId())
                .setAttribute(
                    AnalyticsAttributes.Process.DEFINITION_KEY, value.getProcessDefinitionKey())
                .setAttribute(AnalyticsAttributes.Process.VERSION, (long) value.getVersion())
                .setAttribute(AnalyticsAttributes.Tenant.ID, value.getTenantId())
                .setTimestamp(record.getTimestamp(), TimeUnit.MILLISECONDS));
  }
}
