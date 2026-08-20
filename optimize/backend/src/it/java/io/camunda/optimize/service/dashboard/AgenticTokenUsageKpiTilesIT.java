/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.dashboard;

import static io.camunda.optimize.AgenticInstanceFixtures.PROC_KEY;
import static io.camunda.optimize.AgenticInstanceFixtures.agenticInstanceWithTokens;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.KPI_AVG_TOKENS_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.KPI_MEDIAN_TOKENS_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.TOKEN_CONSUMERS_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.TOKEN_OUTLIER_BANDS_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.TOKEN_TREND_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticReportFilters.noExtraFilters;
import static io.camunda.optimize.service.dashboard.AgenticReportFilters.rollingEndDateFilter;
import static io.camunda.optimize.service.dashboard.AgenticReportFilters.withDefinitions;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.result.MeasureDto;
import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import io.camunda.optimize.service.db.report.result.MapCommandResult;
import io.camunda.optimize.service.exceptions.evaluation.ReportEvaluationException;
import io.camunda.optimize.service.report.ReportEvaluationService;
import java.time.OffsetDateTime;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgenticTokenUsageKpiTilesIT extends AbstractBrokerlessZeebeCCSMIT {

  private AgenticReportEvaluator reports;

  @BeforeEach
  void setUp() {
    embeddedOptimizeExtension.getBean(AgenticControlDashboardService.class).reconcile();
    reports =
        new AgenticReportEvaluator(
            embeddedOptimizeExtension.getBean(ReportEvaluationService.class));
  }

  @Test
  void shouldComputeAverageTokensPerExecution() {
    // totals: 150, 300, 450  → avg = 300
    final ProcessInstanceDto inst1 = agenticInstanceWithTokens(PROC_KEY, 100L, 50L).build();
    final ProcessInstanceDto inst2 = agenticInstanceWithTokens(PROC_KEY, 200L, 100L).build();
    final ProcessInstanceDto inst3 = agenticInstanceWithTokens(PROC_KEY, 300L, 150L).build();

    persistProcessInstances(List.of(inst1, inst2, inst3));

    final Double result = reports.evaluateNumber(KPI_AVG_TOKENS_REPORT_ID, noExtraFilters());
    assertThat(result).isCloseTo(300.0, within(1.0));
  }

  @Test
  void shouldComputeMedianTokensPerExecution() {
    // totals: 100, 200, 300, 400, 500 → median = 300
    final ProcessInstanceDto inst1 = agenticInstanceWithTokens(PROC_KEY, 70L, 30L).build();
    final ProcessInstanceDto inst2 = agenticInstanceWithTokens(PROC_KEY, 140L, 60L).build();
    final ProcessInstanceDto inst3 = agenticInstanceWithTokens(PROC_KEY, 210L, 90L).build();
    final ProcessInstanceDto inst4 = agenticInstanceWithTokens(PROC_KEY, 280L, 120L).build();
    final ProcessInstanceDto inst5 = agenticInstanceWithTokens(PROC_KEY, 350L, 150L).build();

    persistProcessInstances(List.of(inst1, inst2, inst3, inst4, inst5));

    final Double result = reports.evaluateNumber(KPI_MEDIAN_TOKENS_REPORT_ID, noExtraFilters());
    // ES percentile approximation — allow small delta
    assertThat(result).isCloseTo(300.0, within(10.0));
  }

  @Test
  void shouldAggregateMetricsWithinRollingDateFilter() {
    // both within last-1-week: avg tokens should reflect only these two
    final OffsetDateTime now = OffsetDateTime.now();
    final ProcessInstanceDto inst1 =
        agenticInstanceWithTokens(PROC_KEY, 100L, 100L)
            .startDate(now.minusDays(3))
            .endDate(now.minusDays(2))
            .build();
    final ProcessInstanceDto inst2 =
        agenticInstanceWithTokens(PROC_KEY, 200L, 200L)
            .startDate(now.minusDays(2))
            .endDate(now.minusDays(1))
            .build();
    // old instance with very different tokens — outside 1-week window
    final ProcessInstanceDto oldInst =
        agenticInstanceWithTokens(PROC_KEY, 10_000L, 10_000L)
            .startDate(now.minusDays(30))
            .endDate(now.minusDays(29))
            .build();

    persistProcessInstances(List.of(inst1, inst2, oldInst));

    // avg of 200 and 400 = 300
    assertThat(
            reports.evaluateNumber(
                KPI_AVG_TOKENS_REPORT_ID, rollingEndDateFilter(1L, DateUnit.WEEKS)))
        .isCloseTo(300.0, within(1.0));
  }

  @Test
  void shouldApplyDateFilterToMedianTokens() {
    // within window: totals 100, 200, 300 → median 200
    final ProcessInstanceDto inst1 =
        agenticInstanceWithTokens(PROC_KEY, 50L, 50L)
            .startDate(OffsetDateTime.now().minusDays(3))
            .endDate(OffsetDateTime.now().minusDays(2))
            .build();
    final ProcessInstanceDto inst2 =
        agenticInstanceWithTokens(PROC_KEY, 100L, 100L)
            .startDate(OffsetDateTime.now().minusDays(2))
            .endDate(OffsetDateTime.now().minusDays(1))
            .build();
    final ProcessInstanceDto inst3 =
        agenticInstanceWithTokens(PROC_KEY, 150L, 150L)
            .startDate(OffsetDateTime.now().minusDays(1))
            .endDate(OffsetDateTime.now().minusHours(1))
            .build();
    // outside window — very high total, should not affect median
    final ProcessInstanceDto old =
        agenticInstanceWithTokens(PROC_KEY, 10_000L, 10_000L)
            .startDate(OffsetDateTime.now().minusDays(30))
            .endDate(OffsetDateTime.now().minusDays(29))
            .build();

    persistProcessInstances(List.of(inst1, inst2, inst3, old));

    assertThat(
            reports.evaluateNumber(
                KPI_MEDIAN_TOKENS_REPORT_ID, rollingEndDateFilter(1L, DateUnit.WEEKS)))
        .isCloseTo(200.0, within(10.0));
  }

  @Test
  void shouldAggregateMetricsForSelectedProcessDefinition() {
    final String procKeyA = "proc-metrics-a";
    final String procKeyB = "proc-metrics-b";

    // procKeyA: input+output totals = 300 and 600 → avg = 450
    // procKeyB: total = 900 — should be excluded
    persistProcessInstances(
        List.of(
            agenticInstanceWithTokens(procKeyA, 100L, 200L).build(),
            agenticInstanceWithTokens(procKeyA, 200L, 400L).build(),
            agenticInstanceWithTokens(procKeyB, 300L, 600L).build()));

    assertThat(
            reports.evaluateNumber(
                KPI_AVG_TOKENS_REPORT_ID,
                withDefinitions(List.of(new ReportDataDefinitionDto(procKeyA)))))
        .isCloseTo(450.0, within(1.0));
  }

  @Test
  void shouldApplyDefinitionFilterToMedianTokens() {
    final String procKeyA = "proc-med-a";
    final String procKeyB = "proc-med-b";

    // procKeyA: totals 100, 200, 300 → median 200
    // procKeyB: total 50_000 — should be excluded
    persistProcessInstances(
        List.of(
            agenticInstanceWithTokens(procKeyA, 50L, 50L).build(),
            agenticInstanceWithTokens(procKeyA, 100L, 100L).build(),
            agenticInstanceWithTokens(procKeyA, 150L, 150L).build(),
            agenticInstanceWithTokens(procKeyB, 25_000L, 25_000L).build()));

    assertThat(
            reports.evaluateNumber(
                KPI_MEDIAN_TOKENS_REPORT_ID,
                withDefinitions(List.of(new ReportDataDefinitionDto(procKeyA)))))
        .isCloseTo(200.0, within(10.0));
  }

  @Test
  void shouldComputeWeeklyInputTokensGroupedByWeek() {
    // given two instances within the same week with known input tokens
    persistProcessInstances(
        List.of(
            agenticInstanceWithTokens(PROC_KEY, 100L, 50L)
                .startDate(OffsetDateTime.now().minusDays(3))
                .endDate(OffsetDateTime.now().minusDays(2))
                .build(),
            agenticInstanceWithTokens(PROC_KEY, 200L, 80L)
                .startDate(OffsetDateTime.now().minusDays(3))
                .endDate(OffsetDateTime.now().minusDays(2))
                .build()));

    // when evaluating the first measure (input tokens) of the token-trend report
    final List<MapResultEntryDto> buckets = reports.evaluateMapMeasure(TOKEN_TREND_REPORT_ID, 0);

    // then total input tokens across all buckets equals 300
    assertThat(buckets).isNotEmpty();
    final double totalInput = sumBuckets(buckets);
    assertThat(totalInput).isCloseTo(300.0, within(1.0));
  }

  @Test
  void shouldComputeWeeklyOutputTokensGroupedByWeek() {
    // given two instances within the same week with known output tokens
    persistProcessInstances(
        List.of(
            agenticInstanceWithTokens(PROC_KEY, 50L, 100L)
                .startDate(OffsetDateTime.now().minusDays(3))
                .endDate(OffsetDateTime.now().minusDays(2))
                .build(),
            agenticInstanceWithTokens(PROC_KEY, 80L, 200L)
                .startDate(OffsetDateTime.now().minusDays(3))
                .endDate(OffsetDateTime.now().minusDays(2))
                .build()));

    // when evaluating the second measure (output tokens) of the token-trend report
    final List<MapResultEntryDto> buckets = reports.evaluateMapMeasure(TOKEN_TREND_REPORT_ID, 1);

    // then total output tokens across all buckets equals 300
    assertThat(buckets).isNotEmpty();
    final double totalOutput = sumBuckets(buckets);
    assertThat(totalOutput).isCloseTo(300.0, within(1.0));
  }

  @Test
  void shouldApplyDefinitionFilterToTokenTrend() {
    final String procKeyA = "proc-trend-a";
    final String procKeyB = "proc-trend-b";

    // procKeyA: input 100 + 200 = 300 in the same week; procKeyB: input 999 — must be excluded
    persistProcessInstances(
        List.of(
            agenticInstanceWithTokens(procKeyA, 100L, 0L)
                .startDate(OffsetDateTime.now().minusDays(3))
                .endDate(OffsetDateTime.now().minusDays(2))
                .build(),
            agenticInstanceWithTokens(procKeyA, 200L, 0L)
                .startDate(OffsetDateTime.now().minusDays(3))
                .endDate(OffsetDateTime.now().minusDays(2))
                .build(),
            agenticInstanceWithTokens(procKeyB, 999L, 0L)
                .startDate(OffsetDateTime.now().minusDays(3))
                .endDate(OffsetDateTime.now().minusDays(2))
                .build()));

    // the first command result of the trend report is the input-tokens series, scoped to procKeyA
    final double totalInput =
        sumBuckets(
            reports.evaluateMapData(
                TOKEN_TREND_REPORT_ID,
                withDefinitions(List.of(new ReportDataDefinitionDto(procKeyA)))));
    assertThat(totalInput).isCloseTo(300.0, within(1.0));
  }

  @Test
  void shouldApplyDateFilterToTokenTrend() {
    // within the last-1-day window: input 150
    final ProcessInstanceDto recent =
        agenticInstanceWithTokens(PROC_KEY, 150L, 0L)
            .startDate(OffsetDateTime.now().minusHours(3))
            .endDate(OffsetDateTime.now().minusHours(2))
            .build();
    // outside the window: input 999 — must not be summed into any bucket
    final ProcessInstanceDto old =
        agenticInstanceWithTokens(PROC_KEY, 999L, 0L)
            .startDate(OffsetDateTime.now().minusDays(10))
            .endDate(OffsetDateTime.now().minusDays(9))
            .build();

    persistProcessInstances(List.of(recent, old));

    final double totalInput =
        sumBuckets(
            reports.evaluateMapData(
                TOKEN_TREND_REPORT_ID, rollingEndDateFilter(1L, DateUnit.DAYS)));
    assertThat(totalInput).isCloseTo(150.0, within(1.0));
  }

  @Test
  void shouldReturnThreePercentileMeasures_forTokenOutlierBands() {
    // given — 5 instances sharing an end date with total tokens [100, 200, 300, 400, 500]
    final OffsetDateTime sharedEnd = OffsetDateTime.now().minusDays(2);
    persistProcessInstances(
        List.of(
            agenticInstanceWithTokens(PROC_KEY, 100L, 0L).endDate(sharedEnd).build(),
            agenticInstanceWithTokens(PROC_KEY, 200L, 0L).endDate(sharedEnd).build(),
            agenticInstanceWithTokens(PROC_KEY, 300L, 0L).endDate(sharedEnd).build(),
            agenticInstanceWithTokens(PROC_KEY, 400L, 0L).endDate(sharedEnd).build(),
            agenticInstanceWithTokens(PROC_KEY, 500L, 0L).endDate(sharedEnd).build()));

    // when
    final MapCommandResult result =
        reports.evaluateMap(TOKEN_OUTLIER_BANDS_REPORT_ID, noExtraFilters());

    // then — exactly three measures, ordered p5, p50, p95
    final List<MeasureDto<List<MapResultEntryDto>>> measures = result.getMeasures();
    assertThat(measures).hasSize(3);
    assertThat(measures.get(0).getAggregationType())
        .isEqualTo(new AggregationDto(AggregationType.PERCENTILE, 5.0));
    assertThat(measures.get(1).getAggregationType())
        .isEqualTo(new AggregationDto(AggregationType.PERCENTILE, 50.0));
    assertThat(measures.get(2).getAggregationType())
        .isEqualTo(new AggregationDto(AggregationType.PERCENTILE, 95.0));

    // and the p50 band reflects the median total tokens (300) — ES percentile approximation
    assertThat(measures.get(1).getData().getFirst().getValue()).isCloseTo(300.0, within(50.0));
  }

  @Test
  void shouldApplyDefinitionFilterToTokenOutlierBands() {
    final String procKeyA = "proc-bands-a";
    final String procKeyB = "proc-bands-b";
    final OffsetDateTime sharedEnd = OffsetDateTime.now().minusDays(2);

    // procKeyA: total tokens [100, 200, 300]; procKeyB: a 9000-token outlier that would inflate the
    // p95 band if the definition filter were not applied
    persistProcessInstances(
        List.of(
            agenticInstanceWithTokens(procKeyA, 100L, 0L).endDate(sharedEnd).build(),
            agenticInstanceWithTokens(procKeyA, 200L, 0L).endDate(sharedEnd).build(),
            agenticInstanceWithTokens(procKeyA, 300L, 0L).endDate(sharedEnd).build(),
            agenticInstanceWithTokens(procKeyB, 9000L, 0L).endDate(sharedEnd).build()));

    final MapCommandResult result =
        reports.evaluateMap(
            TOKEN_OUTLIER_BANDS_REPORT_ID,
            withDefinitions(List.of(new ReportDataDefinitionDto(procKeyA))));

    final List<MeasureDto<List<MapResultEntryDto>>> measures = result.getMeasures();
    assertThat(measures).hasSize(3);
    // p50 ≈ 200 (median of A) and p95 stays well below the excluded 9000-token outlier
    assertThat(measures.get(1).getData().getFirst().getValue()).isCloseTo(200.0, within(50.0));
    assertThat(measures.get(2).getData().getFirst().getValue()).isLessThan(1000.0);
  }

  @Test
  void shouldRankProcessesByTotalTokensConsumed() {
    final String heavyProc = "heavy-agent-process";
    final String lightProc = "light-agent-process";

    // heavyProc consumes 450 tokens total, lightProc consumes 120
    persistProcessInstances(
        List.of(
            agenticInstanceWithTokens(heavyProc, 200L, 100L).build(),
            agenticInstanceWithTokens(heavyProc, 100L, 50L).build(),
            agenticInstanceWithTokens(lightProc, 80L, 40L).build()));

    // when evaluating the top-consumers report grouped by process definition key
    final List<MapResultEntryDto> buckets =
        reports.evaluateMapMeasure(TOKEN_CONSUMERS_REPORT_ID, 0);

    // then each process appears with its summed total tokens, ranked with the heaviest first
    assertThat(buckets)
        .filteredOn(e -> e.getValue() != null)
        .extracting(MapResultEntryDto::getKey)
        .containsExactly(heavyProc, lightProc);
    assertThat(buckets.stream().filter(e -> heavyProc.equals(e.getKey())).findFirst())
        .hasValueSatisfying(e -> assertThat(e.getValue()).isCloseTo(450.0, within(1.0)));
    assertThat(buckets.stream().filter(e -> lightProc.equals(e.getKey())).findFirst())
        .hasValueSatisfying(e -> assertThat(e.getValue()).isCloseTo(120.0, within(1.0)));
  }

  @Test
  void shouldLabelTopTokenConsumersWithLatestVersionProcessName() {
    // given agentic instances of a process whose (BPMN) key is not human-readable
    final String processKey = "invoice-agent-process";
    persistProcessInstances(
        List.of(
            agenticInstanceWithTokens(processKey, 200L, 100L).build(),
            agenticInstanceWithTokens(processKey, 100L, 50L).build()));
    // and a later version of that definition carrying a human-readable name (the auto-seeded v1
    // definition defaults its name to the key, so the name must come from the latest version)
    persistProcessDefinitions(
        List.of(
            ProcessDefinitionOptimizeDto.builder()
                .id(processKey + ":2:2")
                .key(processKey)
                .version("2")
                .name("Invoice Approval Agent")
                .dataSource(new ZeebeDataSourceDto("test-source", 1))
                .tenantId(ZEEBE_DEFAULT_TENANT_ID)
                .bpmn20Xml("<definitions/>")
                .build()));

    // when evaluating the top-consumers tile
    final List<MapResultEntryDto> buckets =
        reports.evaluateMapMeasure(TOKEN_CONSUMERS_REPORT_ID, 0);

    // then the bar is still keyed by the BPMN process id but labelled with the latest-version name
    assertThat(buckets)
        .filteredOn(e -> e.getValue() != null)
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.getKey()).isEqualTo(processKey);
              assertThat(e.getLabel()).isEqualTo("Invoice Approval Agent");
            });
  }

  @Test
  void shouldApplyDateFilterToTopTokenConsumers() {
    final String recentProc = "recent-agent-process";
    final String oldProc = "old-agent-process";

    // recentProc (within the last-1-day window) consumes 300 tokens
    // oldProc (10 days ago) consumes 999 tokens — would top the ranking if not filtered out
    persistProcessInstances(
        List.of(
            agenticInstanceWithTokens(recentProc, 300L, 0L)
                .startDate(OffsetDateTime.now().minusHours(3))
                .endDate(OffsetDateTime.now().minusHours(2))
                .build(),
            agenticInstanceWithTokens(oldProc, 999L, 0L)
                .startDate(OffsetDateTime.now().minusDays(10))
                .endDate(OffsetDateTime.now().minusDays(9))
                .build()));

    final List<MapResultEntryDto> buckets =
        reports.evaluateMapData(TOKEN_CONSUMERS_REPORT_ID, rollingEndDateFilter(1L, DateUnit.DAYS));

    // only the in-window process is ranked; the older heavier consumer is excluded
    final List<MapResultEntryDto> ranked =
        buckets.stream().filter(e -> e.getValue() != null).toList();
    assertThat(ranked).extracting(MapResultEntryDto::getKey).containsExactly(recentProc);
    assertThat(ranked.getFirst().getValue()).isCloseTo(300.0, within(1.0));
  }

  @Test
  void shouldReturnOnlyTopConsumersWithTotalCountWhenPaginated() {
    // given three processes with distinct total token sums
    persistProcessInstances(
        List.of(
            agenticInstanceWithTokens("proc-a", 300L, 0L).build(),
            agenticInstanceWithTokens("proc-b", 200L, 0L).build(),
            agenticInstanceWithTokens("proc-c", 100L, 0L).build()));

    // when evaluating the top-consumers report limited to the two heaviest processes
    // (limit only, no offset — mirroring how the dashboard frontend requests the tile)
    final CommandEvaluationResult<?> commandResult =
        reports.evaluatePaginated(
            TOKEN_CONSUMERS_REPORT_ID, noExtraFilters(), new PaginationDto(2, null));
    final List<MapResultEntryDto> buckets =
        ((MapCommandResult) commandResult).getMeasures().getFirst().getData();

    // then only the two heaviest processes are returned, ranked descending
    assertThat(buckets)
        .filteredOn(e -> e.getValue() != null)
        .extracting(MapResultEntryDto::getKey)
        .containsExactly("proc-a", "proc-b");
    // and the total distinct process count is surfaced for the "top N of total" label
    assertThat(commandResult.getPagination().getTotal()).isEqualTo(3L);
    // and the pagination is valid, so it survives REST mapping and the total reaches the frontend
    assertThat(commandResult.getPagination().isValid()).isTrue();
  }

  @Test
  void shouldRejectNonZeroOffsetForGroupedTopNReport() {
    // given a top-consumers report (grouped top-N supports a limit but not paging into results)
    persistProcessInstances(List.of(agenticInstanceWithTokens("proc-a", 300L, 0L).build()));

    // when evaluating it with a non-zero pagination offset
    final ThrowingCallable evaluation =
        () ->
            reports.evaluatePaginated(
                TOKEN_CONSUMERS_REPORT_ID, noExtraFilters(), new PaginationDto(2, 1));

    // then the request is rejected rather than silently returning a first page
    assertThatThrownBy(evaluation)
        .isInstanceOf(ReportEvaluationException.class)
        .hasMessageContaining("non-zero pagination offset");
  }

  @Test
  void shouldRejectNonZeroOffsetWithoutLimitForGroupedTopNReport() {
    // given a top-consumers report (grouped top-N supports a limit but not paging into results)
    persistProcessInstances(List.of(agenticInstanceWithTokens("proc-a", 300L, 0L).build()));

    // when evaluating it with a non-zero offset but no limit
    final ThrowingCallable evaluation =
        () ->
            reports.evaluatePaginated(
                TOKEN_CONSUMERS_REPORT_ID, noExtraFilters(), new PaginationDto(null, 1));

    // then the request is rejected rather than silently ignoring the offset
    assertThatThrownBy(evaluation)
        .isInstanceOf(ReportEvaluationException.class)
        .hasMessageContaining("non-zero pagination offset");
  }

  private static double sumBuckets(final List<MapResultEntryDto> buckets) {
    return buckets.stream()
        .filter(e -> e.getValue() != null)
        .mapToDouble(MapResultEntryDto::getValue)
        .sum();
  }
}
