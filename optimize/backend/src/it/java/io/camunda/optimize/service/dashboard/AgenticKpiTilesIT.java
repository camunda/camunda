/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.dashboard;

import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.DURATION_STABILITY_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.FAILURE_RATE_BY_VERSION_REPORT_ID;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
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
import io.camunda.optimize.service.exceptions.evaluation.ReportEvaluationException;
import io.camunda.optimize.service.report.ReportEvaluationService;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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
    final ProcessInstanceDto completed1 = agenticInstanceWithTokens(PROC_KEY, 100L, 50L).build();
    final ProcessInstanceDto completed2 = agenticInstanceWithTokens(PROC_KEY, 200L, 100L).build();
    // running instance — should not be counted
    final ProcessInstanceDto running =
        agenticInstanceWithTokens(PROC_KEY, 50L, 25L)
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
    final ProcessInstanceDto inst1 =
        agenticInstanceWithTokens(PROC_KEY, 0L, 0L).duration(3_600_000L).build();
    final ProcessInstanceDto inst2 =
        agenticInstanceWithTokens(PROC_KEY, 0L, 0L).duration(10_800_000L).build();

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

    final Double result = evaluateNumber(reportId, noExtraFilters());
    // ES percentile approximation
    assertThat(result).isCloseTo(expectedValue, within(tolerance));
  }

  // ---------------------------------------------------------------------------
  // Incident rate (percentage of completed agentic instances with a resolved incident)
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
        agenticInstanceWithTokens(PROC_KEY, 100L, 50L)
            .processInstanceId(instanceId1)
            .incidents(List.of(resolved))
            .build();

    // no incidents
    final ProcessInstanceDto clean =
        agenticInstanceWithTokens(PROC_KEY, 100L, 50L).processInstanceId(instanceId2).build();

    // running instance with a resolved incident — must not be counted
    final String runningId = UUID.randomUUID().toString();
    final ProcessInstanceDto running =
        agenticInstanceWithTokens(PROC_KEY, 100L, 50L)
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
    // 1 instance with resolved incident / 2 completed agentic instances = 50%
    assertThat(result).isEqualTo(50.0);
  }

  // ---------------------------------------------------------------------------
  // Average tokens
  // ---------------------------------------------------------------------------

  @Test
  void shouldComputeAverageTokensPerExecution() {
    // totals: 150, 300, 450  → avg = 300
    final ProcessInstanceDto inst1 = agenticInstanceWithTokens(PROC_KEY, 100L, 50L).build();
    final ProcessInstanceDto inst2 = agenticInstanceWithTokens(PROC_KEY, 200L, 100L).build();
    final ProcessInstanceDto inst3 = agenticInstanceWithTokens(PROC_KEY, 300L, 150L).build();

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
    final ProcessInstanceDto inst1 = agenticInstanceWithTokens(PROC_KEY, 70L, 30L).build();
    final ProcessInstanceDto inst2 = agenticInstanceWithTokens(PROC_KEY, 140L, 60L).build();
    final ProcessInstanceDto inst3 = agenticInstanceWithTokens(PROC_KEY, 210L, 90L).build();
    final ProcessInstanceDto inst4 = agenticInstanceWithTokens(PROC_KEY, 280L, 120L).build();
    final ProcessInstanceDto inst5 = agenticInstanceWithTokens(PROC_KEY, 350L, 150L).build();

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
        agenticInstanceWithTokens(PROC_KEY, 100L, 50L)
            .startDate(OffsetDateTime.now().minusHours(1))
            .endDate(OffsetDateTime.now().minusMinutes(30))
            .build();
    final ProcessInstanceDto older =
        agenticInstanceWithTokens(PROC_KEY, 100L, 50L)
            .startDate(OffsetDateTime.now().minusDays(30))
            .endDate(OffsetDateTime.now().minusDays(29))
            .build();
    final ProcessInstanceDto oldest =
        agenticInstanceWithTokens(PROC_KEY, 100L, 50L)
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
        agenticInstanceWithTokens(PROC_KEY, 100L, 50L)
            .startDate(OffsetDateTime.now().minusHours(1))
            .endDate(OffsetDateTime.now().minusMinutes(30))
            .build();
    // ended 6 hours ago — outside window
    final ProcessInstanceDto outsideWindow =
        agenticInstanceWithTokens(PROC_KEY, 100L, 50L)
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
        agenticInstanceWithTokens(PROC_KEY, 100L, 50L)
            .startDate(OffsetDateTime.now().minusHours(13))
            .endDate(OffsetDateTime.now().minusHours(12))
            .build();
    // ended 3 days ago — outside window
    final ProcessInstanceDto outsideWindow =
        agenticInstanceWithTokens(PROC_KEY, 100L, 50L)
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
        agenticInstanceWithTokens(PROC_KEY, 100L, 50L)
            .startDate(OffsetDateTime.now().minusDays(4))
            .endDate(OffsetDateTime.now().minusDays(3))
            .build();
    // ended 10 days ago — outside window
    final ProcessInstanceDto outsideWindow =
        agenticInstanceWithTokens(PROC_KEY, 100L, 50L)
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
        agenticInstanceWithTokens(PROC_KEY, 100L, 50L)
            .startDate(OffsetDateTime.now().minusDays(16))
            .endDate(OffsetDateTime.now().minusDays(15))
            .build();
    // ended 40 days ago — outside window
    final ProcessInstanceDto outsideWindow =
        agenticInstanceWithTokens(PROC_KEY, 100L, 50L)
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
        agenticInstanceWithTokens(PROC_KEY, 100L, 100L)
            .startDate(OffsetDateTime.now().minusDays(3))
            .endDate(OffsetDateTime.now().minusDays(2))
            .build();
    final ProcessInstanceDto inst2 =
        agenticInstanceWithTokens(PROC_KEY, 200L, 200L)
            .startDate(OffsetDateTime.now().minusDays(2))
            .endDate(OffsetDateTime.now().minusDays(1))
            .build();
    // old instance with very different tokens — outside 1-week window
    final ProcessInstanceDto oldInst =
        agenticInstanceWithTokens(PROC_KEY, 10_000L, 10_000L)
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

    assertThat(evaluateNumber(KPI_AVG_DURATION_REPORT_ID, rollingEndDateFilter(1L, DateUnit.DAYS)))
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

    assertThat(evaluateNumber(KPI_DURATION_P50_REPORT_ID, rollingEndDateFilter(1L, DateUnit.DAYS)))
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

    assertThat(evaluateNumber(KPI_DURATION_P95_REPORT_ID, rollingEndDateFilter(1L, DateUnit.DAYS)))
        .isCloseTo(3_600_000.0, within(1.0));
  }

  @Test
  void shouldApplyDateFilterToIncidentRate() {
    // within window: 1 resolved incident
    final String recentId = UUID.randomUUID().toString();
    final ProcessInstanceDto recent =
        agenticInstanceWithTokens(PROC_KEY, 100L, 50L)
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
        agenticInstanceWithTokens(PROC_KEY, 100L, 50L)
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

    // 1 instance with resolved incident / 1 completed agentic instance in window = 100%
    assertThat(evaluateNumber(KPI_INCIDENT_RATE_REPORT_ID, rollingEndDateFilter(1L, DateUnit.DAYS)))
        .isEqualTo(100.0);
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
            agenticInstanceWithTokens(procKeyA, 100L, 50L).build(),
            agenticInstanceWithTokens(procKeyB, 200L, 100L).build(),
            agenticInstanceWithTokens(procKeyB, 300L, 150L).build()));

    // no definition filter → all 3 counted
    assertThat(evaluateNumber(KPI_COMPLETED_REPORT_ID, noExtraFilters())).isEqualTo(3.0);
  }

  @Test
  void shouldFilterToSingleProcessDefinition() {
    final String procKeyA = "proc-single-a";
    final String procKeyB = "proc-single-b";

    persistProcessInstances(
        List.of(
            agenticInstanceWithTokens(procKeyA, 100L, 50L).build(),
            agenticInstanceWithTokens(procKeyA, 200L, 100L).build(),
            agenticInstanceWithTokens(procKeyB, 300L, 150L).build()));

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
            agenticInstanceWithTokens(procKeyA, 100L, 50L).build(),
            agenticInstanceWithTokens(procKeyB, 200L, 100L).build(),
            agenticInstanceWithTokens(procKeyC, 300L, 150L).build()));

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
            agenticInstanceWithTokens(procKeyA, 100L, 200L).build(),
            agenticInstanceWithTokens(procKeyA, 200L, 400L).build(),
            agenticInstanceWithTokens(procKeyB, 300L, 600L).build()));

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
            agenticInstanceWithTokens(procKeyA, 0L, 0L).duration(3_600_000L).build(),
            agenticInstanceWithTokens(procKeyA, 0L, 0L).duration(10_800_000L).build(),
            agenticInstanceWithTokens(procKeyB, 0L, 0L).duration(36_000_000L).build()));

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
            agenticInstanceWithDuration(procKeyA, 3_600_000L).build(),
            agenticInstanceWithDuration(procKeyA, 10_800_000L).build(),
            agenticInstanceWithDuration(procKeyB, 36_000_000L).build()));

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
            agenticInstanceWithDuration(procKeyA, 3_600_000L).build(),
            agenticInstanceWithDuration(procKeyA, 10_800_000L).build(),
            agenticInstanceWithDuration(procKeyB, 36_000_000L).build()));

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
            agenticInstanceWithTokens(procKeyA, 100L, 50L)
                .processInstanceId(idA)
                .incidents(
                    List.of(
                        IncidentDto.builder()
                            .incidentStatus(IncidentStatus.RESOLVED)
                            .processInstanceId(idA)
                            .build()))
                .build(),
            agenticInstanceWithTokens(procKeyB, 100L, 50L)
                .processInstanceId(idB)
                .incidents(
                    List.of(
                        IncidentDto.builder()
                            .incidentStatus(IncidentStatus.RESOLVED)
                            .processInstanceId(idB)
                            .build()))
                .build()));

    // only procKeyA selected → 1 instance with incident / 1 completed agentic instance = 100%
    assertThat(
            evaluateNumber(
                KPI_INCIDENT_RATE_REPORT_ID,
                withDefinitions(List.of(new ReportDataDefinitionDto(procKeyA)))))
        .isEqualTo(100.0);
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
            evaluateNumber(
                KPI_MEDIAN_TOKENS_REPORT_ID,
                withDefinitions(List.of(new ReportDataDefinitionDto(procKeyA)))))
        .isCloseTo(200.0, within(10.0));
  }

  // ---------------------------------------------------------------------------
  // Scoped baseline: denominator must exclude running and non-agentic instances
  // ---------------------------------------------------------------------------

  @Test
  void shouldExcludeRunningInstancesFromIncidentRateDenominator() {
    // given: 1 completed agentic with incident, 1 completed agentic without,
    //        plus 5 running agentic instances (no incidents) — running must not count
    final String withIncidentId = UUID.randomUUID().toString();
    final ProcessInstanceDto withIncident =
        agenticInstanceWithTokens(PROC_KEY, 100L, 50L)
            .processInstanceId(withIncidentId)
            .incidents(
                List.of(
                    IncidentDto.builder()
                        .incidentStatus(IncidentStatus.RESOLVED)
                        .processInstanceId(withIncidentId)
                        .build()))
            .build();
    final ProcessInstanceDto completedClean =
        agenticInstanceWithTokens(PROC_KEY, 100L, 50L).build();
    final List<ProcessInstanceDto> runningInstances =
        java.util.stream.IntStream.range(0, 5)
            .mapToObj(
                i ->
                    agenticInstanceWithTokens(PROC_KEY, 100L, 50L)
                        .state(ProcessInstanceConstants.ACTIVE_STATE)
                        .endDate(null)
                        .duration(null)
                        .build())
            .toList();

    persistProcessInstances(
        java.util.stream.Stream.concat(
                java.util.stream.Stream.of(withIncident, completedClean), runningInstances.stream())
            .toList());

    // denominator = 2 completed agentic instances (not 7); numerator = 1 → 50%
    assertThat(evaluateNumber(KPI_INCIDENT_RATE_REPORT_ID, noExtraFilters())).isEqualTo(50.0);
  }

  @Test
  void shouldExcludeNonAgenticInstancesFromIncidentRateDenominator() {
    // given: 1 completed agentic with incident, 1 completed agentic without,
    //        plus 8 completed non-agentic instances — non-agentic must not count
    final String withIncidentId = UUID.randomUUID().toString();
    final ProcessInstanceDto withIncident =
        agenticInstanceWithTokens(PROC_KEY, 100L, 50L)
            .processInstanceId(withIncidentId)
            .incidents(
                List.of(
                    IncidentDto.builder()
                        .incidentStatus(IncidentStatus.RESOLVED)
                        .processInstanceId(withIncidentId)
                        .build()))
            .build();
    final ProcessInstanceDto completedAgentic =
        agenticInstanceWithTokens(PROC_KEY, 100L, 50L).build();
    final List<ProcessInstanceDto> nonAgenticInstances =
        java.util.stream.IntStream.range(0, 8)
            .mapToObj(i -> completedInstance(PROC_KEY).build())
            .toList();

    persistProcessInstances(
        java.util.stream.Stream.concat(
                java.util.stream.Stream.of(withIncident, completedAgentic),
                nonAgenticInstances.stream())
            .toList());

    // denominator = 2 completed agentic instances (not 10); numerator = 1 → 50%
    assertThat(evaluateNumber(KPI_INCIDENT_RATE_REPORT_ID, noExtraFilters())).isEqualTo(50.0);
  }

  @Test
  void shouldScopeBothNumeratorAndDenominatorByDateFilter() {
    // given: within window — 1 agentic with incident, 1 agentic without
    //        outside window — 10 agentic with incidents (must not affect either side)
    final String recentWithId = UUID.randomUUID().toString();
    final ProcessInstanceDto recentWithIncident =
        agenticInstanceWithTokens(PROC_KEY, 100L, 50L)
            .processInstanceId(recentWithId)
            .startDate(OffsetDateTime.now().minusHours(3))
            .endDate(OffsetDateTime.now().minusHours(2))
            .incidents(
                List.of(
                    IncidentDto.builder()
                        .incidentStatus(IncidentStatus.RESOLVED)
                        .processInstanceId(recentWithId)
                        .build()))
            .build();
    final ProcessInstanceDto recentClean =
        agenticInstanceWithTokens(PROC_KEY, 100L, 50L)
            .startDate(OffsetDateTime.now().minusHours(3))
            .endDate(OffsetDateTime.now().minusHours(2))
            .build();
    final List<ProcessInstanceDto> oldWithIncidents =
        java.util.stream.IntStream.range(0, 10)
            .mapToObj(
                i -> {
                  final String id = UUID.randomUUID().toString();
                  return agenticInstanceWithTokens(PROC_KEY, 100L, 50L)
                      .processInstanceId(id)
                      .startDate(OffsetDateTime.now().minusDays(10))
                      .endDate(OffsetDateTime.now().minusDays(9))
                      .incidents(
                          List.of(
                              IncidentDto.builder()
                                  .incidentStatus(IncidentStatus.RESOLVED)
                                  .processInstanceId(id)
                                  .build()))
                      .build();
                })
            .toList();

    persistProcessInstances(
        java.util.stream.Stream.concat(
                java.util.stream.Stream.of(recentWithIncident, recentClean),
                oldWithIncidents.stream())
            .toList());

    // denominator = 2 (in-window completed agentic); numerator = 1 → 50%
    assertThat(evaluateNumber(KPI_INCIDENT_RATE_REPORT_ID, rollingEndDateFilter(1L, DateUnit.DAYS)))
        .isEqualTo(50.0);
  }

  @Test
  void shouldScopeBothNumeratorAndDenominatorByDefinitionFilter() {
    // given: procKeyA — 1 with incident, 3 without → scoped rate = 25%
    //        procKeyB — 5 all with incidents → must not inflate the procKeyA denominator
    final String procKeyA = "proc-scope-a";
    final String procKeyB = "proc-scope-b";

    final String idA = UUID.randomUUID().toString();
    final ProcessInstanceDto aWithIncident =
        agenticInstanceWithTokens(procKeyA, 100L, 50L)
            .processInstanceId(idA)
            .incidents(
                List.of(
                    IncidentDto.builder()
                        .incidentStatus(IncidentStatus.RESOLVED)
                        .processInstanceId(idA)
                        .build()))
            .build();
    final List<ProcessInstanceDto> aClean =
        java.util.stream.IntStream.range(0, 3)
            .mapToObj(i -> agenticInstanceWithTokens(procKeyA, 100L, 50L).build())
            .toList();
    final List<ProcessInstanceDto> bWithIncidents =
        java.util.stream.IntStream.range(0, 5)
            .mapToObj(
                i -> {
                  final String id = UUID.randomUUID().toString();
                  return agenticInstanceWithTokens(procKeyB, 100L, 50L)
                      .processInstanceId(id)
                      .incidents(
                          List.of(
                              IncidentDto.builder()
                                  .incidentStatus(IncidentStatus.RESOLVED)
                                  .processInstanceId(id)
                                  .build()))
                      .build();
                })
            .toList();

    persistProcessInstances(
        java.util.stream.Stream.concat(
                java.util.stream.Stream.concat(
                    java.util.stream.Stream.of(aWithIncident), aClean.stream()),
                bWithIncidents.stream())
            .toList());

    // denominator = 4 procKeyA instances (not 9); numerator = 1 → 25%
    assertThat(
            evaluateNumber(
                KPI_INCIDENT_RATE_REPORT_ID,
                withDefinitions(List.of(new ReportDataDefinitionDto(procKeyA)))))
        .isEqualTo(25.0);
  }

  // ---------------------------------------------------------------------------
  // Token trend report (weekly multi-measure line chart)
  // ---------------------------------------------------------------------------

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
            agenticInstanceWithTokens(PROC_KEY, 50L, 100L)
                .startDate(OffsetDateTime.now().minusDays(3))
                .endDate(OffsetDateTime.now().minusDays(2))
                .build(),
            agenticInstanceWithTokens(PROC_KEY, 80L, 200L)
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
            agenticInstanceWithTokens(heavyProc, 200L, 100L).build(),
            agenticInstanceWithTokens(heavyProc, 100L, 50L).build(),
            agenticInstanceWithTokens(lightProc, 80L, 40L).build()));

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
            agenticInstanceWithTokens("proc-a", 300L, 0L).build(),
            agenticInstanceWithTokens("proc-b", 200L, 0L).build(),
            agenticInstanceWithTokens("proc-c", 100L, 0L).build()));

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

  @Test
  void shouldRejectNonZeroOffsetForGroupedTopNReport() {
    // given a top-consumers report (grouped top-N supports a limit but not paging into results)
    persistProcessInstances(List.of(agenticInstanceWithTokens("proc-a", 300L, 0L).build()));

    // when evaluating it with a non-zero pagination offset
    final ThrowingCallable evaluation =
        () ->
            evaluationService.evaluateSavedReportWithAdditionalFilters(
                USER_ID, UTC, TOKEN_CONSUMERS_REPORT_ID, noExtraFilters(), new PaginationDto(2, 1));

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
            evaluationService.evaluateSavedReportWithAdditionalFilters(
                USER_ID,
                UTC,
                TOKEN_CONSUMERS_REPORT_ID,
                noExtraFilters(),
                new PaginationDto(null, 1));

    // then the request is rejected rather than silently ignoring the offset
    assertThatThrownBy(evaluation)
        .isInstanceOf(ReportEvaluationException.class)
        .hasMessageContaining("non-zero pagination offset");
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
        agenticInstanceWithTokens(procKeyA, 100L, 50L)
            .startDate(OffsetDateTime.now().minusHours(2))
            .endDate(OffsetDateTime.now().minusHours(1))
            .build();
    // procKeyA old — excluded by date
    final ProcessInstanceDto aOld =
        agenticInstanceWithTokens(procKeyA, 100L, 50L)
            .startDate(OffsetDateTime.now().minusDays(10))
            .endDate(OffsetDateTime.now().minusDays(9))
            .build();
    // procKeyB recent — excluded by definition
    final ProcessInstanceDto bRecent =
        agenticInstanceWithTokens(procKeyB, 100L, 50L)
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
  // Duration stability
  // ---------------------------------------------------------------------------

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
    final MapCommandResult result = evaluateMap(DURATION_STABILITY_REPORT_ID, noExtraFilters());

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
    final MapCommandResult result = evaluateMap(DURATION_STABILITY_REPORT_ID, noExtraFilters());

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
    final MapCommandResult result = evaluateMap(DURATION_STABILITY_REPORT_ID, noExtraFilters());

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
    final MapCommandResult result = evaluateMap(DURATION_STABILITY_REPORT_ID, noExtraFilters());

    // then — only 1 data point in each measure (the agentic instance)
    result.getMeasures().forEach(m -> assertThat(m.getData()).hasSize(1));
  }

  // ---------------------------------------------------------------------------
  // Failure rate by process version
  // ---------------------------------------------------------------------------

  @Test
  void shouldComputeFailureRatePerProcessVersion() {
    // given version 1: 2 completed agentic instances, 1 with a resolved incident → 50%
    final ProcessInstanceDto v1WithIncident =
        resolvedIncident(agenticInstance(PROC_KEY, "1", 100L, 50L));
    final ProcessInstanceDto v1Clean = agenticInstance(PROC_KEY, "1", 100L, 50L).build();
    // version 2: 4 completed agentic instances, 1 with a resolved incident → 25%
    final ProcessInstanceDto v2WithIncident =
        resolvedIncident(agenticInstance(PROC_KEY, "2", 100L, 50L));
    final List<ProcessInstanceDto> v2Clean =
        repeat(3, () -> agenticInstance(PROC_KEY, "2", 100L, 50L).build());

    persistProcessInstances(
        Stream.concat(Stream.of(v1WithIncident, v1Clean, v2WithIncident), v2Clean.stream())
            .toList());

    // when
    final List<MapResultEntryDto> buckets =
        evaluateMapData(FAILURE_RATE_BY_VERSION_REPORT_ID, noExtraFilters());

    // then
    assertThat(rateForVersion(buckets, "1")).isEqualTo(50.0);
    assertThat(rateForVersion(buckets, "2")).isEqualTo(25.0);
  }

  @Test
  void shouldScopeDenominatorPerVersionBucket() {
    // given version 1: 1 completed agentic with incident, 1 completed agentic clean → 50%
    final ProcessInstanceDto withIncident =
        resolvedIncident(agenticInstance(PROC_KEY, "1", 100L, 50L));
    final ProcessInstanceDto clean = agenticInstance(PROC_KEY, "1", 100L, 50L).build();
    // running agentic instances on the same version — must be excluded from the denominator
    final List<ProcessInstanceDto> running =
        repeat(
            5,
            () ->
                agenticInstance(PROC_KEY, "1", 100L, 50L)
                    .state(ProcessInstanceConstants.ACTIVE_STATE)
                    .endDate(null)
                    .duration(null)
                    .build());
    // completed non-agentic instances on the same version — must be excluded from the denominator
    final List<ProcessInstanceDto> nonAgentic =
        repeat(8, () -> completedInstance(PROC_KEY).processDefinitionVersion("1").build());

    persistProcessInstances(
        Stream.of(List.of(withIncident, clean), running, nonAgentic)
            .flatMap(List::stream)
            .toList());

    // when
    final List<MapResultEntryDto> buckets =
        evaluateMapData(FAILURE_RATE_BY_VERSION_REPORT_ID, noExtraFilters());

    // then denominator = 2 completed agentic instances (not 15); numerator = 1 → 50%
    assertThat(rateForVersion(buckets, "1")).isEqualTo(50.0);
  }

  @Test
  void shouldScopeFailureRatePerVersionByDateFilter() {
    // given version 1 in window: 1 with incident, 1 clean → 50%
    final ProcessInstanceDto recentWithIncident =
        resolvedIncident(
            agenticInstance(PROC_KEY, "1", 100L, 50L)
                .startDate(OffsetDateTime.now().minusHours(3))
                .endDate(OffsetDateTime.now().minusHours(2)));
    final ProcessInstanceDto recentClean =
        agenticInstance(PROC_KEY, "1", 100L, 50L)
            .startDate(OffsetDateTime.now().minusHours(3))
            .endDate(OffsetDateTime.now().minusHours(2))
            .build();
    // version 1 out of window: 10 with incidents — must not affect the bucket
    final List<ProcessInstanceDto> oldWithIncidents =
        repeat(
            10,
            () ->
                resolvedIncident(
                    agenticInstance(PROC_KEY, "1", 100L, 50L)
                        .startDate(OffsetDateTime.now().minusDays(10))
                        .endDate(OffsetDateTime.now().minusDays(9))));

    persistProcessInstances(
        Stream.concat(Stream.of(recentWithIncident, recentClean), oldWithIncidents.stream())
            .toList());

    // when
    final List<MapResultEntryDto> buckets =
        evaluateMapData(FAILURE_RATE_BY_VERSION_REPORT_ID, rollingEndDateFilter(1L, DateUnit.DAYS));

    // then only the 2 in-window instances count → 1/2 = 50%
    assertThat(rateForVersion(buckets, "1")).isEqualTo(50.0);
  }

  @Test
  void shouldReturnOnlyScopedProcessVersionsWhenDefinitionFilterApplied() {
    final String procKeyA = "proc-fr-a";
    final String procKeyB = "proc-fr-b";

    // procKeyA version 1: 2 instances, 1 with incident → 50%
    final ProcessInstanceDto aV1WithIncident =
        resolvedIncident(agenticInstance(procKeyA, "1", 100L, 50L));
    final ProcessInstanceDto aV1Clean = agenticInstance(procKeyA, "1", 100L, 50L).build();
    // procKeyA version 2: 4 instances, 1 with incident → 25%
    final ProcessInstanceDto aV2WithIncident =
        resolvedIncident(agenticInstance(procKeyA, "2", 100L, 50L));
    final List<ProcessInstanceDto> aV2Clean =
        repeat(3, () -> agenticInstance(procKeyA, "2", 100L, 50L).build());
    // procKeyB version 1: 5 instances all with incidents — must not inflate procKeyA's version 1
    final List<ProcessInstanceDto> bV1WithIncidents =
        repeat(5, () -> resolvedIncident(agenticInstance(procKeyB, "1", 100L, 50L)));

    persistProcessInstances(
        Stream.of(List.of(aV1WithIncident, aV1Clean, aV2WithIncident), aV2Clean, bV1WithIncidents)
            .flatMap(List::stream)
            .toList());

    // when
    final List<MapResultEntryDto> buckets =
        evaluateMapData(
            FAILURE_RATE_BY_VERSION_REPORT_ID,
            withDefinitions(List.of(new ReportDataDefinitionDto(procKeyA))));

    // then scope to procKeyA → only its versions appear, each with its own scoped rate
    assertThat(buckets).extracting(MapResultEntryDto::getKey).containsExactlyInAnyOrder("1", "2");
    assertThat(rateForVersion(buckets, "1")).isEqualTo(50.0);
    assertThat(rateForVersion(buckets, "2")).isEqualTo(25.0);
  }

  @Test
  void shouldReturnNoErrorWhenNoCompletedAgenticInstancesMatch() {
    // given only running agentic and completed non-agentic instances — nothing to group
    final ProcessInstanceDto running =
        agenticInstance(PROC_KEY, "1", 100L, 50L)
            .state(ProcessInstanceConstants.ACTIVE_STATE)
            .endDate(null)
            .duration(null)
            .build();
    final ProcessInstanceDto nonAgentic = completedInstance(PROC_KEY).build();

    persistProcessInstances(List.of(running, nonAgentic));

    // when
    final List<MapResultEntryDto> buckets =
        evaluateMapData(FAILURE_RATE_BY_VERSION_REPORT_ID, noExtraFilters());

    // then evaluation succeeds with empty buckets (no NaN, no exception)
    assertThat(buckets).isEmpty();
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /**
   * Builds a minimal completed agentic {@link ProcessInstanceDto} with one {@link AgentInstanceDto}
   * and the given total token counts.
   */
  private ProcessInstanceDto.ProcessInstanceDtoBuilder agenticInstanceWithTokens(
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

  /**
   * Builds a minimal completed agentic {@link ProcessInstanceDto} with one {@link AgentInstanceDto}
   * and the given execution duration (ms). Use this overload for duration-focused tests.
   */
  private ProcessInstanceDto.ProcessInstanceDtoBuilder agenticInstanceWithDuration(
      final String processDefinitionKey, final long duration) {
    return agenticInstanceWithTokens(processDefinitionKey, 0L, 0L).duration(duration);
  }

  /**
   * Builds a completed agentic {@link ProcessInstanceDto} pinned to a specific process definition
   * version, so reports grouped by version see distinct buckets.
   */
  private ProcessInstanceDto.ProcessInstanceDtoBuilder agenticInstance(
      final String processDefinitionKey,
      final String processDefinitionVersion,
      final long inputTokens,
      final long outputTokens) {
    return agenticInstanceWithTokens(processDefinitionKey, inputTokens, outputTokens)
        .processDefinitionVersion(processDefinitionVersion)
        .processDefinitionId(processDefinitionKey + ":" + processDefinitionVersion + ":1");
  }

  private List<ProcessInstanceDto> repeat(
      final int count, final java.util.function.Supplier<ProcessInstanceDto> supplier) {
    return java.util.stream.IntStream.range(0, count).mapToObj(i -> supplier.get()).toList();
  }

  private ProcessInstanceDto resolvedIncident(
      final ProcessInstanceDto.ProcessInstanceDtoBuilder builder) {
    final String instanceId = UUID.randomUUID().toString();
    return builder
        .processInstanceId(instanceId)
        .incidents(
            List.of(
                IncidentDto.builder()
                    .incidentStatus(IncidentStatus.RESOLVED)
                    .processInstanceId(instanceId)
                    .build()))
        .build();
  }

  private List<MapResultEntryDto> evaluateMapData(
      final String reportId, final AdditionalProcessReportEvaluationFilterDto filterDto) {
    return evaluateMap(reportId, filterDto).getFirstMeasureData();
  }

  private Double rateForVersion(
      final List<MapResultEntryDto> buckets, final String processDefinitionVersion) {
    return buckets.stream()
        .filter(entry -> processDefinitionVersion.equals(entry.getKey()))
        .map(MapResultEntryDto::getValue)
        .findFirst()
        .orElse(null);
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

  private MapCommandResult evaluateMap(
      final String reportId, final AdditionalProcessReportEvaluationFilterDto filterDto) {
    final var result =
        evaluationService.evaluateSavedReportWithAdditionalFilters(
            USER_ID, UTC, reportId, filterDto, null);
    return (MapCommandResult)
        ((io.camunda.optimize.dto.optimize.query.report.SingleReportEvaluationResult<?>)
                result.getEvaluationResult())
            .getFirstCommandResult();
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
