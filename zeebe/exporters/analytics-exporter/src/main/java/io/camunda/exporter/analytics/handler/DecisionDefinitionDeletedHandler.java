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
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Emits a {@code camunda.decision.definition.deleted} OTel event for each deleted decision. Carries
 * the same attributes as {@code camunda.decision.definition.created}.
 */
public final class DecisionDefinitionDeletedHandler
    implements AnalyticsHandler<DecisionRecordValue> {

  private final OtelSdkManager otelSdkManager;

  public DecisionDefinitionDeletedHandler(final OtelSdkManager otelSdkManager) {
    this.otelSdkManager = Objects.requireNonNull(otelSdkManager);
  }

  @Override
  public AnalyticsCategory category() {
    return AnalyticsCategory.OPTIONAL;
  }

  @Override
  public void handle(final Record<DecisionRecordValue> record) {
    final var value = record.getValue();

    otelSdkManager.logEvent(
        AnalyticsAttributes.Event.DECISION_DEFINITION_DELETED,
        record.getPosition(),
        log ->
            log.setAttribute(AnalyticsAttributes.Decision.ID, value.getDecisionId())
                .setAttribute(AnalyticsAttributes.Decision.KEY, value.getDecisionKey())
                .setAttribute(AnalyticsAttributes.Decision.VERSION, (long) value.getVersion())
                .setAttribute(AnalyticsAttributes.Tenant.ID, value.getTenantId())
                .setTimestamp(record.getTimestamp(), TimeUnit.MILLISECONDS));
  }
}
