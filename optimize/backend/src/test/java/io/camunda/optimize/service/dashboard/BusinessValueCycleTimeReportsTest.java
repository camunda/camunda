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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class BusinessValueCycleTimeReportsTest {

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
  void shouldSeedDurationByProcessAsTopNHorizontalBar() {
    // when
    underTest.reconcile();

    // then per-process average cycle time is a horizontal BAR sorted DESC so slowest processes
    // surface first, with AVG aggregation
    final ProcessReportDataDto data =
        captureReportData(
            BusinessValueDashboardService.DURATION_BY_PROCESS_REPORT_ID,
            BusinessValueDashboardService.KPI_TOP_BY_CYCLE_TIME_NAME,
            BusinessValueDashboardService.KPI_TOP_BY_CYCLE_TIME_DESCRIPTION);

    assertThat(data.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(data.getView().getFirstProperty()).isEqualTo(ViewProperty.DURATION);
    assertThat(data.getGroupBy())
        .isInstanceOf(
            io.camunda.optimize.dto.optimize.query.report.single.process.group
                .ProcessDefinitionKeyGroupByDto.class);
    assertThat(data.getDistributedBy()).isInstanceOf(NoneDistributedByDto.class);
    assertThat(data.getVisualization()).isEqualTo(ProcessVisualization.BAR);
    assertThat(data.getConfiguration().getAggregationTypes())
        .containsExactly(new AggregationDto(AggregationType.AVERAGE));
    assertThat(data.getConfiguration().getHorizontalBar()).isTrue();
    assertThat(data.getConfiguration().getSorting()).isPresent();
    assertThat(data.getConfiguration().getSorting().get().getOrder())
        .contains(io.camunda.optimize.dto.optimize.query.sorting.SortOrder.DESC);
    assertThat(data.getFilter()).hasAtLeastOneElementOfType(CompletedInstancesOnlyFilterDto.class);
    assertThat(data.isBusinessValueReport()).isTrue();
  }

  @Test
  void shouldSeedDurationPercentilesAsMultiAggregationNumber() {
    // when
    underTest.reconcile();

    // then the L1 cycle-time distribution report carries AVG + P50 + P95 measures in one seed
    final ProcessReportDataDto data =
        captureReportData(
            BusinessValueDashboardService.DURATION_PERCENTILES_REPORT_ID,
            BusinessValueDashboardService.KPI_CYCLE_TIME_DISTRIBUTION_NAME,
            BusinessValueDashboardService.KPI_CYCLE_TIME_DISTRIBUTION_DESCRIPTION);

    assertThat(data.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(data.getView().getFirstProperty()).isEqualTo(ViewProperty.DURATION);
    assertThat(data.getGroupBy()).isInstanceOf(NoneGroupByDto.class);
    assertThat(data.getDistributedBy()).isInstanceOf(NoneDistributedByDto.class);
    assertThat(data.getVisualization()).isEqualTo(ProcessVisualization.NUMBER);

    final List<AggregationDto> aggTypes =
        List.copyOf(data.getConfiguration().getAggregationTypes());
    assertThat(aggTypes).hasSize(3);
    assertThat(aggTypes.get(0).getType()).isEqualTo(AggregationType.AVERAGE);
    assertThat(aggTypes.get(1).getType()).isEqualTo(AggregationType.PERCENTILE);
    assertThat(aggTypes.get(1).getValue()).isEqualTo(50.0);
    assertThat(aggTypes.get(2).getType()).isEqualTo(AggregationType.PERCENTILE);
    assertThat(aggTypes.get(2).getValue()).isEqualTo(95.0);

    assertThat(data.getFilter()).hasAtLeastOneElementOfType(CompletedInstancesOnlyFilterDto.class);
    assertThat(data.isBusinessValueReport()).isTrue();
  }

  @Test
  void shouldSeedDurationByDateAsAutomaticBucketAverageLine() {
    // when
    underTest.reconcile();

    // then cycle-time trend uses AUTOMATIC bucket unit so the x-axis granularity follows the
    // caller's date range at evaluate time
    final ProcessReportDataDto data =
        captureReportData(
            BusinessValueDashboardService.DURATION_BY_DATE_REPORT_ID,
            BusinessValueDashboardService.KPI_CYCLE_TIME_HISTORY_NAME,
            BusinessValueDashboardService.KPI_CYCLE_TIME_HISTORY_DESCRIPTION);

    assertThat(data.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(data.getView().getFirstProperty()).isEqualTo(ViewProperty.DURATION);
    assertThat(data.getGroupBy()).isInstanceOf(EndDateGroupByDto.class);
    assertThat(((EndDateGroupByDto) data.getGroupBy()).getValue().getUnit())
        .isEqualTo(AggregateByDateUnit.AUTOMATIC);
    assertThat(data.getDistributedBy()).isInstanceOf(NoneDistributedByDto.class);
    assertThat(data.getVisualization()).isEqualTo(ProcessVisualization.LINE);
    assertThat(data.getConfiguration().getAggregationTypes())
        .containsExactly(new AggregationDto(AggregationType.AVERAGE));
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
