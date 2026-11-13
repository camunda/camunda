/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation.result;

import static io.camunda.search.aggregation.UsageMetricsAggregation.AGGREGATION_SUM_EVENT_TYPE;
import static io.camunda.search.aggregation.UsageMetricsAggregation.AGGREGATION_TERMS_EVENT_TYPE;
import static io.camunda.search.aggregation.UsageMetricsAggregation.AGGREGATION_TERMS_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.core.AggregationResult;
import io.camunda.search.entities.UsageMetricStatisticsEntity.UsageMetricStatisticsEntityTenant;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsEventType;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UsageMetricsAggregationResultTransformerTest {

  private final UsageMetricsAggregationResultTransformer transformer =
      new UsageMetricsAggregationResultTransformer();

  @Test
  void shouldReturnZeroStatisticsWhenAggregationsNull() {
    // when
    final var result = transformer.apply(null).result();

    // then
    assertThat(result.totalRpi()).isZero();
    assertThat(result.totalEdi()).isZero();
    assertThat(result.at()).isZero();
    assertThat(result.tenants()).isEmpty();
  }

  @Test
  void shouldReturnZeroStatisticsWhenAggregationsEmpty() {
    // when
    final var result = transformer.apply(Map.of()).result();

    // then
    assertThat(result.totalRpi()).isZero();
    assertThat(result.totalEdi()).isZero();
    assertThat(result.at()).isZero();
    assertThat(result.tenants()).isEmpty();
  }

  @Test
  void shouldReturnTotalsWithoutTenants() {
    // given
    final Map<String, AggregationResult> input =
        Map.of(
            AGGREGATION_TERMS_EVENT_TYPE,
            eventTypesAggregation(5L, 2L),
            AGGREGATION_TERMS_TENANT_ID,
            new AggregationResult(
                null,
                Map.of(
                    "tenant-a", new AggregationResult(1L, Map.of()),
                    "tenant-b", new AggregationResult(1L, Map.of()))));

    // when
    final var result = transformer.apply(input).result();

    // then
    assertThat(result.totalRpi()).isEqualTo(5L);
    assertThat(result.totalEdi()).isEqualTo(2L);
    assertThat(result.at()).isEqualTo(2L);
    assertThat(result.tenants()).isEmpty();
  }

  @Test
  void shouldReturnTotalsAndPerTenantBreakdownWhenTenantsRequested() {
    // given
    final Map<String, AggregationResult> input =
        Map.of(
            AGGREGATION_TERMS_TENANT_ID,
            new AggregationResult(
                null,
                Map.of(
                    "tenant-a", tenantAggregation(5L, 2L),
                    "tenant-b", tenantAggregation(1L, 3L))));

    // when
    final var result = transformer.apply(input).result();

    // then
    assertThat(result.totalRpi()).isEqualTo(6L);
    assertThat(result.totalEdi()).isEqualTo(5L);
    assertThat(result.at()).isEqualTo(2L);
    assertThat(result.tenants())
        .containsOnly(
            Map.entry("tenant-a", new UsageMetricStatisticsEntityTenant(5L, 2L)),
            Map.entry("tenant-b", new UsageMetricStatisticsEntityTenant(1L, 3L)));
  }

  private static AggregationResult tenantAggregation(final long rpi, final long edi) {
    return new AggregationResult(
        null, Map.of(AGGREGATION_TERMS_EVENT_TYPE, eventTypesAggregation(rpi, edi)));
  }

  private static AggregationResult eventTypesAggregation(final long rpi, final long edi) {
    return new AggregationResult(
        null,
        Map.of(
            UsageMetricsEventType.RPI.name(), sumAggregation(rpi),
            UsageMetricsEventType.EDI.name(), sumAggregation(edi)));
  }

  private static AggregationResult sumAggregation(final long count) {
    return new AggregationResult(
        null, Map.of(AGGREGATION_SUM_EVENT_TYPE, new AggregationResult(count, Map.of())));
  }
}
