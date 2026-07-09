/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics.handler;

import static io.camunda.exporter.analytics.AnalyticsAttributes.Event.ADHOC_SUBPROCESS_ACTIVATED;
import static io.camunda.exporter.analytics.AnalyticsAttributes.Process.BPMN_PROCESS_ID;
import static io.camunda.exporter.analytics.AnalyticsAttributes.Process.DEFINITION_KEY;
import static io.camunda.exporter.analytics.AnalyticsAttributes.Process.INSTANCE_KEY;

import io.camunda.exporter.analytics.AnalyticsAttributes;
import io.camunda.exporter.analytics.AnalyticsHandler;
import io.camunda.exporter.analytics.OtelSdkManager;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Emits an {@code adhoc_subprocess_activated} OTel event when an ad-hoc subprocess element is
 * activated. Skips non-ad-hoc element types silently.
 */
public final class AdHocSubProcessHandler implements AnalyticsHandler<ProcessInstanceRecordValue> {

  private final OtelSdkManager otelSdkManager;

  public AdHocSubProcessHandler(final OtelSdkManager otelSdkManager) {
    this.otelSdkManager = Objects.requireNonNull(otelSdkManager);
  }

  @Override
  public void handle(final Record<ProcessInstanceRecordValue> record) {
    final var value = record.getValue();
    if (value.getBpmnElementType() != BpmnElementType.AD_HOC_SUB_PROCESS) {
      return;
    }

    otelSdkManager.logEvent(
        ADHOC_SUBPROCESS_ACTIVATED,
        record.getPosition(),
        log ->
            log.setAttribute(BPMN_PROCESS_ID, value.getBpmnProcessId())
                .setAttribute(DEFINITION_KEY, value.getProcessDefinitionKey())
                .setAttribute(INSTANCE_KEY, value.getProcessInstanceKey())
                // Element.ID and Tenant.ID share the unqualified name ID — use qualified form to
                // disambiguate
                .setAttribute(AnalyticsAttributes.Element.ID, value.getElementId())
                .setAttribute(AnalyticsAttributes.Tenant.ID, value.getTenantId())
                .setTimestamp(record.getTimestamp(), TimeUnit.MILLISECONDS));
  }
}
