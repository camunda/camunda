/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.dashboard;

import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.KPI_AVG_DURATION_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.KPI_AVG_TOKENS_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.KPI_COMPLETED_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.KPI_DURATION_P50_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.KPI_DURATION_P95_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.KPI_INCIDENT_RATE_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.KPI_MEDIAN_TOKENS_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.TOKEN_CONSUMERS_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.TOKEN_TREND_REPORT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.dto.optimize.ProcessInstanceConstants;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.persistence.incident.IncidentDto;
import io.camunda.optimize.dto.optimize.persistence.incident.IncidentStatus;
import io.camunda.optimize.dto.optimize.query.process.AgentInstanceDto;
import io.camunda.optimize.dto.optimize.query.process.AgentInstanceDto.AgentMetricsDto;
import io.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.SingleReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.RollingDateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceEndDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.result.MeasureDto;
import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import io.camunda.optimize.service.db.report.result.MapCommandResult;
import io.camunda.optimize.service.db.report.result.NumberCommandResult;
import io.camunda.optimize.service.report.ReportEvaluationService;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AgenticKpiTilesIT extends AbstractBrokerlessZeebeCCSMIT {

  private static final String PROC_KEY = "my-agent-process";
  private static final String USER_ID = "testUser";
  private static final ZoneId UTC = ZoneId.of("UTC");

  private ReportEvaluationService evaluationService;

  @BeforeEach
  void setUp() {
    embeddedOptimizeExtension.getBean(AgenticControlDashboardService.class).reconcile();
    evaluationService = embeddedOptimizeExtension.getBean(ReportEvaluationService.class);
  }

  // ---------------------------------------------------------------------------
  // Completed agent runs
  // ---------------------------------------------------------------------------

  @Test
  void shouldCountOnlyCompletedAgenticInstances() {
    final ProcessInstanceDto completed1 = agenticInstance(PROC_KEY, 100L, 50L).build();
    final ProcessInstanceDto completed2 = agenticInstance(PROC_KEY, 200L, 100L).build();
    // running instance — should not be counted
    final ProcessInstanceDto running =
        agenticInstance(PROC_KEY, 50L, 25L)
            .state(ProcessInstanceConstants.ACTIVE_STATE)
            .endDate(null)
            .duration(null)
            .build();
    // completed but no agent instances — should not be counted
    final ProcessInstanceDto nonAgentic = completedInstance(PROC_KEY).build();

    persistProcessInstances(List.of(completed1, completed2, running, nonAgentic));

    final Double result = evaluateNumber(KPI_COMPLETED_REPORT_ID, noExtraFilters());
    assertThat(result).isEqualTo(2.0);
  }

  // ---------------------------------------------------------------------------
  // Average duration
  // ---------------------------------------------------------------------------

  @Test
  void shouldComputeAverageDurationOfCompletedAgenticInstances() {
    // durations: 1 h and 3 h → avg = 2 h = 7_200_000 ms
    final ProcessInstanceDto inst1 = agenticInstance(PROC_KEY, 0L, 0L).duration(3_600_000L).build();
    final ProcessInstanceDto inst2 =
        agenticInstance(PROC_KEY, 0L, 0L).duration(10_800_000L).build();

    persistProcessInstances(List.of(inst1, inst2));

    final Double result = evaluateNumber(KPI_AVG_DURATION_REPORT_ID, noExtraFilters());
    assertThat(result).isCloseTo(7_200_000.0, within(1.0));
  }

  // ---------------------------------------------------------------------------
  // Percentile durations
  // ---------------------------------------------------------------------------

  @ParameterizedTest
  @MethodSource("durationPercentileReportIds")
  void shouldComputeDurationPercentileOfCompletedAgenticInstances(
      final String reportId, final double expectedValue, final double tolerance) {
    // durations: 1h, 2h, 3h, 4h, 5h
    final ProcessInstanceDto inst1 = agenticInstance(PROC_KEY, 0L, 0L).duration(3_600_000L).build();
    final ProcessInstanceDto inst2 = agenticInstance(PROC_KEY, 0L, 0L).duration(7_200_000L).build();
    final ProcessInstanceDto inst3 =
        agenticInstance(PROC_KEY, 0L, 0L).duration(10_800_000L).build();
    final ProcessInstanceDto inst4 =
        agenticInstance(PROC_KEY, 0L, 0L).duration(14_400_000L).build();
    final ProcessInstanceDto inst5 =
        agenticInstance(PROC_KEY, 0L, 0L).duration(18_000_000L).build();

    persistProcessInstances(List.of(inst1, inst2, inst3, inst4, inst5));

    final Double result = evaluateNumber(reportId, noExtraFilters());
    // ES percentile approximation
    assertThat(result).isCloseTo(expectedValue, within(tolerance));
  }

  // ---------------------------------------------------------------------------
  // Incident rate (count of resolved incidents on completed agentic instances)
  // ---------------------------------------------------------------------------

  @Test
  void shouldCountResolvedIncidentsOnCompletedAgenticInstances() {
    final String instanceId1 = UUID.randomUUID().toString();
    final String instanceId2 = UUID.randomUUID().toString();

    // one resolved incident
    final IncidentDto resolved =
        IncidentDto.builder()
            .incidentStatus(IncidentStatus.RESOLVED)
            .processInstanceId(instanceId1)
            .build();
    final ProcessInstanceDto withIncident =
        agenticInstance(PROC_KEY, 100L, 50L)
            .processInstanceId(instanceId1)
            .incidents(List.of(resolved))
            .build();

    // no incidents
    final ProcessInstanceDto clean =
        agenticInstance(PROC_KEY, 100L, 50L).processInstanceId(instanceId2).build();

    // running instance with a resolved incident — must not be counted
    final String runningId = UUID.randomUUID().toString();
    final ProcessInstanceDto running =
        agenticInstance(PROC_KEY, 100L, 50L)
            .processInstanceId(runningId)
            .state(ProcessInstanceConstants.ACTIVE_STATE)
            .endDate(null)
            .duration(null)
            .incidents(
                List.of(
                    IncidentDto.builder()
                        .incidentStatus(IncidentStatus.RESOLVED)
                        .processInstanceId(runningId)
                        .build()))
            .build();

    persistProcessInstances(List.of(withIncident, clean, running));

    final Double result = evaluateNumber(KPI_INCIDENT_RATE_REPORT_ID, noExtraFilters());
    assertThat(result).isEqualTo(1.0);
  }

  // ---------------------------------------------------------------------------
  // Average tokens
  // ---------------------------------------------------------------------------

  @Test
  void shouldComputeAverageTokensPerExecution() {
    // totals: 150, 300, 450  → avg = 300
    final ProcessInstanceDto inst1 = agenticInstance(PROC_KEY, 100L, 50L).build();
    final ProcessInstanceDto inst2 = agenticInstance(PROC_KEY, 200L, 100L).build();
    final ProcessInstanceDto inst3 = agenticInstance(PROC_KEY, 300L, 150L).build();

    persistProcessInstances(List.of(inst1, inst2, inst3));

    final Double result = evaluateNumber(KPI_AVG_TOKENS_REPORT_ID, noExtraFilters());
    assertThat(result).isCloseTo(300.0, within(1.0));
  }

  // ---------------------------------------------------------------------------
  // Median tokens
  // ---------------------------------------------------------------------------

  @Test
  void shouldComputeMedianTokensPerExecution() {
    // totals: 100, 200, 300, 400, 500 → median = 300
    final ProcessInstanceDto inst1 = agenticInstance(PROC_KEY, 70L, 30L).build();
    final ProcessInstanceDto inst2 = agenticInstance(PROC_KEY, 140L, 60L).build();
    final ProcessInstanceDto inst3 = agenticInstance(PROC_KEY, 210L, 90L).build();
    final ProcessInstanceDto inst4 = agenticInstance(PROC_KEY, 280L, 120L).build();
    final ProcessInstanceDto inst5 = agenticInstance(PROC_KEY, 350L, 150L).build();

    persistProcessInstances(List.of(inst1, inst2, inst3, inst4, inst5));

    final Double result = evaluateNumber(KPI_MEDIAN_TOKENS_REPORT_ID, noExtraFilters());
    // ES percentile approximation — allow small delta
    assertThat(result).isCloseTo(300.0, within(10.0));
  }

  // ---------------------------------------------------------------------------
  // Date range filters
  // ---------------------------------------------------------------------------

  @Test
  void shouldCountAllInstancesWhenNoDateFilterApplied() {
    // instances spread across a wide range — all should be counted without a date filter
    final ProcessInstanceDto recent =
        agenticInstance(PROC_KEY, 100L, 50L)
            .startDate(OffsetDateTime.now().minusHours(1))
            .endDate(OffsetDateTime.now().minusMinutes(30))
            .build();
    final ProcessInstanceDto older =
        agenticInstance(PROC_KEY, 100L, 50L)
            .startDate(OffsetDateTime.now().minusDays(30))
            .endDate(OffsetDateTime.now().minusDays(29))
            .build();
    final ProcessInstanceDto oldest =
        agenticInstance(PROC_KEY, 100L, 50L)
            .startDate(OffsetDateTime.now().minusDays(365))
            .endDate(OffsetDateTime.now().minusDays(364))
            .build();

    persistProcessInstances(List.of(recent, older, oldest));

    assertThat(evaluateNumber(KPI_COMPLETED_REPORT_ID, noExtraFilters())).isEqualTo(3.0);
  }

  @Test
  void shouldApplyRollingHourDateFilter() {
    // ended 30 min ago — within last-2-hours window
    final ProcessInstanceDto withinWindow =
        agenticInstance(PROC_KEY, 100L, 50L)
            .startDate(OffsetDateTime.now().minusHours(1))
            .endDate(OffsetDateTime.now().minusMinutes(30))
            .build();
    // ended 6 hours ago — outside window
    final ProcessInstanceDto outsideWindow =
        agenticInstance(PROC_KEY, 100L, 50L)
            .startDate(OffsetDateTime.now().minusHours(7))
            .endDate(OffsetDateTime.now().minusHours(6))
            .build();

    persistProcessInstances(List.of(withinWindow, outsideWindow));

    assertThat(evaluateNumber(KPI_COMPLETED_REPORT_ID, rollingEndDateFilter(2L, DateUnit.HOURS)))
        .isEqualTo(1.0);
  }

  @Test
  void shouldApplyRollingDayDateFilter() {
    // ended 12 hours ago — within last-1-day window
    final ProcessInstanceDto withinWindow =
        agenticInstance(PROC_KEY, 100L, 50L)
            .startDate(OffsetDateTime.now().minusHours(13))
            .endDate(OffsetDateTime.now().minusHours(12))
            .build();
    // ended 3 days ago — outside window
    final ProcessInstanceDto outsideWindow =
        agenticInstance(PROC_KEY, 100L, 50L)
            .startDate(OffsetDateTime.now().minusDays(4))
            .endDate(OffsetDateTime.now().minusDays(3))
            .build();

    persistProcessInstances(List.of(withinWindow, outsideWindow));

    assertThat(evaluateNumber(KPI_COMPLETED_REPORT_ID, rollingEndDateFilter(1L, DateUnit.DAYS)))
        .isEqualTo(1.0);
  }

  @Test
  void shouldApplyRollingWeekDateFilter() {
    // ended 3 days ago — within last-1-week window
    final ProcessInstanceDto withinWindow =
        agenticInstance(PROC_KEY, 100L, 50L)
            .startDate(OffsetDateTime.now().minusDays(4))
            .endDate(OffsetDateTime.now().minusDays(3))
            .build();
    // ended 10 days ago — outside window
    final ProcessInstanceDto outsideWindow =
        agenticInstance(PROC_KEY, 100L, 50L)
            .startDate(OffsetDateTime.now().minusDays(11))
            .endDate(OffsetDateTime.now().minusDays(10))
            .build();

    persistProcessInstances(List.of(withinWindow, outsideWindow));

    assertThat(evaluateNumber(KPI_COMPLETED_REPORT_ID, rollingEndDateFilter(1L, DateUnit.WEEKS)))
        .isEqualTo(1.0);
  }

  @Test
  void shouldApplyRollingMonthDateFilter() {
    // ended 15 days ago — within last-1-month window
    final ProcessInstanceDto withinWindow =
        agenticInstance(PROC_KEY, 100L, 50L)
            .startDate(OffsetDateTime.now().minusDays(16))
            .endDate(OffsetDateTime.now().minusDays(15))
            .build();
    // ended 40 days ago — outside window
    final ProcessInstanceDto outsideWindow =
        agenticInstance(PROC_KEY, 100L, 50L)
            .startDate(OffsetDateTime.now().minusDays(41))
            .endDate(OffsetDateTime.now().minusDays(40))
            .build();

    persistProcessInstances(List.of(withinWindow, outsideWindow));

    assertThat(evaluateNumber(KPI_COMPLETED_REPORT_ID, rollingEndDateFilter(1L, DateUnit.MONTHS)))
        .isEqualTo(1.0);
  }

  @Test
  void shouldAggregateMetricsWithinRollingDateFilter() {
    // both within last-1-week: avg tokens should reflect only these two
    final ProcessInstanceDto inst1 =
        agenticInstance(PROC_KEY, 100L, 100L)
            .startDate(OffsetDateTime.now().minusDays(3))
            .endDate(OffsetDateTime.now().minusDays(2))
            .build();
    final ProcessInstanceDto inst2 =
        agenticInstance(PROC_KEY, 200L, 200L)
            .startDate(OffsetDateTime.now().minusDays(2))
            .endDate(OffsetDateTime.now().minusDays(1))
            .build();
    // old instance with very different tokens — outside 1-week window
    final ProcessInstanceDto oldInst =
        agenticInstance(PROC_KEY, 10_000L, 10_000L)
            .startDate(OffsetDateTime.now().minusDays(30))
            .endDate(OffsetDateTime.now().minusDays(29))
            .build();

    persistProcessInstances(List.of(inst1, inst2, oldInst));

    // avg of 200 and 400 = 300
    assertThat(evaluateNumber(KPI_AVG_TOKENS_REPORT_ID, rollingEndDateFilter(1L, DateUnit.WEEKS)))
        .isCloseTo(300.0, within(1.0));
  }

  @Test
  void shouldApplyDateFilterToAvgDuration() {
    // within window: duration 1h
    final ProcessInstanceDto recent =
        agenticInstance(PROC_KEY, 0L, 0L)
            .startDate(OffsetDateTime.now().minusHours(3))
            .endDate(OffsetDateTime.now().minusHours(2))
            .duration(3_600_000L)
            .build();
    // outside window: duration 5h — should not skew the avg
    final ProcessInstanceDto old =
        agenticInstance(PROC_KEY, 0L, 0L)
            .startDate(OffsetDateTime.now().minusDays(10))
            .endDate(OffsetDateTime.now().minusDays(9))
            .duration(18_000_000L)
            .build();

    persistProcessInstances(List.of(recent, old));

    assertThat(evaluateNumber(KPI_AVG_DURATION_REPORT_ID, rollingEndDateFilter(1L, DateUnit.DAYS)))
        .isCloseTo(3_600_000.0, within(1.0));
  }

  @Test
  void shouldApplyDateFilterToP50Duration() {
    // within window: duration 1h
    final ProcessInstanceDto recent =
        agenticInstance(PROC_KEY, 0L, 0L)
            .startDate(OffsetDateTime.now().minusHours(3))
            .endDate(OffsetDateTime.now().minusHours(2))
            .duration(3_600_000L)
            .build();
    // outside window: duration 5h — should not affect P50
    final ProcessInstanceDto old =
        agenticInstance(PROC_KEY, 0L, 0L)
            .startDate(OffsetDateTime.now().minusDays(10))
            .endDate(OffsetDateTime.now().minusDays(9))
            .duration(18_000_000L)
            .build();

    persistProcessInstances(List.of(recent, old));

    assertThat(evaluateNumber(KPI_DURATION_P50_REPORT_ID, rollingEndDateFilter(1L, DateUnit.DAYS)))
        .isCloseTo(3_600_000.0, within(1.0));
  }

  @Test
  void shouldApplyDateFilterToP95Duration() {
    // within window: duration 1h
    final ProcessInstanceDto recent =
        agenticInstance(PROC_KEY, 0L, 0L)
            .startDate(OffsetDateTime.now().minusHours(3))
            .endDate(OffsetDateTime.now().minusHours(2))
            .duration(3_600_000L)
            .build();
    // outside window: duration 5h — should not affect P95
    final ProcessInstanceDto old =
        agenticInstance(PROC_KEY, 0L, 0L)
            .startDate(OffsetDateTime.now().minusDays(10))
            .endDate(OffsetDateTime.now().minusDays(9))
            .duration(18_000_000L)
            .build();

    persistProcessInstances(List.of(recent, old));

    assertThat(evaluateNumber(KPI_DURATION_P95_REPORT_ID, rollingEndDateFilter(1L, DateUnit.DAYS)))
        .isCloseTo(3_600_000.0, within(1.0));
  }

  @Test
  void shouldApplyDateFilterToIncidentRate() {
    // within window: 1 resolved incident
    final String recentId = UUID.randomUUID().toString();
    final ProcessInstanceDto recent =
        agenticInstance(PROC_KEY, 100L, 50L)
            .processInstanceId(recentId)
            .startDate(OffsetDateTime.now().minusHours(3))
            .endDate(OffsetDateTime.now().minusHours(2))
            .incidents(
                List.of(
                    IncidentDto.builder()
                        .incidentStatus(IncidentStatus.RESOLVED)
                        .processInstanceId(recentId)
                        .build()))
            .build();
    // outside window: 1 resolved incident — should be excluded
    final String oldId = UUID.randomUUID().toString();
    final ProcessInstanceDto old =
        agenticInstance(PROC_KEY, 100L, 50L)
            .processInstanceId(oldId)
            .startDate(OffsetDateTime.now().minusDays(10))
            .endDate(OffsetDateTime.now().minusDays(9))
            .incidents(
                List.of(
                    IncidentDto.builder()
                        .incidentStatus(IncidentStatus.RESOLVED)
                        .processInstanceId(oldId)
                        .build()))
            .build();

    persistProcessInstances(List.of(recent, old));

    assertThat(evaluateNumber(KPI_INCIDENT_RATE_REPORT_ID, rollingEndDateFilter(1L, DateUnit.DAYS)))
        .isEqualTo(1.0);
  }

  @Test
  void shouldApplyDateFilterToMedianTokens() {
    // within window: totals 100, 200, 300 → median 200
    final ProcessInstanceDto inst1 =
        agenticInstance(PROC_KEY, 50L, 50L)
            .startDate(OffsetDateTime.now().minusDays(3))
            .endDate(OffsetDateTime.now().minusDays(2))
            .build();
    final ProcessInstanceDto inst2 =
        agenticInstance(PROC_KEY, 100L, 100L)
            .startDate(OffsetDateTime.now().minusDays(2))
            .endDate(OffsetDateTime.now().minusDays(1))
            .build();
    final ProcessInstanceDto inst3 =
        agenticInstance(PROC_KEY, 150L, 150L)
            .startDate(OffsetDateTime.now().minusDays(1))
            .endDate(OffsetDateTime.now().minusHours(1))
            .build();
    // outside window — very high total, should not affect median
    final ProcessInstanceDto old =
        agenticInstance(PROC_KEY, 10_000L, 10_000L)
            .startDate(OffsetDateTime.now().minusDays(30))
            .endDate(OffsetDateTime.now().minusDays(29))
            .build();

    persistProcessInstances(List.of(inst1, inst2, inst3, old));

    assertThat(
            evaluateNumber(KPI_MEDIAN_TOKENS_REPORT_ID, rollingEndDateFilter(1L, DateUnit.WEEKS)))
        .isCloseTo(200.0, within(10.0));
  }

  // ---------------------------------------------------------------------------
  // Process definition filters
  // ---------------------------------------------------------------------------

  @Test
  void shouldCountAllProcessesWhenNoDefinitionFilterApplied() {
    final String procKeyA = "proc-all-a";
    final String procKeyB = "proc-all-b";

    persistProcessInstances(
        List.of(
            agenticInstance(procKeyA, 100L, 50L).build(),
            agenticInstance(procKeyB, 200L, 100L).build(),
            agenticInstance(procKeyB, 300L, 150L).build()));

    // no definition filter → all 3 counted
    assertThat(evaluateNumber(KPI_COMPLETED_REPORT_ID, noExtraFilters())).isEqualTo(3.0);
  }

  @Test
  void shouldFilterToSingleProcessDefinition() {
    final String procKeyA = "proc-single-a";
    final String procKeyB = "proc-single-b";

    persistProcessInstances(
        List.of(
            agenticInstance(procKeyA, 100L, 50L).build(),
            agenticInstance(procKeyA, 200L, 100L).build(),
            agenticInstance(procKeyB, 300L, 150L).build()));

    assertThat(
            evaluateNumber(
                KPI_COMPLETED_REPORT_ID,
                withDefinitions(List.of(new ReportDataDefinitionDto(procKeyA)))))
        .isEqualTo(2.0);
  }

  @Test
  void shouldFilterToMultipleProcessDefinitions() {
    final String procKeyA = "proc-multi-a";
    final String procKeyB = "proc-multi-b";
    final String procKeyC = "proc-multi-c";

    persistProcessInstances(
        List.of(
            agenticInstance(procKeyA, 100L, 50L).build(),
            agenticInstance(procKeyB, 200L, 100L).build(),
            agenticInstance(procKeyC, 300L, 150L).build()));

    // select A and B only — C excluded
    assertThat(
            evaluateNumber(
                KPI_COMPLETED_REPORT_ID,
                withDefinitions(
                    List.of(
                        new ReportDataDefinitionDto(procKeyA),
                        new ReportDataDefinitionDto(procKeyB)))))
        .isEqualTo(2.0);
  }

  @Test
  void shouldAggregateMetricsForSelectedProcessDefinition() {
    final String procKeyA = "proc-metrics-a";
    final String procKeyB = "proc-metrics-b";

    // procKeyA: input+output totals = 300 and 600 → avg = 450
    // procKeyB: total = 900 — should be excluded
    persistProcessInstances(
        List.of(
            agenticInstance(procKeyA, 100L, 200L).build(),
            agenticInstance(procKeyA, 200L, 400L).build(),
            agenticInstance(procKeyB, 300L, 600L).build()));

    assertThat(
            evaluateNumber(
                KPI_AVG_TOKENS_REPORT_ID,
                withDefinitions(List.of(new ReportDataDefinitionDto(procKeyA)))))
        .isCloseTo(450.0, within(1.0));
  }

  @Test
  void shouldApplyDefinitionFilterToAvgDuration() {
    final String procKeyA = "proc-dur-a";
    final String procKeyB = "proc-dur-b";

    // procKeyA: durations 1h and 3h → avg 2h
    // procKeyB: duration 10h — should be excluded
    persistProcessInstances(
        List.of(
            agenticInstance(procKeyA, 0L, 0L).duration(3_600_000L).build(),
            agenticInstance(procKeyA, 0L, 0L).duration(10_800_000L).build(),
            agenticInstance(procKeyB, 0L, 0L).duration(36_000_000L).build()));

    assertThat(
            evaluateNumber(
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
            agenticInstance(procKeyA, 0L, 0L).duration(3_600_000L).build(),
            agenticInstance(procKeyA, 0L, 0L).duration(10_800_000L).build(),
            agenticInstance(procKeyB, 0L, 0L).duration(36_000_000L).build()));

    assertThat(
            evaluateNumber(
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
            agenticInstance(procKeyA, 0L, 0L).duration(3_600_000L).build(),
            agenticInstance(procKeyA, 0L, 0L).duration(10_800_000L).build(),
            agenticInstance(procKeyB, 0L, 0L).duration(36_000_000L).build()));

    assertThat(
            evaluateNumber(
                KPI_DURATION_P95_REPORT_ID,
                withDefinitions(List.of(new ReportDataDefinitionDto(procKeyA)))))
        .isCloseTo(10_800_000.0, within(1_000_000.0));
  }

  @Test
  void shouldApplyDefinitionFilterToIncidentRate() {
    final String procKeyA = "proc-inc-a";
    final String procKeyB = "proc-inc-b";

    final String idA = UUID.randomUUID().toString();
    final String idB = UUID.randomUUID().toString();

    persistProcessInstances(
        List.of(
            agenticInstance(procKeyA, 100L, 50L)
                .processInstanceId(idA)
                .incidents(
                    List.of(
                        IncidentDto.builder()
                            .incidentStatus(IncidentStatus.RESOLVED)
                            .processInstanceId(idA)
                            .build()))
                .build(),
            agenticInstance(procKeyB, 100L, 50L)
                .processInstanceId(idB)
                .incidents(
                    List.of(
                        IncidentDto.builder()
                            .incidentStatus(IncidentStatus.RESOLVED)
                            .processInstanceId(idB)
                            .build()))
                .build()));

    // only procKeyA selected → 1 incident
    assertThat(
            evaluateNumber(
                KPI_INCIDENT_RATE_REPORT_ID,
                withDefinitions(List.of(new ReportDataDefinitionDto(procKeyA)))))
        .isEqualTo(1.0);
  }

  @Test
  void shouldApplyDefinitionFilterToMedianTokens() {
    final String procKeyA = "proc-med-a";
    final String procKeyB = "proc-med-b";

    // procKeyA: totals 100, 200, 300 → median 200
    // procKeyB: total 50_000 — should be excluded
    persistProcessInstances(
        List.of(
            agenticInstance(procKeyA, 50L, 50L).build(),
            agenticInstance(procKeyA, 100L, 100L).build(),
            agenticInstance(procKeyA, 150L, 150L).build(),
            agenticInstance(procKeyB, 25_000L, 25_000L).build()));

    assertThat(
            evaluateNumber(
                KPI_MEDIAN_TOKENS_REPORT_ID,
                withDefinitions(List.of(new ReportDataDefinitionDto(procKeyA)))))
        .isCloseTo(200.0, within(10.0));
  }

  // ---------------------------------------------------------------------------
  // Token trend report (weekly multi-measure line chart)
  // ---------------------------------------------------------------------------

  @Test
  void shouldComputeWeeklyInputTokensGroupedByWeek() {
    // given two instances within the same week with known input tokens
    persistProcessInstances(
        List.of(
            agenticInstance(PROC_KEY, 100L, 50L)
                .startDate(OffsetDateTime.now().minusDays(3))
                .endDate(OffsetDateTime.now().minusDays(2))
                .build(),
            agenticInstance(PROC_KEY, 200L, 80L)
                .startDate(OffsetDateTime.now().minusDays(3))
                .endDate(OffsetDateTime.now().minusDays(2))
                .build()));

    // when evaluating the first measure (input tokens) of the token-trend report
    final List<MapResultEntryDto> buckets = evaluateMapMeasure(TOKEN_TREND_REPORT_ID, 0);

    // then total input tokens across all buckets equals 300
    assertThat(buckets).isNotEmpty();
    final double totalInput =
        buckets.stream()
            .filter(e -> e.getValue() != null)
            .mapToDouble(MapResultEntryDto::getValue)
            .sum();
    assertThat(totalInput).isCloseTo(300.0, within(1.0));
  }

  @Test
  void shouldComputeWeeklyOutputTokensGroupedByWeek() {
    // given two instances within the same week with known output tokens
    persistProcessInstances(
        List.of(
            agenticInstance(PROC_KEY, 50L, 100L)
                .startDate(OffsetDateTime.now().minusDays(3))
                .endDate(OffsetDateTime.now().minusDays(2))
                .build(),
            agenticInstance(PROC_KEY, 80L, 200L)
                .startDate(OffsetDateTime.now().minusDays(3))
                .endDate(OffsetDateTime.now().minusDays(2))
                .build()));

    // when evaluating the second measure (output tokens) of the token-trend report
    final List<MapResultEntryDto> buckets = evaluateMapMeasure(TOKEN_TREND_REPORT_ID, 1);

    // then total output tokens across all buckets equals 300
    assertThat(buckets).isNotEmpty();
    final double totalOutput =
        buckets.stream()
            .filter(e -> e.getValue() != null)
            .mapToDouble(MapResultEntryDto::getValue)
            .sum();
    assertThat(totalOutput).isCloseTo(300.0, within(1.0));
  }

  // ---------------------------------------------------------------------------
  // Top token consumers by process
  // ---------------------------------------------------------------------------

  @Test
  void shouldRankProcessesByTotalTokensConsumed() {
    final String heavyProc = "heavy-agent-process";
    final String lightProc = "light-agent-process";

    // heavyProc consumes 450 tokens total, lightProc consumes 120
    persistProcessInstances(
        List.of(
            agenticInstance(heavyProc, 200L, 100L).build(),
            agenticInstance(heavyProc, 100L, 50L).build(),
            agenticInstance(lightProc, 80L, 40L).build()));

    // when evaluating the top-consumers report grouped by process definition key
    final List<MapResultEntryDto> buckets = evaluateMapMeasure(TOKEN_CONSUMERS_REPORT_ID, 0);

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
  void shouldReturnOnlyTopConsumersWithTotalCountWhenPaginated() {
    // given three processes with distinct total token sums
    persistProcessInstances(
        List.of(
            agenticInstance("proc-a", 300L, 0L).build(),
            agenticInstance("proc-b", 200L, 0L).build(),
            agenticInstance("proc-c", 100L, 0L).build()));

    // when evaluating the top-consumers report limited to the two heaviest processes
    // (limit only, no offset — mirroring how the dashboard frontend requests the tile)
    final var result =
        evaluationService.evaluateSavedReportWithAdditionalFilters(
            USER_ID, UTC, TOKEN_CONSUMERS_REPORT_ID, noExtraFilters(), new PaginationDto(2, null));
    final CommandEvaluationResult<?> commandResult =
        ((SingleReportEvaluationResult<?>) result.getEvaluationResult()).getFirstCommandResult();
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

  // ---------------------------------------------------------------------------
  // Combined: process definition + date range
  // ---------------------------------------------------------------------------
  @Test
  void shouldApplyDefinitionAndDateFilterTogether() {
    final String procKeyA = "proc-combo-a";
    final String procKeyB = "proc-combo-b";

    // procKeyA recent — should be counted
    final ProcessInstanceDto aRecent =
        agenticInstance(procKeyA, 100L, 50L)
            .startDate(OffsetDateTime.now().minusHours(2))
            .endDate(OffsetDateTime.now().minusHours(1))
            .build();
    // procKeyA old — excluded by date
    final ProcessInstanceDto aOld =
        agenticInstance(procKeyA, 100L, 50L)
            .startDate(OffsetDateTime.now().minusDays(10))
            .endDate(OffsetDateTime.now().minusDays(9))
            .build();
    // procKeyB recent — excluded by definition
    final ProcessInstanceDto bRecent =
        agenticInstance(procKeyB, 100L, 50L)
            .startDate(OffsetDateTime.now().minusHours(2))
            .endDate(OffsetDateTime.now().minusHours(1))
            .build();

    persistProcessInstances(List.of(aRecent, aOld, bRecent));

    final AdditionalProcessReportEvaluationFilterDto combined =
        withDefinitionsAndDateFilter(
            List.of(new ReportDataDefinitionDto(procKeyA)), 1L, DateUnit.DAYS);
    assertThat(evaluateNumber(KPI_COMPLETED_REPORT_ID, combined)).isEqualTo(1.0);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /**
   * Builds a minimal completed agentic {@link ProcessInstanceDto} with one {@link AgentInstanceDto}
   * and the given total token counts.
   */
  private ProcessInstanceDto.ProcessInstanceDtoBuilder agenticInstance(
      final String processDefinitionKey, final long inputTokens, final long outputTokens) {
    final AgentMetricsDto metrics = new AgentMetricsDto();
    metrics.setInputTokens(inputTokens);
    metrics.setOutputTokens(outputTokens);

    final AgentInstanceDto agentInstance = new AgentInstanceDto();
    agentInstance.setAgentInstanceId(UUID.randomUUID().toString());
    agentInstance.setMetrics(metrics);

    return completedInstance(processDefinitionKey)
        .agentInstances(List.of(agentInstance))
        .agentTotalInputTokens(inputTokens)
        .agentTotalOutputTokens(outputTokens)
        .agentTotalTokens(inputTokens + outputTokens);
  }

  private List<MapResultEntryDto> evaluateMapMeasure(
      final String reportId, final int measureIndex) {
    final var result =
        evaluationService.evaluateSavedReportWithAdditionalFilters(
            USER_ID, UTC, reportId, noExtraFilters(), null);
    final MapCommandResult commandResult =
        (MapCommandResult)
            ((SingleReportEvaluationResult<?>) result.getEvaluationResult())
                .getCommandEvaluationResults()
                .get(measureIndex);
    final List<MeasureDto<List<MapResultEntryDto>>> measures = commandResult.getMeasures();
    return measures.isEmpty() ? List.of() : measures.getFirst().getData();
  }

  private Double evaluateNumber(
      final String reportId, final AdditionalProcessReportEvaluationFilterDto filterDto) {
    final NumberCommandResult commandResult =
        (NumberCommandResult) firstCommandResult(reportId, filterDto);
    final List<MeasureDto<Double>> measures = commandResult.getMeasures();
    return measures.isEmpty() ? null : measures.getFirst().getData();
  }

  private CommandEvaluationResult<?> firstCommandResult(
      final String reportId, final AdditionalProcessReportEvaluationFilterDto filterDto) {
    final var result =
        evaluationService.evaluateSavedReportWithAdditionalFilters(
            USER_ID, UTC, reportId, filterDto, null);
    return ((SingleReportEvaluationResult<?>) result.getEvaluationResult()).getFirstCommandResult();
  }

  private AdditionalProcessReportEvaluationFilterDto noExtraFilters() {
    return new AdditionalProcessReportEvaluationFilterDto(List.of());
  }

  private AdditionalProcessReportEvaluationFilterDto rollingEndDateFilter(
      final long value, final DateUnit unit) {
    final InstanceEndDateFilterDto filter = new InstanceEndDateFilterDto();
    filter.setData(new RollingDateFilterDataDto(new RollingDateFilterStartDto(value, unit)));
    filter.setFilterLevel(FilterApplicationLevel.INSTANCE);
    return new AdditionalProcessReportEvaluationFilterDto(List.of((ProcessFilterDto<?>) filter));
  }

  private AdditionalProcessReportEvaluationFilterDto withDefinitions(
      final List<ReportDataDefinitionDto> definitions) {
    final AdditionalProcessReportEvaluationFilterDto dto =
        new AdditionalProcessReportEvaluationFilterDto(List.of());
    dto.setDefinitions(definitions);
    return dto;
  }

  private AdditionalProcessReportEvaluationFilterDto withDefinitionsAndDateFilter(
      final List<ReportDataDefinitionDto> definitions, final long value, final DateUnit unit) {
    final InstanceEndDateFilterDto filter = new InstanceEndDateFilterDto();
    filter.setData(new RollingDateFilterDataDto(new RollingDateFilterStartDto(value, unit)));
    filter.setFilterLevel(FilterApplicationLevel.INSTANCE);
    final AdditionalProcessReportEvaluationFilterDto dto = withDefinitions(definitions);
    dto.setFilter(List.of((ProcessFilterDto<?>) filter));
    return dto;
  }

  private static Stream<Arguments> durationPercentileReportIds() {
    return Stream.of(
        Arguments.of(KPI_DURATION_P50_REPORT_ID, 10_800_000.0, 10_000.0),
        Arguments.of(KPI_DURATION_P95_REPORT_ID, 18_000_000.0, 1_000_000.0));
  }
}
