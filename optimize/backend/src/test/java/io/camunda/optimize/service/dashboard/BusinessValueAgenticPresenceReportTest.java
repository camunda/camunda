/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.dashboard;

import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.BUSINESS_VALUE_DASHBOARD_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.NoneDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedInstancesOnlyFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.HasAgentInstancesFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessDefinitionKeyGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import io.camunda.optimize.service.db.reader.DashboardReader;
import io.camunda.optimize.service.db.writer.DashboardWriter;
import io.camunda.optimize.service.db.writer.ReportWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class BusinessValueAgenticPresenceReportTest {

  private final DashboardWriter dashboardWriter = mock(DashboardWriter.class);
  private final DashboardReader dashboardReader = mock(DashboardReader.class);
  private final ReportWriter reportWriter = mock(ReportWriter.class);
  private final ConfigurationService configurationService = mock(ConfigurationService.class);

  private final BusinessValueDashboardService underTest =
      new BusinessValueDashboardService(
          dashboardWriter, dashboardReader, reportWriter, configurationService);

  @BeforeEach
  void setUp() {
    when(reportWriter.createOrUpdateSingleProcessReport(
            any(), isNull(), any(), any(), any(), isNull()))
        .thenAnswer(invocation -> new IdResponseDto(invocation.getArgument(0)));
    when(dashboardReader.getDashboard(BUSINESS_VALUE_DASHBOARD_ID)).thenReturn(Optional.empty());
  }

  @Test
  void shouldSeedAgentPresenceAsPerProcessPie() {
    // when
    underTest.reconcile();

    // then agent presence uses the only registered per-process AGENT_TOTAL_TOKENS execution plan
    // (view=AGENT_INSTANCE + groupBy=PROCESS_DEFINITION_KEY + distributedBy=NONE) rendered as PIE,
    // aggregated with SUM to serve as a non-zero presence indicator per process definition
    final ProcessReportDataDto data =
        captureReportData(
            BusinessValueDashboardService.AGENT_PRESENCE_BY_PROCESS_REPORT_ID,
            BusinessValueDashboardService.KPI_AGENTIC_PROCESSES_NAME,
            BusinessValueDashboardService.KPI_AGENTIC_PROCESSES_DESCRIPTION);

    assertThat(data.getView().getEntity()).isEqualTo(ProcessViewEntity.AGENT_INSTANCE);
    assertThat(data.getView().getFirstProperty()).isEqualTo(ViewProperty.TOTAL_TOKENS);
    assertThat(data.getGroupBy()).isInstanceOf(ProcessDefinitionKeyGroupByDto.class);
    assertThat(data.getDistributedBy()).isInstanceOf(NoneDistributedByDto.class);
    assertThat(data.getVisualization()).isEqualTo(ProcessVisualization.PIE);
    assertThat(data.getConfiguration().getAggregationTypes())
        .containsExactly(new AggregationDto(AggregationType.SUM));

    // both scope filters — completed instances + agent-bearing instances only
    assertThat(data.getFilter()).hasAtLeastOneElementOfType(CompletedInstancesOnlyFilterDto.class);
    assertThat(data.getFilter()).hasAtLeastOneElementOfType(HasAgentInstancesFilterDto.class);

    assertThat(data.isBusinessValueReport()).isTrue();
  }

  private ProcessReportDataDto captureReportData(
      final String reportId, final String nameKey, final String descriptionKey) {
    final ArgumentCaptor<ProcessReportDataDto> captor =
        ArgumentCaptor.forClass(ProcessReportDataDto.class);
    verify(reportWriter)
        .createOrUpdateSingleProcessReport(
            eq(reportId), isNull(), captor.capture(), eq(nameKey), eq(descriptionKey), isNull());
    return captor.getValue();
  }
}
