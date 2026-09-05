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
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;
import io.opentelemetry.api.common.Attributes;
import java.util.Objects;

/**
 * Increments the {@code camunda.decision.instance.evaluated} counter for each evaluated decision
 * instance. Exactly one increment per record, so the counter matches the engine's EDI usage metric:
 * the sub-decisions in {@code getEvaluatedDecisions()} must not be counted separately.
 */
public final class DecisionInstanceEvaluatedHandler
    implements AnalyticsHandler<DecisionEvaluationRecordValue> {

  private final OtelSdkManager otelSdkManager;

  public DecisionInstanceEvaluatedHandler(final OtelSdkManager otelSdkManager) {
    this.otelSdkManager = Objects.requireNonNull(otelSdkManager);
  }

  @Override
  public AnalyticsCategory category() {
    return AnalyticsCategory.CONTRACTUAL;
  }

  @Override
  public void handle(final Record<DecisionEvaluationRecordValue> record) {
    final var value = record.getValue();

    otelSdkManager.incrementMetric(
        AnalyticsAttributes.Metric.DECISION_INSTANCE_EVALUATED,
        record.getPosition(),
        record.getTimestamp(),
        Attributes.of(AnalyticsAttributes.Tenant.ID, value.getTenantId()));
  }
}
