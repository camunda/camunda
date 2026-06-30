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
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.KPI_COMPLETED_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticReportFilters.noExtraFilters;
import static io.camunda.optimize.service.dashboard.AgenticReportFilters.rollingEndDateFilter;
import static io.camunda.optimize.service.dashboard.AgenticReportFilters.withDefinitions;
import static io.camunda.optimize.service.dashboard.AgenticReportFilters.withDefinitionsAndDateFilter;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.dto.optimize.ProcessInstanceConstants;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import io.camunda.optimize.service.report.ReportEvaluationService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgenticExecutionKpiTilesIT extends AbstractBrokerlessZeebeCCSMIT {

  private AgenticReportEvaluator reports;

  @BeforeEach
  void setUp() {
    embeddedOptimizeExtension.getBean(AgenticControlDashboardService.class).reconcile();
    reports =
        new AgenticReportEvaluator(
            embeddedOptimizeExtension.getBean(ReportEvaluationService.class));
  }

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

    final Double result = reports.evaluateNumber(KPI_COMPLETED_REPORT_ID, noExtraFilters());
    assertThat(result).isEqualTo(2.0);
  }

  @Test
  void shouldCountAllInstancesWhenNoDateFilterApplied() {
    // instances spread across a wide range — all should be counted without a date filter
    final OffsetDateTime now = OffsetDateTime.now();
    final ProcessInstanceDto recent =
        agenticInstanceWithTokens(PROC_KEY, 100L, 50L)
            .startDate(now.minusHours(1))
            .endDate(now.minusMinutes(30))
            .build();
    final ProcessInstanceDto older =
        agenticInstanceWithTokens(PROC_KEY, 100L, 50L)
            .startDate(now.minusDays(30))
            .endDate(now.minusDays(29))
            .build();
    final ProcessInstanceDto oldest =
        agenticInstanceWithTokens(PROC_KEY, 100L, 50L)
            .startDate(now.minusDays(365))
            .endDate(now.minusDays(364))
            .build();

    persistProcessInstances(List.of(recent, older, oldest));

    assertThat(reports.evaluateNumber(KPI_COMPLETED_REPORT_ID, noExtraFilters())).isEqualTo(3.0);
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

    assertThat(
            reports.evaluateNumber(
                KPI_COMPLETED_REPORT_ID, rollingEndDateFilter(2L, DateUnit.HOURS)))
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

    assertThat(
            reports.evaluateNumber(
                KPI_COMPLETED_REPORT_ID, rollingEndDateFilter(1L, DateUnit.DAYS)))
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

    assertThat(
            reports.evaluateNumber(
                KPI_COMPLETED_REPORT_ID, rollingEndDateFilter(1L, DateUnit.WEEKS)))
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

    assertThat(
            reports.evaluateNumber(
                KPI_COMPLETED_REPORT_ID, rollingEndDateFilter(1L, DateUnit.MONTHS)))
        .isEqualTo(1.0);
  }

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
    assertThat(reports.evaluateNumber(KPI_COMPLETED_REPORT_ID, noExtraFilters())).isEqualTo(3.0);
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
            reports.evaluateNumber(
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
            reports.evaluateNumber(
                KPI_COMPLETED_REPORT_ID,
                withDefinitions(
                    List.of(
                        new ReportDataDefinitionDto(procKeyA),
                        new ReportDataDefinitionDto(procKeyB)))))
        .isEqualTo(2.0);
  }

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
    assertThat(reports.evaluateNumber(KPI_COMPLETED_REPORT_ID, combined)).isEqualTo(1.0);
  }
}
