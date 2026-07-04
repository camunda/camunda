/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.fact.Transition;
import io.camunda.analytics.webapp.ServingTestSupport.Fixture;
import io.camunda.analytics.webapp.dashboard.DashboardRepository;
import io.camunda.analytics.webapp.dashboard.DistinctPoint;
import io.camunda.analytics.webapp.dashboard.DurationPercentilePoint;
import io.camunda.analytics.webapp.dashboard.RatioPoint;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Read-path tests: the dashboard queries answered through the neutral serving executor. */
final class DashboardServingTest {

  private static final String PROCESS = "order-process";
  private static final String TENANT = "<default>";

  private Fixture fixture;
  private DashboardRepository repository;

  @BeforeEach
  void setUp() {
    fixture = ServingTestSupport.create();
    repository = new DashboardRepository(fixture.executor(), fixture.catalog());
  }

  @AfterEach
  void tearDown() {
    fixture.datasetStore().close();
    fixture.metadataStore().close();
  }

  @Test
  void shouldReturnDurationSummaryFromServingStore() {
    // given four completed instances with known durations folded into the percentile cube
    ServingTestSupport.seed(
        fixture,
        "process-duration",
        "p95",
        completed(100_000L, 200_000L, 300_000L, 400_000L),
        PROCESS);

    // when the KPI-tile summary reads the whole range
    final DurationPercentilePoint summary = repository.durationSummary(PROCESS, null, null);

    // then count / min / max are exact and the percentiles fall inside the observed range
    assertThat(summary.observationCount()).isEqualTo(4L);
    assertThat(summary.minMs()).isEqualTo(100_000L);
    assertThat(summary.maxMs()).isEqualTo(400_000L);
    assertThat(summary.p50Ms()).isBetween(100_000L, 400_000L);
    assertThat(summary.p90Ms()).isGreaterThanOrEqualTo(summary.p50Ms());
  }

  @Test
  void shouldReturnSlaRatioFromServingStore() {
    // given four completed instances, three within the 300s SLA threshold
    final long window =
        ServingTestSupport.seed(
            fixture,
            "process-sla",
            "sla_compliance",
            completed(100_000L, 200_000L, 300_000L, 400_000L),
            PROCESS);

    // when the SLA-met ratio series is read
    final List<RatioPoint> ratios = repository.ratios(PROCESS, "sla_met", null, null);

    // then it is matched=3 of total=4 for the seeded window
    assertThat(ratios)
        .singleElement()
        .satisfies(
            point -> {
              assertThat(point.windowStart()).isEqualTo(window);
              assertThat(point.matched()).isEqualTo(3L);
              assertThat(point.total()).isEqualTo(4L);
              assertThat(point.ratio()).isEqualTo(0.75);
              assertThat(point.maturing()).isFalse();
            });
  }

  @Test
  void shouldReturnUnmodeledRatioAsEmpty() {
    // given a seeded SLA cube but a metric with no declared dataset
    ServingTestSupport.seed(fixture, "process-sla", "sla_compliance", completed(100_000L), PROCESS);

    // when / then — no-incident has no cube, so it reads empty
    assertThat(repository.ratios(PROCESS, "no_incident", null, null)).isEmpty();
  }

  @Test
  void shouldReturnDistinctCountFromServingStore() {
    // given three distinct process definitions observed for the tenant
    final List<Fact> facts = new ArrayList<>();
    for (final String process : List.of("order-process", "payment-process", "shipping-process")) {
      facts.add(Fact.builder(FactType.PROCESS_INSTANCE).field("bpmnProcessId", process).build());
    }
    final long window =
        ServingTestSupport.seed(fixture, "process-distinct", "distinct", facts, TENANT);

    // when the distinct series is read
    final List<DistinctPoint> distinct = repository.distinct(TENANT, null, null);

    // then the estimate is three, inside its confidence interval, for the seeded window
    assertThat(distinct)
        .singleElement()
        .satisfies(
            point -> {
              assertThat(point.windowStart()).isEqualTo(window);
              assertThat(point.estimate()).isEqualTo(3L);
              assertThat(point.lowerBound()).isLessThanOrEqualTo(3L);
              assertThat(point.upperBound()).isGreaterThanOrEqualTo(3L);
            });
  }

  private static List<Fact> completed(final long... durationsMs) {
    final List<Fact> facts = new ArrayList<>();
    for (final long duration : durationsMs) {
      facts.add(
          Fact.builder(FactType.PROCESS_INSTANCE)
              .transition(Transition.COMPLETED)
              .field("durationMs", duration)
              .build());
    }
    return facts;
  }
}
