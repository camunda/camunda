/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics.handler;

import static io.camunda.exporter.analytics.AnalyticsAttributes.Event.USER_TASK_CREATED;
import static io.camunda.exporter.analytics.AnalyticsAttributes.Process.BPMN_PROCESS_ID;
import static io.camunda.exporter.analytics.AnalyticsAttributes.Process.DEFINITION_KEY;
import static io.camunda.exporter.analytics.AnalyticsAttributes.Process.INSTANCE_KEY;

import io.camunda.exporter.analytics.AnalyticsAttributes;
import io.camunda.exporter.analytics.AnalyticsHandler;
import io.camunda.exporter.analytics.OtelSdkManager;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Emits a {@code user_task_created} OTel event for each user task creation. Emits only safe
 * process-metadata attributes — never assignee, candidate users, candidate groups, or variables.
 */
public final class UserTaskCreatedHandler implements AnalyticsHandler<UserTaskRecordValue> {

  private final OtelSdkManager otelSdkManager;

  public UserTaskCreatedHandler(final OtelSdkManager otelSdkManager) {
    this.otelSdkManager = Objects.requireNonNull(otelSdkManager);
  }

  @Override
  public void handle(final Record<UserTaskRecordValue> record) {
    final var value = record.getValue();

    otelSdkManager.logEvent(
        USER_TASK_CREATED,
        record.getPosition(),
        log ->
            log.setAttribute(BPMN_PROCESS_ID, value.getBpmnProcessId())
                .setAttribute(DEFINITION_KEY, value.getProcessDefinitionKey())
                .setAttribute(INSTANCE_KEY, value.getProcessInstanceKey())
                // Element.ID and Tenant.ID share the unqualified name ID -- use qualified form to
                // disambiguate
                .setAttribute(AnalyticsAttributes.Element.ID, value.getElementId())
                .setAttribute(AnalyticsAttributes.Tenant.ID, value.getTenantId())
                .setTimestamp(record.getTimestamp(), TimeUnit.MILLISECONDS));
  }
}
