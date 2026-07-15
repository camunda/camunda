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
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.fact.Transition;
import io.camunda.analytics.query.SnapshotQueryExecutor;
import io.camunda.analytics.query.TableQueryExecutor;
import io.camunda.analytics.webapp.ServingTestSupport.Fixture;
import io.camunda.analytics.webapp.dashboard.DashboardRepository;
import io.camunda.analytics.webapp.dashboard.DurationPercentilePoint;
import io.camunda.analytics.webapp.dashboard.KpiComparison;
import io.camunda.analytics.webapp.dashboard.PercentileComparison;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The period-over-period comparison reads: the same whole-range aggregation over {@code [from, to)}
 * and over the immediately preceding same-length range, plus the percentile-trend overlay whose
 * previous points ride the current period's time grid.
 */
final class DashboardComparisonServingTest {

  private static final String PROCESS = "order-process";
  private static final long MINUTE = 60_000L;

  private Fixture fixture;
  private DashboardRepository repository;

  /** The current period: exactly the one aligned minute window the "current" seeds land in. */
  private long from;

  private long to;

  /** The previous period's window start ({@code from − P}, {@code P} = one window here). */
  private long previousWindow;

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
    from = ServingTestSupport.window(MINUTE);
    to = from + MINUTE;
    previousWindow = from - MINUTE;
  }

  @AfterEach
  void tearDown() {
    fixture.datasetStore().close();
    fixture.metadataStore().close();
  }

  @Test
  void shouldAggregateBothPeriodsOfTheKpiComparison() {
    // given a current window with 3 started / 2 completed / 1 terminated and a previous window
    // with 1 started / 1 completed
    ServingTestSupport.seedAt(
        fixture, "process-instances", from, lifecycle(3, 2_000L, 20_000L), PROCESS);
    ServingTestSupport.seedAt(
        fixture, "process-instances", previousWindow, lifecycle(1, 5_000L), PROCESS);
    // and duration observations for both periods (current 100s/300s, previous 200s)
    ServingTestSupport.seedAt(
        fixture, "process-duration", from, completed(100_000L, 300_000L), PROCESS);
    ServingTestSupport.seedAt(
        fixture, "process-duration", previousWindow, completed(200_000L), PROCESS);
    // and SLA populations: current 1 of 2 within the 9s SLA, previous 1 of 1
    ServingTestSupport.seedAt(
        fixture, "process-quality", from, completed(4_000L, 20_000L), PROCESS);
    ServingTestSupport.seedAt(
        fixture, "process-quality", previousWindow, completed(5_000L), PROCESS);

    // when the comparison reads the current window as its range
    final KpiComparison comparison = repository.kpiComparison(PROCESS, from, to);

    // then the current side aggregates only the current window
    assertThat(comparison.current().activated()).isEqualTo(3L);
    assertThat(comparison.current().ended()).isEqualTo(3L); // 2 completed + 1 terminated
    assertThat(comparison.current().duration().observationCount()).isEqualTo(2L);
    assertThat(comparison.current().slaCompliance().matched()).isEqualTo(1L);
    assertThat(comparison.current().slaCompliance().total()).isEqualTo(2L);

    // and the previous side aggregates only the preceding same-length range
    assertThat(comparison.previous().activated()).isEqualTo(1L);
    assertThat(comparison.previous().ended()).isEqualTo(1L);
    assertThat(comparison.previous().duration().observationCount()).isEqualTo(1L);
    assertThat(comparison.previous().slaCompliance().ratio()).isEqualTo(1.0);
  }

  @Test
  void shouldReportAnEmptyPreviousPeriodAsZeroes() {
    // given data only in the current window
    ServingTestSupport.seedAt(fixture, "process-instances", from, lifecycle(2, 3_000L), PROCESS);

    // when the comparison reads
    final KpiComparison comparison = repository.kpiComparison(PROCESS, from, to);

    // then the previous side is all zeroes (the client hides its badges on a zero basis)
    assertThat(comparison.current().activated()).isEqualTo(2L);
    assertThat(comparison.previous().activated()).isZero();
    assertThat(comparison.previous().ended()).isZero();
    assertThat(comparison.previous().slaCompliance().total()).isZero();
  }

  @Test
  void shouldRetimestampThePreviousPercentileSeriesOntoTheCurrentGrid() {
    // given one duration window per period with distinct populations
    ServingTestSupport.seedAt(
        fixture, "process-duration", from, completed(100_000L, 300_000L), PROCESS);
    ServingTestSupport.seedAt(
        fixture, "process-duration", previousWindow, completed(200_000L), PROCESS);

    // when the overlay read runs
    final PercentileComparison comparison =
        repository.durationPercentilesCompare(PROCESS, from, to);

    // then the previous point is shifted by the period length onto the current grid, so both
    // series align on one time axis
    assertThat(comparison.current())
        .extracting(DurationPercentilePoint::windowStart, DurationPercentilePoint::observationCount)
        .containsExactly(tuple(from, 2L));
    assertThat(comparison.previous())
        .extracting(DurationPercentilePoint::windowStart, DurationPercentilePoint::observationCount)
        .containsExactly(tuple(from, 1L));
    assertThat(comparison.previous().get(0).minMs()).isEqualTo(200_000L);
  }

  @Test
  void shouldRejectAnEmptyComparisonRange() {
    // given / when / then — a zero-length range has no "previous period"
    assertThatThrownBy(() -> repository.kpiComparison(PROCESS, from, from))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> repository.durationPercentilesCompare(PROCESS, to, from))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** {@code started} activations plus one COMPLETED fact per given duration. */
  private static List<Fact> lifecycle(final int started, final long... completedDurationsMs) {
    final List<Fact> facts = new ArrayList<>();
    for (int i = 0; i < started; i++) {
      facts.add(Fact.builder(FactType.PROCESS_INSTANCE).transition(Transition.ACTIVATED).build());
    }
    facts.addAll(completed(completedDurationsMs));
    if (completedDurationsMs.length < started) {
      // one terminated instance closes the books for the current-window fixture
      facts.add(Fact.builder(FactType.PROCESS_INSTANCE).transition(Transition.TERMINATED).build());
    }
    return facts;
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
