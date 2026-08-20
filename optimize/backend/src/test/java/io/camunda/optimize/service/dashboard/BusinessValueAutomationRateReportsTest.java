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
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.NoneDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedInstancesOnlyFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessDefinitionKeyGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import io.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import io.camunda.optimize.service.db.reader.DashboardReader;
import io.camunda.optimize.service.db.writer.DashboardWriter;
import io.camunda.optimize.service.db.writer.ReportWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class BusinessValueAutomationRateReportsTest {

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
  void shouldSeedAggregateAutomationRateAsSingleNumber() {
    // when
    underTest.reconcile();

    // then the aggregate tile maps to PROCESS_INSTANCE_AUTOMATION_RATE_GROUP_BY_NONE (NUMBER)
    final ProcessReportDataDto data =
        captureReportData(
            BusinessValueDashboardService.AUTOMATION_RATE_AGGREGATE_REPORT_ID,
            BusinessValueDashboardService.KPI_AGGREGATED_AUTOMATION_RATE_NAME,
            BusinessValueDashboardService.KPI_AUTOMATION_RATE_DESCRIPTION);

    assertThat(data.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(data.getView().getFirstProperty()).isEqualTo(ViewProperty.AUTOMATION_RATE);
    assertThat(data.getGroupBy()).isInstanceOf(NoneGroupByDto.class);
    assertThat(data.getDistributedBy()).isInstanceOf(NoneDistributedByDto.class);
    assertThat(data.getVisualization()).isEqualTo(ProcessVisualization.NUMBER);
    assertThat(data.getFilter()).hasAtLeastOneElementOfType(CompletedInstancesOnlyFilterDto.class);
    assertThat(data.isBusinessValueReport()).isTrue();
  }

  @Test
  void shouldSeedAutomationRateByProcessAsTopNHorizontalBar() {
    // when
    underTest.reconcile();

    // then per-process tile is a horizontal BAR sorted DESC so top-N processes surface first
    final ProcessReportDataDto data =
        captureReportData(
            BusinessValueDashboardService.AUTOMATION_RATE_BY_PROCESS_REPORT_ID,
            BusinessValueDashboardService.KPI_AUTOMATION_RATE_BY_PROCESS_NAME,
            BusinessValueDashboardService.KPI_AUTOMATION_RATE_BY_PROCESS_DESCRIPTION);

    assertThat(data.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(data.getView().getFirstProperty()).isEqualTo(ViewProperty.AUTOMATION_RATE);
    assertThat(data.getGroupBy()).isInstanceOf(ProcessDefinitionKeyGroupByDto.class);
    assertThat(data.getDistributedBy()).isInstanceOf(NoneDistributedByDto.class);
    assertThat(data.getVisualization()).isEqualTo(ProcessVisualization.BAR);
    assertThat(data.getConfiguration().getHorizontalBar()).isTrue();
    assertThat(data.getConfiguration().getSorting()).isPresent();
    assertThat(data.getConfiguration().getSorting().get().getOrder()).contains(SortOrder.DESC);
    assertThat(data.getFilter()).hasAtLeastOneElementOfType(CompletedInstancesOnlyFilterDto.class);
    assertThat(data.isBusinessValueReport()).isTrue();
  }

  private ProcessReportDataDto captureReportData(
      final String reportId, final String name, final String description) {
    final ArgumentCaptor<ProcessReportDataDto> captor =
        ArgumentCaptor.forClass(ProcessReportDataDto.class);
    verify(reportWriter)
        .createOrUpdateSingleProcessReport(
            eq(reportId), isNull(), captor.capture(), eq(name), eq(description), isNull());
    return captor.getValue();
  }
}
