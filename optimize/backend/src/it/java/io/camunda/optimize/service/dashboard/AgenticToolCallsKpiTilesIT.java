/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.dashboard;

import static io.camunda.optimize.AgenticInstanceFixtures.PROC_KEY;
import static io.camunda.optimize.AgenticInstanceFixtures.agenticInstanceWithToolCalls;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.KPI_TOOL_CALLS_DESCRIPTION;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.KPI_TOOL_CALLS_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticReportFilters.noExtraFilters;
import static io.camunda.optimize.service.dashboard.AgenticReportFilters.rollingEndDateFilter;
import static io.camunda.optimize.service.dashboard.AgenticReportFilters.withDefinitions;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.dto.optimize.ProcessInstanceConstants;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.reader.ReportReader;
import io.camunda.optimize.service.report.ReportEvaluationService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgenticToolCallsKpiTilesIT extends AbstractBrokerlessZeebeCCSMIT {

  private AgenticReportEvaluator reports;
  private ReportReader reportReader;

  @BeforeEach
  void setUp() {
    embeddedOptimizeExtension.getBean(AgenticControlDashboardService.class).reconcile();
    reports =
        new AgenticReportEvaluator(
            embeddedOptimizeExtension.getBean(ReportEvaluationService.class));
    reportReader = embeddedOptimizeExtension.getBean(ReportReader.class);
  }

  @Test
  void shouldPersistDescriptionAsSubtitleForToolCallsTile() {
    // given the agentic reports were seeded by the reconcile above

    // then the total tool calls NUMBER tile carries its description as the subtitle override
    final ProcessReportDataDto data =
        (ProcessReportDataDto)
            reportReader.getReport(KPI_TOOL_CALLS_REPORT_ID).orElseThrow().getData();
    assertThat(data.getConfiguration().getSubtitle()).isEqualTo(KPI_TOOL_CALLS_DESCRIPTION);
  }

  @Test
  void shouldSumToolCallsAcrossAllCompletedAgenticInstances() {
    // given completed agentic instances with known tool-call totals across two processes
    final ProcessInstanceDto a = agenticInstanceWithToolCalls(PROC_KEY, 5L).build();
    final ProcessInstanceDto b = agenticInstanceWithToolCalls(PROC_KEY, 10L).build();
    final ProcessInstanceDto c = agenticInstanceWithToolCalls("other-agent-process", 15L).build();
    // running agentic — excluded by completedInstancesOnly
    final ProcessInstanceDto running =
        agenticInstanceWithToolCalls(PROC_KEY, 100L)
            .state(ProcessInstanceConstants.ACTIVE_STATE)
            .endDate(null)
            .duration(null)
            .build();
    // completed non-agentic — excluded by hasAgentInstances
    final ProcessInstanceDto nonAgentic = completedInstance(PROC_KEY).build();

    persistProcessInstances(List.of(a, b, c, running, nonAgentic));

    // fleet view (L0, no definition scope): 5 + 10 + 15 = 30
    assertThat(reports.evaluateNumber(KPI_TOOL_CALLS_REPORT_ID, noExtraFilters())).isEqualTo(30.0);
  }

  @Test
  void shouldScopeToolCallsToSelectedProcessDefinition() {
    final String procKeyA = "proc-tools-a";
    final String procKeyB = "proc-tools-b";

    persistProcessInstances(
        List.of(
            agenticInstanceWithToolCalls(procKeyA, 7L).build(),
            agenticInstanceWithToolCalls(procKeyA, 8L).build(),
            agenticInstanceWithToolCalls(procKeyB, 100L).build()));

    // drill-down (L1) to procKeyA only: 7 + 8 = 15 (procKeyB excluded)
    assertThat(
            reports.evaluateNumber(
                KPI_TOOL_CALLS_REPORT_ID,
                withDefinitions(List.of(new ReportDataDefinitionDto(procKeyA)))))
        .isEqualTo(15.0);
  }

  @Test
  void shouldScopeToolCallsByDateFilter() {
    // capture one reference point so the window math stays deterministic across clock boundaries
    final OffsetDateTime now = OffsetDateTime.now();
    // within last-1-day window
    final ProcessInstanceDto recent =
        agenticInstanceWithToolCalls(PROC_KEY, 12L)
            .startDate(now.minusHours(3))
            .endDate(now.minusHours(2))
            .build();
    // outside the window — must not be added to the total
    final ProcessInstanceDto old =
        agenticInstanceWithToolCalls(PROC_KEY, 999L)
            .startDate(now.minusDays(10))
            .endDate(now.minusDays(9))
            .build();

    persistProcessInstances(List.of(recent, old));

    assertThat(
            reports.evaluateNumber(
                KPI_TOOL_CALLS_REPORT_ID, rollingEndDateFilter(1L, DateUnit.DAYS)))
        .isEqualTo(12.0);
  }
}
