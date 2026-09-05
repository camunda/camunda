/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics.handler;

import static io.camunda.exporter.analytics.AnalyticsAttributes.Tenant.ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.analytics.AnalyticsAttributes;
import io.camunda.exporter.analytics.AnalyticsCategory;
import io.camunda.exporter.analytics.TestOtelSdkManager;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.EvaluatedDecisionValue;
import io.camunda.zeebe.protocol.record.value.ImmutableDecisionEvaluationRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DecisionInstanceEvaluatedHandlerTest {

  private static final ProtocolFactory FACTORY = new ProtocolFactory();

  private InMemoryLogRecordExporter logExporter;
  private InMemoryMetricReader metricReader;
  private DecisionInstanceEvaluatedHandler handler;

  @BeforeEach
  void setUp() {
    logExporter = InMemoryLogRecordExporter.create();
    metricReader = InMemoryMetricReader.create();
    handler =
        new DecisionInstanceEvaluatedHandler(
            TestOtelSdkManager.inMemoryWithMetrics(logExporter, metricReader));
  }

  @SuppressWarnings("unchecked")
  private static <T extends RecordValue> Record<T> typed(final Record<?> record) {
    return (Record<T>) record;
  }

  private static Record<?> evaluatedRecord(final String tenantId) {
    return evaluatedRecord(tenantId, List.of(FACTORY.generateObject(EvaluatedDecisionValue.class)));
  }

  private static Record<?> evaluatedRecord(
      final String tenantId, final List<EvaluatedDecisionValue> evaluatedDecisions) {
    final var value =
        ImmutableDecisionEvaluationRecordValue.builder()
            .from(FACTORY.generateObject(DecisionEvaluationRecordValue.class))
            .withTenantId(tenantId)
            .withEvaluatedDecisions(evaluatedDecisions)
            .build();
    return FACTORY.generateRecord(
        ValueType.DECISION_EVALUATION,
        r ->
            r.withRecordType(RecordType.EVENT)
                .withIntent(DecisionEvaluationIntent.EVALUATED)
                .withValue(value));
  }

  private Optional<MetricData> counter() {
    final Collection<MetricData> metrics = metricReader.collectAllMetrics();
    return metrics.stream()
        .filter(m -> m.getName().equals(AnalyticsAttributes.Metric.DECISION_INSTANCE_EVALUATED))
        .findFirst();
  }

  @Test
  void shouldAccumulateOneIncrementPerRecord() {
    // when
    handler.handle(typed(evaluatedRecord("tenant-a")));
    handler.handle(typed(evaluatedRecord("tenant-a")));

    // then
    assertThat(counter())
        .isPresent()
        .hasValueSatisfying(
            metric -> {
              final long total =
                  metric.getLongSumData().getPoints().stream()
                      .mapToLong(LongPointData::getValue)
                      .sum();
              assertThat(total).isEqualTo(2);
            });
  }

  @Test
  void shouldCountOncePerRecordWhenSeveralDecisionsAreEvaluated() {
    // given a DRG evaluation: the requested decision plus its required sub-decisions
    final Record<DecisionEvaluationRecordValue> record =
        typed(
            evaluatedRecord(
                "tenant-a",
                List.of(
                    FACTORY.generateObject(EvaluatedDecisionValue.class),
                    FACTORY.generateObject(EvaluatedDecisionValue.class),
                    FACTORY.generateObject(EvaluatedDecisionValue.class))));
    assertThat(record.getValue().getEvaluatedDecisions()).hasSize(3);

    // when
    handler.handle(record);

    // then
    assertThat(counter())
        .isPresent()
        .hasValueSatisfying(
            metric ->
                assertThat(metric.getLongSumData().getPoints())
                    .singleElement()
                    .extracting(LongPointData::getValue)
                    .isEqualTo(1L));
  }

  @Test
  void shouldCarryTenantIdAsDimension() {
    // when
    handler.handle(typed(evaluatedRecord("tenant-a")));
    handler.handle(typed(evaluatedRecord("tenant-b")));

    // then
    assertThat(counter())
        .isPresent()
        .hasValueSatisfying(
            metric ->
                assertThat(metric.getLongSumData().getPoints())
                    .hasSize(2)
                    .allSatisfy(point -> assertThat(point.getValue()).isEqualTo(1))
                    .extracting(point -> point.getAttributes().get(ID))
                    .containsExactlyInAnyOrder("tenant-a", "tenant-b"));
  }

  @Test
  void shouldReturnCorrectCategory() {
    assertThat(handler.category()).isEqualTo(AnalyticsCategory.CONTRACTUAL);
  }
}
