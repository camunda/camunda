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
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.KPI_INCIDENT_RATE_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.KPI_MEDIAN_TOKENS_REPORT_ID;
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
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.RollingDateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceEndDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.result.MeasureDto;
import io.camunda.optimize.service.db.report.result.NumberCommandResult;
import io.camunda.optimize.service.report.ReportEvaluationService;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    // 1 instance with resolved incident / 2 completed agentic instances = 50%
    assertThat(result).isEqualTo(50.0);
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

    // 1 instance with resolved incident / 1 completed agentic instance in window = 100%
    assertThat(evaluateNumber(KPI_INCIDENT_RATE_REPORT_ID, rollingEndDateFilter(1L, DateUnit.DAYS)))
        .isEqualTo(100.0);
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
  // Scoped baseline: denominator must exclude running and non-agentic instances
  // ---------------------------------------------------------------------------

  @Test
  void shouldExcludeRunningInstancesFromIncidentRateDenominator() {
    // given: 1 completed agentic with incident, 1 completed agentic without,
    //        plus 5 running agentic instances (no incidents) — running must not count
    final String withIncidentId = UUID.randomUUID().toString();
    final ProcessInstanceDto withIncident =
        agenticInstance(PROC_KEY, 100L, 50L)
            .processInstanceId(withIncidentId)
            .incidents(
                List.of(
                    IncidentDto.builder()
                        .incidentStatus(IncidentStatus.RESOLVED)
                        .processInstanceId(withIncidentId)
                        .build()))
            .build();
    final ProcessInstanceDto completedClean = agenticInstance(PROC_KEY, 100L, 50L).build();
    final List<ProcessInstanceDto> runningInstances =
        java.util.stream.IntStream.range(0, 5)
            .mapToObj(
                i ->
                    agenticInstance(PROC_KEY, 100L, 50L)
                        .state(ProcessInstanceConstants.ACTIVE_STATE)
                        .endDate(null)
                        .duration(null)
                        .build())
            .toList();

    persistProcessInstances(
        java.util.stream.Stream.concat(
                java.util.stream.Stream.of(withIncident, completedClean),
                runningInstances.stream())
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
        agenticInstance(PROC_KEY, 100L, 50L)
            .processInstanceId(withIncidentId)
            .incidents(
                List.of(
                    IncidentDto.builder()
                        .incidentStatus(IncidentStatus.RESOLVED)
                        .processInstanceId(withIncidentId)
                        .build()))
            .build();
    final ProcessInstanceDto completedAgentic = agenticInstance(PROC_KEY, 100L, 50L).build();
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
        agenticInstance(PROC_KEY, 100L, 50L)
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
        agenticInstance(PROC_KEY, 100L, 50L)
            .startDate(OffsetDateTime.now().minusHours(3))
            .endDate(OffsetDateTime.now().minusHours(2))
            .build();
    final List<ProcessInstanceDto> oldWithIncidents =
        java.util.stream.IntStream.range(0, 10)
            .mapToObj(
                i -> {
                  final String id = UUID.randomUUID().toString();
                  return agenticInstance(PROC_KEY, 100L, 50L)
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
    assertThat(
            evaluateNumber(
                KPI_INCIDENT_RATE_REPORT_ID, rollingEndDateFilter(1L, DateUnit.DAYS)))
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
        agenticInstance(procKeyA, 100L, 50L)
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
            .mapToObj(i -> agenticInstance(procKeyA, 100L, 50L).build())
            .toList();
    final List<ProcessInstanceDto> bWithIncidents =
        java.util.stream.IntStream.range(0, 5)
            .mapToObj(
                i -> {
                  final String id = UUID.randomUUID().toString();
                  return agenticInstance(procKeyB, 100L, 50L)
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

  private Double evaluateNumber(
      final String reportId, final AdditionalProcessReportEvaluationFilterDto filterDto) {
    final var result =
        evaluationService.evaluateSavedReportWithAdditionalFilters(
            USER_ID, UTC, reportId, filterDto, null);
    final NumberCommandResult commandResult =
        (NumberCommandResult)
            ((io.camunda.optimize.dto.optimize.query.report.SingleReportEvaluationResult<?>)
                    result.getEvaluationResult())
                .getFirstCommandResult();
    final List<MeasureDto<Double>> measures = commandResult.getMeasures();
    return measures.isEmpty() ? null : measures.getFirst().getData();
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
}
