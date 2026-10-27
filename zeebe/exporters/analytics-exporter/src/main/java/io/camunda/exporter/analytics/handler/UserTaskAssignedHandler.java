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
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Emits a {@code camunda.user_task.assigned} OTel event for each user task assignment. The assignee
 * is PII and no assignee-derived data (raw or hashed) is emitted; the event only signals that an
 * assignment happened.
 *
 * <p>Records with an empty assignee are skipped, matching the engine's assignment guard.
 */
public final class UserTaskAssignedHandler implements AnalyticsHandler<UserTaskRecordValue> {

  private final OtelSdkManager otelSdkManager;

  public UserTaskAssignedHandler(final OtelSdkManager otelSdkManager) {
    this.otelSdkManager = Objects.requireNonNull(otelSdkManager);
  }

  @Override
  public AnalyticsCategory category() {
    return AnalyticsCategory.CONTRACTUAL;
  }

  @Override
  public void handle(final Record<UserTaskRecordValue> record) {
    final var value = record.getValue();
    final var assignee = value.getAssignee();
    if (assignee == null || assignee.isEmpty()) {
      return;
    }

    otelSdkManager.logEvent(
        AnalyticsAttributes.Event.USER_TASK_ASSIGNED,
        record.getPosition(),
        log ->
            log.setAttribute(AnalyticsAttributes.UserTask.KEY, value.getUserTaskKey())
                .setAttribute(
                    AnalyticsAttributes.Process.INSTANCE_KEY, value.getProcessInstanceKey())
                .setAttribute(AnalyticsAttributes.Tenant.ID, value.getTenantId())
                .setTimestamp(record.getTimestamp(), TimeUnit.MILLISECONDS));
  }
}
