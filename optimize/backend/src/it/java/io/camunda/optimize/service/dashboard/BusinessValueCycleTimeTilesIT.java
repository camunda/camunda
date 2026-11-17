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
import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
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
    // given two completed instances that ended on different days but share the same duration —
    // giving them the same duration means the bucket AVG equals that duration regardless of how
    // many buckets AggregateByDateUnit.AUTOMATIC picks: if AUTOMATIC splits them into per-day
    // buckets we get two buckets each averaging 4_000; if it collapses them into one wider
    // bucket the single average is still 4_000. Distinct durations would have made the assertion
    // brittle against AUTOMATIC unit changes.
    final String procKey = "cyc-by-date";
    final long duration = 4_000L;
    final java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
    persistProcessInstances(
        List.of(
            bvdInstanceWithDuration(procKey, duration)
                .startDate(now.minusDays(1).minusHours(1))
                .endDate(now.minusDays(1))
                .build(),
            bvdInstanceWithDuration(procKey, duration)
                .startDate(now.minusDays(2).minusHours(1))
                .endDate(now.minusDays(2))
                .build()));

    // when evaluating the cycle-time history tile
    final List<MapResultEntryDto> nonNullValues =
        reports.evaluateMapData(DURATION_BY_DATE_REPORT_ID, noExtraFilters()).stream()
            .filter(entry -> entry.getValue() != null)
            .toList();

    // then at least one bucket carries data and every non-null bucket returns the shared
    // per-instance duration — no assumption about how many buckets AUTOMATIC produced
    assertThat(nonNullValues).isNotEmpty();
    assertThat(nonNullValues)
        .extracting(MapResultEntryDto::getValue)
        .allMatch(value -> value == (double) duration);
  }

  @Test
  void shouldLabelPerProcessCycleTimeBarsWithTheProcessName() {
    // given a completed instance of a process whose BPMN id is not human-readable, plus a later
    // definition version carrying the name
    final String processKey = "order-fulfilment-v2";
    persistProcessInstances(List.of(bvdInstanceWithDuration(processKey, 5_000L).build()));
    persistProcessDefinitions(
        List.of(
            ProcessDefinitionOptimizeDto.builder()
                .id(processKey + ":2:2")
                .key(processKey)
                .version("2")
                .name("Order fulfilment")
                .dataSource(new ZeebeDataSourceDto("test-source", 1))
                .tenantId(ZEEBE_DEFAULT_TENANT_ID)
                .bpmn20Xml("<definitions/>")
                .build()));

    // when evaluating the per-process cycle time tile
    final List<MapResultEntryDto> result =
        reports.evaluateMapData(DURATION_BY_PROCESS_REPORT_ID, noExtraFilters());

    // then the bar is keyed by the BPMN process id and labelled with the process name
    assertThat(result)
        .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getLabel)
        .containsExactly(tuple(processKey, "Order fulfilment"));
  }
}
