/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.dashboard;

import static io.camunda.optimize.BusinessValueInstanceFixtures.bvdInstanceWithDuration;
import static io.camunda.optimize.service.dashboard.AgenticReportFilters.noExtraFilters;
import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.DURATION_AVG_TOTAL_REPORT_ID;
import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.DURATION_BY_DATE_REPORT_ID;
import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.DURATION_BY_PROCESS_REPORT_ID;
import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.DURATION_PERCENTILES_REPORT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import io.camunda.optimize.service.report.ReportEvaluationService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Evaluates the L0-cycleTime / L1-overview duration tiles. Covers average/percentile aggregation
 * over synthetic completed instances so a regression in {@code PROCESS_INSTANCE DURATION} view math
 * fails a targeted assertion.
 */
class BusinessValueCycleTimeTilesIT extends AbstractBrokerlessZeebeCCSMIT {

  private AgenticReportEvaluator reports;

  @BeforeEach
  void setUp() {
    embeddedOptimizeExtension.getBean(BusinessValueDashboardService.class).reconcile();
    reports =
        new AgenticReportEvaluator(
            embeddedOptimizeExtension.getBean(ReportEvaluationService.class));
  }

  @Test
  void shouldReturnArithmeticMeanForDurationAvgTotal() {
    // given three completed instances with distinct durations
    final String procKey = "cyc-avg-total";
    persistProcessInstances(
        List.of(
            bvdInstanceWithDuration(procKey, 1_000L).build(),
            bvdInstanceWithDuration(procKey, 3_000L).build(),
            bvdInstanceWithDuration(procKey, 5_000L).build()));

    // when evaluating the aggregate cycle-time tile
    // then the AVERAGE aggregation returns the arithmetic mean
    assertThat(reports.evaluateNumber(DURATION_AVG_TOTAL_REPORT_ID, noExtraFilters()))
        .isEqualTo(3_000.0);
  }

  @Test
  void shouldReturnPerProcessAverageDurationsSortedDesc() {
    // given three processes with clearly separated averages
    final String slow = "cyc-proc-slow";
    final String mid = "cyc-proc-mid";
    final String fast = "cyc-proc-fast";
    persistProcessInstances(
        List.of(
            bvdInstanceWithDuration(slow, 10_000L).build(),
            bvdInstanceWithDuration(slow, 10_000L).build(),
            bvdInstanceWithDuration(mid, 5_000L).build(),
            bvdInstanceWithDuration(mid, 5_000L).build(),
            bvdInstanceWithDuration(fast, 1_000L).build()));

    // when evaluating the per-process duration tile
    final List<MapResultEntryDto> result =
        reports.evaluateMapData(DURATION_BY_PROCESS_REPORT_ID, noExtraFilters());

    // then all three processes appear with the expected averages, sorted DESC by duration
    assertThat(result)
        .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
        .containsExactly(tuple(slow, 10_000.0), tuple(mid, 5_000.0), tuple(fast, 1_000.0));
  }

  @Test
  void shouldEvaluatePercentilesTileEndToEnd() {
    // given 10 evenly-spaced durations so the AVG measure is deterministic (5_500 = mean of
    // 1..10 * 1_000). The tile is a NUMBER visualization with three multi-aggregation measures
    // (AVG, P50, P95); AgenticReportEvaluator#evaluateNumber returns the first measure (AVG).
    // Asserting on that value proves the percentile-configured tile evaluates end-to-end;
    // the P50/P95 measure shapes are covered by the unit tests on the aggregation classes.
    final String procKey = "cyc-percentiles";
    persistProcessInstances(
        java.util.stream.LongStream.rangeClosed(1, 10)
            .mapToObj(i -> bvdInstanceWithDuration(procKey, i * 1_000L).build())
            .toList());

    // then the AVG measure returns the arithmetic mean of the seeded durations
    assertThat(reports.evaluateNumber(DURATION_PERCENTILES_REPORT_ID, noExtraFilters()))
        .isEqualTo(5_500.0);
  }

  @Test
  void shouldReturnAverageDurationPerDateBucketForDurationByDateTile() {
    // given two completed instances that ended on separate days with distinct durations
    final String procKey = "cyc-by-date";
    final java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
    persistProcessInstances(
        List.of(
            bvdInstanceWithDuration(procKey, 2_000L)
                .startDate(now.minusDays(1).minusHours(1))
                .endDate(now.minusDays(1))
                .build(),
            bvdInstanceWithDuration(procKey, 6_000L)
                .startDate(now.minusDays(2).minusHours(1))
                .endDate(now.minusDays(2))
                .build()));

    // when evaluating the cycle-time history tile
    final List<MapResultEntryDto> result =
        reports.evaluateMapData(DURATION_BY_DATE_REPORT_ID, noExtraFilters());

    // then the non-null buckets carry the two seeded per-day averages — asserting on the values
    // themselves (rather than bucket labels) so the test survives AUTOMATIC unit choices
    assertThat(
            result.stream()
                .map(MapResultEntryDto::getValue)
                .filter(java.util.Objects::nonNull)
                .toList())
        .containsExactlyInAnyOrder(2_000.0, 6_000.0);
  }
}
