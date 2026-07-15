/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.fact.Transition;
import io.camunda.analytics.query.SnapshotQueryExecutor;
import io.camunda.analytics.query.TableQueryExecutor;
import io.camunda.analytics.webapp.ServingTestSupport.Fixture;
import io.camunda.analytics.webapp.dashboard.DashboardRepository;
import io.camunda.analytics.webapp.dashboard.VariableCorrelation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The duration-variable correlation read: an overall boxplot fence from the process-duration cube's
 * percentile sketch, cross-checked against every {@code corr-*} cube's per-value sketch — the
 * cross-sketch query step 1's exposed sketch enables.
 */
final class DashboardCorrelationServingTest {

  private static final String PROCESS = "claim-process";

  private Fixture fixture;
  private DashboardRepository repository;

  @BeforeEach
  void setUp() {
    fixture = ServingTestSupport.create();
    repository =
        new DashboardRepository(
            fixture.executor(),
            fixture.catalog(),
            fixture.tableCatalog(),
            new TableQueryExecutor(fixture.datasetStore().queryClient()),
            new SnapshotQueryExecutor(fixture.datasetStore().queryClient()));
  }

  @AfterEach
  void tearDown() {
    fixture.datasetStore().close();
    fixture.metadataStore().close();
  }

  @Test
  void shouldRankAHeavilyOutlierBiasedValueOnTopByLift() {
    // given an overall population of 100 fast completions (~900-1100ms) and 10 far outliers
    // (~50_000ms) — a ~9% overall outlier share
    final List<Fact> overall = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      overall.add(completed(900L + i * 2L));
    }
    for (int i = 0; i < 10; i++) {
      overall.add(completed(50_000L));
    }
    ServingTestSupport.seed(fixture, "process-duration", "percentiles", overall, PROCESS);
    // and a corr-route cube where "manual" is almost entirely far outliers (heavily biased) while
    // "auto" sits entirely inside the fast cluster (not biased)
    final List<Fact> manual = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      manual.add(routeCompleted(49_000L + i * 100L, "manual"));
    }
    final List<Fact> auto = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      auto.add(routeCompleted(900L + i * 2L, "auto"));
    }
    ServingTestSupport.seed(fixture, "corr-route", "duration_p", manual, PROCESS, "manual");
    ServingTestSupport.seed(fixture, "corr-route", "duration_p", auto, PROCESS, "auto");

    // when the correlation is read
    final List<VariableCorrelation> correlations =
        repository.variableCorrelations(PROCESS, null, null);

    // then "manual" tops the ranking with lift well above 1, "auto" trails with lift near/below 1
    assertThat(correlations).isNotEmpty();
    final VariableCorrelation top = correlations.get(0);
    assertThat(top.variable()).isEqualTo("route");
    assertThat(top.value()).isEqualTo("manual");
    assertThat(top.lift()).isGreaterThan(1.5);
    final VariableCorrelation auto2 =
        correlations.stream().filter(c -> c.value().equals("auto")).findFirst().orElseThrow();
    assertThat(auto2.lift()).isLessThan(top.lift());
  }

  @Test
  void shouldExcludeAValueBelowTheMinimumObservationCount() {
    // given an overall outlier-bearing population
    final List<Fact> overall = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      overall.add(completed(900L + i * 2L));
    }
    for (int i = 0; i < 10; i++) {
      overall.add(completed(50_000L));
    }
    ServingTestSupport.seed(fixture, "process-duration", "percentiles", overall, PROCESS);
    // and a corr-route value with only 5 observations — below MIN_OBSERVATIONS (20)
    final List<Fact> rare = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      rare.add(routeCompleted(50_000L, "rare"));
    }
    ServingTestSupport.seed(fixture, "corr-route", "duration_p", rare, PROCESS, "rare");

    // when read
    final List<VariableCorrelation> correlations =
        repository.variableCorrelations(PROCESS, null, null);

    // then the sparse value never appears
    assertThat(correlations).extracting(VariableCorrelation::value).doesNotContain("rare");
  }

  @Test
  void shouldServeEmptyWhenTheOverallOutlierShareIsZero() {
    // given a constant overall duration distribution — Q1 == Q3 == the constant, fence == the
    // constant, and every observation sits at or below it: nothing to correlate against
    final List<Fact> constant = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
      constant.add(completed(1_000L));
    }
    ServingTestSupport.seed(fixture, "process-duration", "percentiles", constant, PROCESS);
    ServingTestSupport.seed(fixture, "corr-route", "duration_p", constant, PROCESS, "onlyValue");

    // when / then
    assertThat(repository.variableCorrelations(PROCESS, null, null)).isEmpty();
  }

  @Test
  void shouldServeEmptyWhenThereIsNoCorrCubeInTheCatalog() {
    // given a catalog with only process-duration — no corr-* cube provisioned on this store
    final CompiledDataset processDuration = fixture.catalog().require("process-duration");
    final Map<String, CompiledDataset> byName = new LinkedHashMap<>();
    byName.put(processDuration.name(), processDuration);
    final DashboardRepository withoutCorrCubes =
        new DashboardRepository(
            fixture.executor(),
            new DatasetCatalog(byName),
            fixture.tableCatalog(),
            new TableQueryExecutor(fixture.datasetStore().queryClient()),
            new SnapshotQueryExecutor(fixture.datasetStore().queryClient()));
    final List<Fact> overall = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      overall.add(completed(900L + i * 2L));
    }
    for (int i = 0; i < 10; i++) {
      overall.add(completed(50_000L));
    }
    ServingTestSupport.seed(fixture, "process-duration", "percentiles", overall, PROCESS);

    // when / then — never a 500, just an empty read
    assertThat(withoutCorrCubes.variableCorrelations(PROCESS, null, null)).isEmpty();
  }

  private static Fact completed(final long durationMs) {
    return Fact.builder(FactType.PROCESS_INSTANCE)
        .transition(Transition.COMPLETED)
        .field("durationMs", durationMs)
        .build();
  }

  private static Fact routeCompleted(final long durationMs, final String route) {
    return Fact.builder(FactType.PROCESS_INSTANCE)
        .transition(Transition.COMPLETED)
        .field("durationMs", durationMs)
        .field("var.route", route)
        .build();
  }
}
