/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.agentic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.camunda.optimize.dto.optimize.query.agentic.AgentQueryParams;
import io.camunda.optimize.service.db.es.filter.agentic.AgentBaselineFilterBuilderES;
import io.camunda.optimize.service.db.os.filter.agentic.AgentBaselineFilterBuilderOS;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class AgenticControlPlaneUtilsTest {

  private static final Instant NOW = Instant.parse("2024-01-31T00:00:00Z");
  private static final Instant SEVEN_DAYS_AGO = NOW.minus(7, ChronoUnit.DAYS);
  private static final List<String> TENANTS = List.of("tenant-a", "tenant-b");

  // ── AgentBaselineFilterBuilderES ────────────────────────────────────────────

  @Test
  void shouldBuildEsBaseFilterWithoutProcessKey() {
    // given
    final AgentQueryParams params = params(null);

    // when
    final var result = AgentBaselineFilterBuilderES.build(params).build();

    // then: state + date range + tenantId + nested exists = 4 clauses
    assertThat(result.filter()).hasSize(4);
  }

  @Test
  void shouldBuildEsBaseFilterWithProcessKey() {
    // given
    final AgentQueryParams params = params("myProcess");

    // when
    final var result = AgentBaselineFilterBuilderES.build(params).build();

    // then: 4 base clauses + processDefinitionKey term = 5
    assertThat(result.filter()).hasSize(5);
  }

  // ── AgentBaselineFilterBuilderOS ────────────────────────────────────────────

  @Test
  void shouldBuildOsBaseFilterWithoutProcessKey() {
    // given
    final AgentQueryParams params = params(null);

    // when
    final List<?> filters = AgentBaselineFilterBuilderOS.build(params);

    // then: state + date range + tenantId + nested exists = 4 clauses
    assertThat(filters).hasSize(4);
  }

  @Test
  void shouldBuildOsBaseFilterWithProcessKey() {
    // given
    final AgentQueryParams params = params("myProcess");

    // when
    final List<?> filters = AgentBaselineFilterBuilderOS.build(params);

    // then: 4 base clauses + processDefinitionKey term = 5
    assertThat(filters).hasSize(5);
  }

  // ── PeriodComparisonExecutor ─────────────────────────────────────────────────

  @Test
  void shouldExecuteBothPeriodQueriesAndReturnPairedResults() {
    // given
    final AgentQueryParams params = params(null);
    final AtomicReference<Instant> capturedPreviousFrom = new AtomicReference<>();
    final PeriodComparisonExecutor executor =
        new PeriodComparisonExecutor(Runnable::run); // inline executor for test

    final Function<AgentQueryParams, String> query =
        p -> {
          if (!p.startDateFrom().equals(params.startDateFrom())) {
            capturedPreviousFrom.set(p.startDateFrom());
          }
          return "result-" + p.startDateFrom();
        };

    // when
    final PeriodComparisonResult<String> result = executor.execute(params, query);

    // then: current uses original params
    assertThat(result.current()).isEqualTo("result-" + params.startDateFrom());

    // then: previous is shifted back by same 7-day range
    assertThat(capturedPreviousFrom.get())
        .isCloseTo(SEVEN_DAYS_AGO.minus(7, ChronoUnit.DAYS), within(1, ChronoUnit.SECONDS));
    assertThat(result.previous()).isNotEqualTo(result.current());
  }

  // ── DateIntervalResolver ─────────────────────────────────────────────────────

  @Test
  void shouldResolveAllDateIntervalBranches() {
    final Instant base = Instant.parse("2024-01-01T00:00:00Z");

    // ≤ 2 days → 1h
    assertThat(DateIntervalResolver.resolve(base, base.plus(1, ChronoUnit.DAYS))).isEqualTo("1h");
    assertThat(DateIntervalResolver.resolve(base, base.plus(2, ChronoUnit.DAYS))).isEqualTo("1h");

    // ≤ 30 days → 1d
    assertThat(DateIntervalResolver.resolve(base, base.plus(3, ChronoUnit.DAYS))).isEqualTo("1d");
    assertThat(DateIntervalResolver.resolve(base, base.plus(30, ChronoUnit.DAYS))).isEqualTo("1d");

    // ≤ 180 days → 1w
    assertThat(DateIntervalResolver.resolve(base, base.plus(31, ChronoUnit.DAYS))).isEqualTo("1w");
    assertThat(DateIntervalResolver.resolve(base, base.plus(180, ChronoUnit.DAYS))).isEqualTo("1w");

    // > 180 days → 1M
    assertThat(DateIntervalResolver.resolve(base, base.plus(181, ChronoUnit.DAYS))).isEqualTo("1M");
    assertThat(DateIntervalResolver.resolve(base, base.plus(365, ChronoUnit.DAYS))).isEqualTo("1M");
  }

  // ── IncidentRateHelper ───────────────────────────────────────────────────────

  @Test
  void shouldComputeIncidentRateAndGuardZeroDenominator() {
    assertThat(IncidentRateHelper.computeRate(0L, 0L)).isEqualTo(0.0);
    assertThat(IncidentRateHelper.computeRate(5L, 0L)).isEqualTo(0.0);
    assertThat(IncidentRateHelper.computeRate(0L, 100L)).isEqualTo(0.0);
    assertThat(IncidentRateHelper.computeRate(5L, 100L)).isEqualTo(0.05);
    assertThat(IncidentRateHelper.computeRate(100L, 100L)).isEqualTo(1.0);
  }

  // ── helpers ─────────────────────────────────────────────────────────────────

  private AgentQueryParams params(final String processDefinitionKey) {
    return new AgentQueryParams(TENANTS, processDefinitionKey, null, SEVEN_DAYS_AGO, NOW);
  }
}
