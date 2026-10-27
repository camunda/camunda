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
import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.NoneDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedInstancesOnlyFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.EndDateGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import io.camunda.optimize.service.db.reader.DashboardReader;
import io.camunda.optimize.service.db.writer.DashboardWriter;
import io.camunda.optimize.service.db.writer.ReportWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class BusinessValueVolumeReportsTest {

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
  void shouldSeedWorkHandledTotalAsAggregateNumber() {
    // when
    underTest.reconcile();

    // then a single-number aggregate report is seeded with process-instance frequency,
    // no groupBy, no distributedBy — mapping to PROCESS_INSTANCE_FREQUENCY_GROUP_BY_NONE
    final ProcessReportDataDto data =
        captureReportData(
            BusinessValueDashboardService.WORK_HANDLED_TOTAL_REPORT_ID,
            BusinessValueDashboardService.KPI_COMPLETED_INSTANCES_NAME,
            BusinessValueDashboardService.KPI_COMPLETED_INSTANCES_DESCRIPTION);

    assertThat(data.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(data.getView().getFirstProperty()).isEqualTo(ViewProperty.FREQUENCY);
    assertThat(data.getGroupBy()).isInstanceOf(NoneGroupByDto.class);
    assertThat(data.getDistributedBy()).isInstanceOf(NoneDistributedByDto.class);
    assertThat(data.getVisualization()).isEqualTo(ProcessVisualization.NUMBER);
    assertThat(data.getFilter()).hasAtLeastOneElementOfType(CompletedInstancesOnlyFilterDto.class);
    assertThat(data.isBusinessValueReport()).isTrue();
  }

  @Test
  void shouldSeedCountByProcessAsTopNHorizontalBar() {
    // when
    underTest.reconcile();

    // then the per-process breakdown maps to
    // PROCESS_INSTANCE_FREQUENCY_GROUP_BY_PROCESS_DEFINITION_KEY
    // (MAP) rendered as horizontal BAR sorted DESC — highest-volume processes surface first
    final ProcessReportDataDto data =
        captureReportData(
            BusinessValueDashboardService.COUNT_BY_PROCESS_REPORT_ID,
            BusinessValueDashboardService.KPI_TOP_BY_VOLUME_NAME,
            BusinessValueDashboardService.KPI_TOP_BY_VOLUME_DESCRIPTION);

    assertThat(data.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(data.getView().getFirstProperty()).isEqualTo(ViewProperty.FREQUENCY);
    assertThat(data.getGroupBy())
        .isInstanceOf(
            io.camunda.optimize.dto.optimize.query.report.single.process.group
                .ProcessDefinitionKeyGroupByDto.class);
    assertThat(data.getDistributedBy()).isInstanceOf(NoneDistributedByDto.class);
    assertThat(data.getVisualization()).isEqualTo(ProcessVisualization.BAR);
    assertThat(data.getConfiguration().getHorizontalBar()).isTrue();
    assertThat(data.getConfiguration().getSorting()).isPresent();
    assertThat(data.getConfiguration().getSorting().get().getOrder())
        .contains(io.camunda.optimize.dto.optimize.query.sorting.SortOrder.DESC);
    assertThat(data.getFilter()).hasAtLeastOneElementOfType(CompletedInstancesOnlyFilterDto.class);
    assertThat(data.isBusinessValueReport()).isTrue();
  }

  @Test
  void shouldSeedCountByDateAsAutomaticBucketLineTrend() {
    // when
    underTest.reconcile();

    // then completed-instance volume trend uses AUTOMATIC bucket unit so the x-axis granularity
    // follows the caller's date range at evaluate time
    final ProcessReportDataDto data =
        captureReportData(
            BusinessValueDashboardService.COUNT_BY_DATE_REPORT_ID,
            BusinessValueDashboardService.KPI_COMPLETED_INSTANCES_OVER_TIME_NAME,
            BusinessValueDashboardService.KPI_COMPLETED_INSTANCES_OVER_TIME_DESCRIPTION);

    assertThat(data.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(data.getView().getFirstProperty()).isEqualTo(ViewProperty.FREQUENCY);
    assertThat(data.getGroupBy()).isInstanceOf(EndDateGroupByDto.class);
    assertThat(((EndDateGroupByDto) data.getGroupBy()).getValue().getUnit())
        .isEqualTo(AggregateByDateUnit.AUTOMATIC);
    assertThat(data.getDistributedBy()).isInstanceOf(NoneDistributedByDto.class);
    assertThat(data.getVisualization()).isEqualTo(ProcessVisualization.LINE);
    assertThat(data.getFilter()).hasAtLeastOneElementOfType(CompletedInstancesOnlyFilterDto.class);
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
