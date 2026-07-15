/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.fact.Transition;
import io.camunda.analytics.query.SnapshotQueryExecutor;
import io.camunda.analytics.query.TableQueryExecutor;
import io.camunda.analytics.webapp.ServingTestSupport.Fixture;
import io.camunda.analytics.webapp.dashboard.DashboardController;
import io.camunda.analytics.webapp.dashboard.DashboardRepository;
import io.camunda.analytics.webapp.dashboard.ElementOutlier;
import io.camunda.analytics.webapp.dashboard.VariableCorrelation;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The outliers and variable-correlation endpoints wire straight through to the repository, and —
 * mirroring the neighboring endpoints' parameter validation — an empty/reversed range is never a
 * 500, just an empty (or query-defined) result.
 */
final class DashboardOutliersControllerTest {

  private static final String PROCESS = "claim-process";

  private Fixture fixture;
  private DashboardController controller;

  @BeforeEach
  void setUp() {
    fixture = ServingTestSupport.create();
    final DashboardRepository repository =
        new DashboardRepository(
            fixture.executor(),
            fixture.catalog(),
            fixture.tableCatalog(),
            new TableQueryExecutor(fixture.datasetStore().queryClient()),
            new SnapshotQueryExecutor(fixture.datasetStore().queryClient()));
    controller = new DashboardController(repository);
  }

  @AfterEach
  void tearDown() {
    fixture.datasetStore().close();
    fixture.metadataStore().close();
  }

  @Test
  void shouldServeOutliersForAProcessWithEnoughObservations() {
    // given a hot element with a skewed distribution well above MIN_OBSERVATIONS
    final List<Fact> facts = new ArrayList<>();
    for (int i = 0; i < 25; i++) {
      facts.add(elementCompleted(900L + i * 4L));
    }
    for (int i = 0; i < 5; i++) {
      facts.add(elementCompleted(100_000L));
    }
    ServingTestSupport.seed(fixture, "elements", "duration_p", facts, PROCESS, "worker");

    // when the endpoint is called directly (mirrors the repository read)
    final List<ElementOutlier> outliers = controller.outliers(PROCESS, null, null);

    // then it serves the same row the repository would
    assertThat(outliers)
        .singleElement()
        .satisfies(o -> assertThat(o.elementId()).isEqualTo("worker"));
  }

  @Test
  void shouldServeAnEmptyOutlierListForAnUnknownProcess() {
    assertThat(controller.outliers("unknown", null, null)).isEmpty();
  }

  @Test
  void shouldRejectAReversedRangeLikeEveryNeighboringEndpoint() {
    // given / when / then — an inverted [from, to) is rejected at the query layer for every
    // dashboard endpoint (ReportQuery), turned into a 400 by the controller's exception handler;
    // this endpoint does not invent different behavior
    assertThatThrownBy(() -> controller.outliers(PROCESS, 10_000L, 5_000L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldServeVariableCorrelationForAProcessWithOutliers() {
    // given an overall outlier-bearing population and a heavily-biased corr-route value
    final List<Fact> overall = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      overall.add(completed(900L + i * 2L));
    }
    for (int i = 0; i < 10; i++) {
      overall.add(completed(50_000L));
    }
    ServingTestSupport.seed(fixture, "process-duration", "percentiles", overall, PROCESS);
    final List<Fact> manual = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      manual.add(routeCompleted(49_000L + i * 100L, "manual"));
    }
    ServingTestSupport.seed(fixture, "corr-route", "duration_p", manual, PROCESS, "manual");

    // when the endpoint is called directly
    final List<VariableCorrelation> correlations =
        controller.variableCorrelation(PROCESS, null, null);

    // then it serves the biased value with lift above 1
    assertThat(correlations).isNotEmpty();
    assertThat(correlations.get(0).lift()).isGreaterThan(1.0);
  }

  @Test
  void shouldServeAnEmptyCorrelationListForAnUnknownProcess() {
    assertThat(controller.variableCorrelation("unknown", null, null)).isEmpty();
  }

  @Test
  void shouldRejectAReversedRangeOnCorrelationTooLikeEveryNeighboringEndpoint() {
    assertThatThrownBy(() -> controller.variableCorrelation(PROCESS, 10_000L, 5_000L))
        .isInstanceOf(IllegalArgumentException.class);
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

  private static Fact elementCompleted(final long durationMs) {
    return Fact.builder(FactType.ELEMENT)
        .transition(Transition.COMPLETED)
        .field("durationMs", durationMs)
        .build();
  }
}
