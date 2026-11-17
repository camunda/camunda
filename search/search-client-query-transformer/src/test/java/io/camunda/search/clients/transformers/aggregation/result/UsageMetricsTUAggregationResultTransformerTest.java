/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation.result;

import static io.camunda.search.aggregation.UsageMetricsTUAggregation.AGGREGATION_TERMS_ASSIGNEE_HASH;
import static io.camunda.search.aggregation.UsageMetricsTUAggregation.AGGREGATION_TERMS_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.core.AggregationResult;
import io.camunda.search.entities.UsageMetricTUStatisticsEntity.UsageMetricTUStatisticsEntityTenant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UsageMetricsTUAggregationResultTransformerTest {

  private final UsageMetricsTUAggregationResultTransformer transformer =
      new UsageMetricsTUAggregationResultTransformer();

  @Test
  void shouldReturnZeroTuWhenAggregationsNull() {
    // when
    final var result = transformer.apply(null).result();

    // then
    assertThat(result.totalTu()).isZero();
    assertThat(result.tenants()).isEmpty();
  }

  @Test
  void shouldReturnZeroTuWhenAggregationsEmpty() {
    // when
    final var result = transformer.apply(Map.of()).result();

    // then
    assertThat(result.totalTu()).isZero();
    assertThat(result.tenants()).isEmpty();
  }

  @Test
  void shouldReturnZeroTuWhenAssigneeHashAggregationMissing() {
    // given
    final Map<String, AggregationResult> input =
        Map.of(AGGREGATION_TERMS_TENANT_ID, new AggregationResult(0L, Map.of()));

    // when
    final var result = transformer.apply(input).result();

    // then
    assertThat(result.totalTu()).isZero();
    assertThat(result.tenants()).isEmpty();
  }

  @Test
  void shouldReturnTotalTuWithoutTenants() {
    // given
    final Map<String, AggregationResult> input =
        Map.of(AGGREGATION_TERMS_ASSIGNEE_HASH, new AggregationResult(7L, Map.of()));

    // when
    final var result = transformer.apply(input).result();

    // then
    assertThat(result.totalTu()).isEqualTo(7L);
    assertThat(result.tenants()).isEmpty();
  }

  @Test
  void shouldReturnTuPerTenantWhenTenantAggregationPresent() {
    // given
    final Map<String, AggregationResult> input =
        Map.of(
            AGGREGATION_TERMS_ASSIGNEE_HASH,
            new AggregationResult(7L, Map.of()),
            AGGREGATION_TERMS_TENANT_ID,
            new AggregationResult(
                null,
                Map.of(
                    "tenant-a", tenantAggregation(4L),
                    "tenant-b", tenantAggregation(3L))));

    // when
    final var result = transformer.apply(input).result();

    // then
    assertThat(result.totalTu()).isEqualTo(7L);
    assertThat(result.tenants())
        .containsOnly(
            Map.entry("tenant-a", new UsageMetricTUStatisticsEntityTenant(4L)),
            Map.entry("tenant-b", new UsageMetricTUStatisticsEntityTenant(3L)));
  }

  private static AggregationResult tenantAggregation(final long tu) {
    return new AggregationResult(
        tu, Map.of(AGGREGATION_TERMS_ASSIGNEE_HASH, new AggregationResult(tu, Map.of())));
  }
}
