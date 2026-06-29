/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.dashboard;

import static io.camunda.optimize.AgenticInstanceFixtures.PROC_KEY;
import static io.camunda.optimize.AgenticInstanceFixtures.agenticInstanceWithDuration;
import static io.camunda.optimize.AgenticInstanceFixtures.agenticInstanceWithTokens;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.DURATION_STABILITY_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.KPI_AVG_DURATION_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.KPI_DURATION_P50_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.KPI_DURATION_P95_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticReportFilters.noExtraFilters;
import static io.camunda.optimize.service.dashboard.AgenticReportFilters.rollingEndDateFilter;
import static io.camunda.optimize.service.dashboard.AgenticReportFilters.withDefinitions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.result.MeasureDto;
import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import io.camunda.optimize.service.db.report.result.MapCommandResult;
import io.camunda.optimize.service.report.ReportEvaluationService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AgenticDurationKpiTilesIT extends AbstractBrokerlessZeebeCCSMIT {

  private AgenticReportEvaluator reports;

  @BeforeEach
  void setUp() {
    embeddedOptimizeExtension.getBean(AgenticControlDashboardService.class).reconcile();
    reports =
        new AgenticReportEvaluator(
            embeddedOptimizeExtension.getBean(ReportEvaluationService.class));
  }

  @Test
  void shouldComputeAverageDurationOfCompletedAgenticInstances() {
    // durations: 1 h and 3 h → avg = 2 h = 7_200_000 ms
    final ProcessInstanceDto inst1 =
        agenticInstanceWithTokens(PROC_KEY, 0L, 0L).duration(3_600_000L).build();
    final ProcessInstanceDto inst2 =
        agenticInstanceWithTokens(PROC_KEY, 0L, 0L).duration(10_800_000L).build();

    persistProcessInstances(List.of(inst1, inst2));

    final Double result = reports.evaluateNumber(KPI_AVG_DURATION_REPORT_ID, noExtraFilters());
    assertThat(result).isCloseTo(7_200_000.0, within(1.0));
  }

  @ParameterizedTest
  @MethodSource("durationPercentileReportIds")
  void shouldComputeDurationPercentileOfCompletedAgenticInstances(
      final String reportId, final double expectedValue, final double tolerance) {
    // durations: 1h, 2h, 3h, 4h, 5h
    final ProcessInstanceDto inst1 =
        agenticInstanceWithTokens(PROC_KEY, 0L, 0L).duration(3_600_000L).build();
    final ProcessInstanceDto inst2 =
        agenticInstanceWithTokens(PROC_KEY, 0L, 0L).duration(7_200_000L).build();
    final ProcessInstanceDto inst3 =
        agenticInstanceWithTokens(PROC_KEY, 0L, 0L).duration(10_800_000L).build();
    final ProcessInstanceDto inst4 =
        agenticInstanceWithTokens(PROC_KEY, 0L, 0L).duration(14_400_000L).build();
    final ProcessInstanceDto inst5 =
        agenticInstanceWithTokens(PROC_KEY, 0L, 0L).duration(18_000_000L).build();

    persistProcessInstances(List.of(inst1, inst2, inst3, inst4, inst5));

    final Double result = reports.evaluateNumber(reportId, noExtraFilters());
    // ES percentile approximation
    assertThat(result).isCloseTo(expectedValue, within(tolerance));
  }

  @Test
  void shouldApplyDateFilterToAvgDuration() {
    // within window: duration 1h
    final ProcessInstanceDto recent =
        agenticInstanceWithTokens(PROC_KEY, 0L, 0L)
            .startDate(OffsetDateTime.now().minusHours(3))
            .endDate(OffsetDateTime.now().minusHours(2))
            .duration(3_600_000L)
            .build();
    // outside window: duration 5h — should not skew the avg
    final ProcessInstanceDto old =
        agenticInstanceWithTokens(PROC_KEY, 0L, 0L)
            .startDate(OffsetDateTime.now().minusDays(10))
            .endDate(OffsetDateTime.now().minusDays(9))
            .duration(18_000_000L)
            .build();

    persistProcessInstances(List.of(recent, old));

    assertThat(
            reports.evaluateNumber(
                KPI_AVG_DURATION_REPORT_ID, rollingEndDateFilter(1L, DateUnit.DAYS)))
        .isCloseTo(3_600_000.0, within(1.0));
  }

  @Test
  void shouldApplyDateFilterToP50Duration() {
    // within window: duration 1h
    final ProcessInstanceDto recent =
        agenticInstanceWithTokens(PROC_KEY, 0L, 0L)
            .startDate(OffsetDateTime.now().minusHours(3))
            .endDate(OffsetDateTime.now().minusHours(2))
            .duration(3_600_000L)
            .build();
    // outside window: duration 5h — should not affect P50
    final ProcessInstanceDto old =
        agenticInstanceWithTokens(PROC_KEY, 0L, 0L)
            .startDate(OffsetDateTime.now().minusDays(10))
            .endDate(OffsetDateTime.now().minusDays(9))
            .duration(18_000_000L)
            .build();

    persistProcessInstances(List.of(recent, old));

    assertThat(
            reports.evaluateNumber(
                KPI_DURATION_P50_REPORT_ID, rollingEndDateFilter(1L, DateUnit.DAYS)))
        .isCloseTo(3_600_000.0, within(1.0));
  }

  @Test
  void shouldApplyDateFilterToP95Duration() {
    // within window: duration 1h
    final ProcessInstanceDto recent =
        agenticInstanceWithTokens(PROC_KEY, 0L, 0L)
            .startDate(OffsetDateTime.now().minusHours(3))
            .endDate(OffsetDateTime.now().minusHours(2))
            .duration(3_600_000L)
            .build();
    // outside window: duration 5h — should not affect P95
    final ProcessInstanceDto old =
        agenticInstanceWithTokens(PROC_KEY, 0L, 0L)
            .startDate(OffsetDateTime.now().minusDays(10))
            .endDate(OffsetDateTime.now().minusDays(9))
            .duration(18_000_000L)
            .build();

    persistProcessInstances(List.of(recent, old));

    assertThat(
            reports.evaluateNumber(
                KPI_DURATION_P95_REPORT_ID, rollingEndDateFilter(1L, DateUnit.DAYS)))
        .isCloseTo(3_600_000.0, within(1.0));
  }

  @Test
  void shouldApplyDefinitionFilterToAvgDuration() {
    final String procKeyA = "proc-dur-a";
    final String procKeyB = "proc-dur-b";

    // procKeyA: durations 1h and 3h → avg 2h
    // procKeyB: duration 10h — should be excluded
    persistProcessInstances(
        List.of(
            agenticInstanceWithTokens(procKeyA, 0L, 0L).duration(3_600_000L).build(),
            agenticInstanceWithTokens(procKeyA, 0L, 0L).duration(10_800_000L).build(),
            agenticInstanceWithTokens(procKeyB, 0L, 0L).duration(36_000_000L).build()));

    assertThat(
            reports.evaluateNumber(
                KPI_AVG_DURATION_REPORT_ID,
                withDefinitions(List.of(new ReportDataDefinitionDto(procKeyA)))))
        .isCloseTo(7_200_000.0, within(1.0));
  }

  @Test
  void shouldApplyDefinitionFilterToP50Duration() {
    final String procKeyA = "proc-p50-dur-a";
    final String procKeyB = "proc-p50-dur-b";

    // procKeyA: durations 1h and 3h → P50 = 2h = 7_200_000 ms
    // procKeyB: duration 10h — should be excluded
    persistProcessInstances(
        List.of(
            agenticInstanceWithDuration(procKeyA, 3_600_000L).build(),
            agenticInstanceWithDuration(procKeyA, 10_800_000L).build(),
            agenticInstanceWithDuration(procKeyB, 36_000_000L).build()));

    assertThat(
            reports.evaluateNumber(
                KPI_DURATION_P50_REPORT_ID,
                withDefinitions(List.of(new ReportDataDefinitionDto(procKeyA)))))
        .isCloseTo(7_200_000.0, within(10_000.0));
  }

  @Test
  void shouldApplyDefinitionFilterToP95Duration() {
    final String procKeyA = "proc-p95-dur-a";
    final String procKeyB = "proc-p95-dur-b";

    // procKeyA: durations 1h and 3h → P95 ≈ 3h = 10_800_000 ms
    // procKeyB: duration 10h — should be excluded
    persistProcessInstances(
        List.of(
            agenticInstanceWithDuration(procKeyA, 3_600_000L).build(),
            agenticInstanceWithDuration(procKeyA, 10_800_000L).build(),
            agenticInstanceWithDuration(procKeyB, 36_000_000L).build()));

    assertThat(
            reports.evaluateNumber(
                KPI_DURATION_P95_REPORT_ID,
                withDefinitions(List.of(new ReportDataDefinitionDto(procKeyA)))))
        .isCloseTo(10_800_000.0, within(1_000_000.0));
  }

  @Test
  void shouldReturnSingleBucketWithBothAggregations_whenAllInstancesShareEndDate() {
    // given — 5 instances all ending at the same point in time → AUTOMATIC bucketing produces 1
    // bucket; durations [1h, 2h, 3h, 4h, 5h] → P50 = 3h, P95 ≈ 5h
    final OffsetDateTime sharedEnd = OffsetDateTime.now().minusHours(1);
    final long oneHour = 3_600_000L;
    persistProcessInstances(
        List.of(
            agenticInstanceWithDuration(PROC_KEY, oneHour).endDate(sharedEnd).build(),
            agenticInstanceWithDuration(PROC_KEY, 2 * oneHour).endDate(sharedEnd).build(),
            agenticInstanceWithDuration(PROC_KEY, 3 * oneHour).endDate(sharedEnd).build(),
            agenticInstanceWithDuration(PROC_KEY, 4 * oneHour).endDate(sharedEnd).build(),
            agenticInstanceWithDuration(PROC_KEY, 5 * oneHour).endDate(sharedEnd).build()));

    // when
    final MapCommandResult result =
        reports.evaluateMap(DURATION_STABILITY_REPORT_ID, noExtraFilters());

    // then — exactly two measures, ordered P50 then P95
    final List<MeasureDto<List<MapResultEntryDto>>> measures = result.getMeasures();
    assertThat(measures).hasSize(2);

    final MeasureDto<List<MapResultEntryDto>> p50Measure = measures.get(0);
    assertThat(p50Measure.getAggregationType())
        .isEqualTo(new AggregationDto(AggregationType.PERCENTILE, 50.0));
    assertThat(p50Measure.getData()).hasSize(1);
    assertThat(p50Measure.getData().getFirst().getValue())
        .isCloseTo(3 * oneHour * 1.0, within((double) oneHour)); // 3h ± 1h tolerance

    final MeasureDto<List<MapResultEntryDto>> p95Measure = measures.get(1);
    assertThat(p95Measure.getAggregationType())
        .isEqualTo(new AggregationDto(AggregationType.PERCENTILE, 95.0));
    assertThat(p95Measure.getData()).hasSize(1);
    assertThat(p95Measure.getData().getFirst().getValue())
        .isCloseTo(5 * oneHour * 1.0, within((double) oneHour)); // 5h ± 1h tolerance
  }

  @Test
  void shouldReturnMultipleBuckets_whenInstancesEndInDifferentMonths() {
    // given — two instances ending 60 days apart; AUTOMATIC bucketing over a 60-day range produces
    // at least 2 buckets (weekly or monthly granularity)
    final long oneHour = 3_600_000L;
    persistProcessInstances(
        List.of(
            agenticInstanceWithDuration(PROC_KEY, oneHour)
                .startDate(OffsetDateTime.now().minusDays(61))
                .endDate(OffsetDateTime.now().minusDays(60))
                .build(),
            agenticInstanceWithDuration(PROC_KEY, 3 * oneHour)
                .startDate(OffsetDateTime.now().minusDays(2))
                .endDate(OffsetDateTime.now().minusDays(1))
                .build()));

    // when
    final MapCommandResult result =
        reports.evaluateMap(DURATION_STABILITY_REPORT_ID, noExtraFilters());

    // then — both measures have at least 2 distinct time buckets
    final List<MeasureDto<List<MapResultEntryDto>>> measures = result.getMeasures();
    assertThat(measures).hasSize(2);
    assertThat(measures.get(0).getData()).hasSizeGreaterThanOrEqualTo(2);
    assertThat(measures.get(1).getData()).hasSizeGreaterThanOrEqualTo(2);
  }

  @Test
  void shouldHandleLargeDurationValuesWithoutOverflow() {
    // given — duration of ~115 days (10^10 ms), exercising 64-bit long handling
    final long largeDuration = 10_000_000_000L;
    final OffsetDateTime endDate = OffsetDateTime.now().minusHours(1);
    persistProcessInstances(
        List.of(agenticInstanceWithDuration(PROC_KEY, largeDuration).endDate(endDate).build()));

    // when
    final MapCommandResult result =
        reports.evaluateMap(DURATION_STABILITY_REPORT_ID, noExtraFilters());

    // then — no exception; the single bucket value is close to the expected duration
    final List<MeasureDto<List<MapResultEntryDto>>> measures = result.getMeasures();
    assertThat(measures).hasSize(2);
    measures.forEach(
        m -> {
          assertThat(m.getData()).hasSize(1);
          final Double value = m.getData().getFirst().getValue();
          assertThat(value).isNotNull().isFinite();
          // TDigest P50/P95 of a single data point equals that point
          assertThat(value).isCloseTo((double) largeDuration, within(largeDuration * 0.01));
        });
  }

  @Test
  void shouldExcludeNonAgenticInstances_fromDurationStabilityReport() {
    // given — one matching agentic+completed instance, plus one without agent instances
    final OffsetDateTime endDate = OffsetDateTime.now().minusHours(1);
    final long oneHour = 3_600_000L;
    final ProcessInstanceDto agentic =
        agenticInstanceWithDuration(PROC_KEY, oneHour).endDate(endDate).build();
    final ProcessInstanceDto nonAgentic = completedInstance(PROC_KEY).endDate(endDate).build();
    persistProcessInstances(List.of(agentic, nonAgentic));

    // when
    final MapCommandResult result =
        reports.evaluateMap(DURATION_STABILITY_REPORT_ID, noExtraFilters());

    // then — only 1 data point in each measure (the agentic instance)
    result.getMeasures().forEach(m -> assertThat(m.getData()).hasSize(1));
  }

  private static Stream<Arguments> durationPercentileReportIds() {
    return Stream.of(
        Arguments.of(KPI_DURATION_P50_REPORT_ID, 10_800_000.0, 10_000.0),
        Arguments.of(KPI_DURATION_P95_REPORT_ID, 18_000_000.0, 1_000_000.0));
  }
}
