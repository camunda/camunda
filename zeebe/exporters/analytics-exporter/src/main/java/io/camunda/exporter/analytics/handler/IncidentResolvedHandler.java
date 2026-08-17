/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics.handler;

import static io.camunda.exporter.analytics.AnalyticsAttributes.Event.PROCESS_INCIDENT_RESOLVED;
import static io.camunda.exporter.analytics.AnalyticsAttributes.Process.BPMN_PROCESS_ID;
import static io.camunda.exporter.analytics.AnalyticsAttributes.Process.DEFINITION_KEY;
import static io.camunda.exporter.analytics.AnalyticsAttributes.Process.INSTANCE_KEY;

import io.camunda.exporter.analytics.AnalyticsAttributes;
import io.camunda.exporter.analytics.AnalyticsCategory;
import io.camunda.exporter.analytics.AnalyticsHandler;
import io.camunda.exporter.analytics.OtelSdkManager;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Emits a {@code camunda.process.incident.resolved} OTel event for each resolved incident. Carries
 * the same attributes as {@code camunda.process.incident.created} so that time-to-resolution is a
 * downstream join on the incident key.
 *
 * <p>The incident key is taken from the record key: {@link IncidentRecordValue} carries no incident
 * key of its own.
 */
public final class IncidentResolvedHandler implements AnalyticsHandler<IncidentRecordValue> {

  private final OtelSdkManager otelSdkManager;

  public IncidentResolvedHandler(final OtelSdkManager otelSdkManager) {
    this.otelSdkManager = Objects.requireNonNull(otelSdkManager);
  }

  @Override
  public AnalyticsCategory category() {
    return AnalyticsCategory.OPTIONAL;
  }

  @Override
  public void handle(final Record<IncidentRecordValue> record) {
    final var value = record.getValue();

    otelSdkManager.logEvent(
        PROCESS_INCIDENT_RESOLVED,
        record.getPosition(),
        log ->
            log.setAttribute(AnalyticsAttributes.Incident.KEY, record.getKey())
                .setAttribute(BPMN_PROCESS_ID, value.getBpmnProcessId())
                .setAttribute(DEFINITION_KEY, value.getProcessDefinitionKey())
                .setAttribute(INSTANCE_KEY, value.getProcessInstanceKey())
                .setAttribute(AnalyticsAttributes.Tenant.ID, value.getTenantId())
                .setTimestamp(record.getTimestamp(), TimeUnit.MILLISECONDS));
  }
}
