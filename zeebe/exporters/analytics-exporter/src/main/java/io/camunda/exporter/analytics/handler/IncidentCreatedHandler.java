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
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Emits a {@code camunda.process.incident.created} OTel event for each raised incident. Emits only
 * safe process-metadata attributes — never the error message, which can carry variable values and
 * other end-user data.
 *
 * <p>The incident key is taken from the record key: {@link IncidentRecordValue} carries no incident
 * key of its own. It is emitted so that resolution can be joined to creation downstream.
 */
public final class IncidentCreatedHandler implements AnalyticsHandler<IncidentRecordValue> {

  private final OtelSdkManager otelSdkManager;

  public IncidentCreatedHandler(final OtelSdkManager otelSdkManager) {
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
        AnalyticsAttributes.Event.PROCESS_INCIDENT_CREATED,
        record.getPosition(),
        log ->
            log.setAttribute(AnalyticsAttributes.Incident.KEY, record.getKey())
                .setAttribute(AnalyticsAttributes.Process.BPMN_PROCESS_ID, value.getBpmnProcessId())
                .setAttribute(
                    AnalyticsAttributes.Process.DEFINITION_KEY, value.getProcessDefinitionKey())
                .setAttribute(
                    AnalyticsAttributes.Process.INSTANCE_KEY, value.getProcessInstanceKey())
                .setAttribute(AnalyticsAttributes.Tenant.ID, value.getTenantId())
                .setTimestamp(record.getTimestamp(), TimeUnit.MILLISECONDS));
  }
}
