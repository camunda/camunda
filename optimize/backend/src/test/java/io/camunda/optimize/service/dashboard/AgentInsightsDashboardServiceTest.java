/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.dashboard;

import static io.camunda.optimize.service.dashboard.AgentInsightsDashboardService.AGENT_INSIGHTS_DASHBOARD_ID;
import static io.camunda.optimize.service.dashboard.AgentInsightsDashboardService.AGENT_INVENTORY_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgentInsightsDashboardService.INCIDENT_RATE_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgentInsightsDashboardService.PROCESS_DURATION_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgentInsightsDashboardService.TOTAL_TOKENS_REPORT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionUpdateDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedInstancesOnlyFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.HasAgentInstancesFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ResolvedIncidentFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import io.camunda.optimize.service.db.reader.DashboardReader;
import io.camunda.optimize.service.db.writer.DashboardWriter;
import io.camunda.optimize.service.db.writer.ReportWriter;
import io.camunda.optimize.service.util.configuration.AgenticPhase2Configuration;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.EntityConfiguration;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentInsightsDashboardServiceTest {

  private final DashboardWriter dashboardWriter = mock(DashboardWriter.class);
  private final DashboardReader dashboardReader = mock(DashboardReader.class);
  private final ReportWriter reportWriter = mock(ReportWriter.class);
  private final ConfigurationService configurationService = mock(ConfigurationService.class);
  private final EntityConfiguration entityConfiguration = new EntityConfiguration();
  private final AgenticPhase2Configuration phase2Configuration = new AgenticPhase2Configuration();

  private final AgentInsightsDashboardService underTest =
      new AgentInsightsDashboardService(
          dashboardWriter, dashboardReader, reportWriter, configurationService);

  @BeforeEach
  void setUp() {
    entityConfiguration.setCreateOnStartup(true);
    when(configurationService.getEntityConfiguration()).thenReturn(entityConfiguration);
    when(configurationService.getAgenticPhase2Configuration()).thenReturn(phase2Configuration);
    when(dashboardReader.getDashboard(AGENT_INSIGHTS_DASHBOARD_ID)).thenReturn(Optional.empty());
  }

  @Test
  void shouldSeedOnStartupWhenPhase2IsEnabled() {
    // given
    phase2Configuration.setEnabled(true);

    // when
    underTest.init();

    // then
    verify(dashboardWriter).saveDashboard(any());
  }

  @Test
  void shouldNotSeedOnStartupWhenPhase2IsDisabled() {
    // given
    phase2Configuration.setEnabled(false);

    // when
    underTest.init();

    // then
    verifyNoInteractions(dashboardWriter, reportWriter);
  }

  @Test
  void shouldSaveDashboardWithOneTilePerReport() {
    // when
    underTest.reconcile();

    // then
    final ArgumentCaptor<DashboardDefinitionRestDto> captor =
        ArgumentCaptor.forClass(DashboardDefinitionRestDto.class);
    verify(dashboardWriter).saveDashboard(captor.capture());
    final DashboardDefinitionRestDto dashboard = captor.getValue();
    assertThat(dashboard.getId()).isEqualTo(AGENT_INSIGHTS_DASHBOARD_ID);
    assertThat(dashboard.isAgentInsightsDashboard()).isTrue();
    assertThat(dashboard.isSystemGeneratedDashboard()).isTrue();
    assertThat(dashboard.getCollectionId()).isNull();
    assertThat(dashboard.getTiles())
        .extracting(DashboardReportTileDto::getId, DashboardReportTileDto::getConfiguration)
        .containsExactly(
            tuple(TOTAL_TOKENS_REPORT_ID, Map.of("section", "summary", "tile", "totalTokens")),
            tuple(
                PROCESS_DURATION_REPORT_ID,
                Map.of("section", "summary", "tile", "processDuration")),
            tuple(INCIDENT_RATE_REPORT_ID, Map.of("section", "summary", "tile", "incidentRate")),
            tuple(
                AGENT_INVENTORY_REPORT_ID,
                Map.of("section", "inventory", "tile", "agentInventory")));
  }

  @Test
  void shouldUpdateExistingDashboardInsteadOfSavingASecondOne() {
    // given
    when(dashboardReader.getDashboard(AGENT_INSIGHTS_DASHBOARD_ID))
        .thenReturn(Optional.of(new DashboardDefinitionRestDto()));

    // when
    underTest.reconcile();

    // then
    verify(dashboardWriter, never()).saveDashboard(any());
    verify(dashboardWriter)
        .updateDashboard(any(DashboardDefinitionUpdateDto.class), eq(AGENT_INSIGHTS_DASHBOARD_ID));
  }

  @Test
  void shouldSeedTotalTokensAsAgentRunReportOverCompletedRuns() {
    // when
    underTest.reconcile();

    // then
    final ProcessReportDataDto data = capturedReportData(TOTAL_TOKENS_REPORT_ID);
    assertThat(data.isAgentInsightsReport()).isTrue();
    assertThat(data.getDefinitions()).isEmpty();
    assertThat(data.getView().getEntity()).isEqualTo(ProcessViewEntity.AGENT_RUN);
    assertThat(data.getView().getProperties())
        .containsExactly(
            ViewProperty.FREQUENCY,
            ViewProperty.TOTAL_TOKENS,
            ViewProperty.INPUT_TOKENS,
            ViewProperty.OUTPUT_TOKENS,
            ViewProperty.CACHE_READ_TOKENS,
            ViewProperty.CACHE_WRITE_TOKENS,
            ViewProperty.REASONING_TOKENS);
    assertThat(data.getConfiguration().getAggregationTypes())
        .containsExactly(
            new AggregationDto(AggregationType.SUM), new AggregationDto(AggregationType.AVERAGE));
    assertThat(data.getFilter())
        .extracting(ProcessFilterDto::getClass)
        .containsExactly(CompletedInstancesOnlyFilterDto.class);
  }

  @Test
  void shouldSeedProcessDurationAsP50AndP95OfCompletedAgenticInstances() {
    // when
    underTest.reconcile();

    // then
    final ProcessReportDataDto data = capturedReportData(PROCESS_DURATION_REPORT_ID);
    assertThat(data.isAgentInsightsReport()).isTrue();
    assertThat(data.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(data.getView().getProperties()).containsExactly(ViewProperty.DURATION);
    assertThat(data.getConfiguration().getAggregationTypes())
        .containsExactly(
            new AggregationDto(AggregationType.PERCENTILE, 50.0),
            new AggregationDto(AggregationType.PERCENTILE, 95.0));
    assertThat(data.getFilter())
        .extracting(ProcessFilterDto::getClass)
        .containsExactly(CompletedInstancesOnlyFilterDto.class, HasAgentInstancesFilterDto.class);
  }

  @Test
  void shouldSeedIncidentRateAsPercentageOfCompletedAgenticInstances() {
    // when
    underTest.reconcile();

    // then
    final ProcessReportDataDto data = capturedReportData(INCIDENT_RATE_REPORT_ID);
    assertThat(data.isAgentInsightsReport()).isTrue();
    assertThat(data.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(data.getView().getProperties()).containsExactly(ViewProperty.PERCENTAGE);
    assertThat(data.getFilter())
        .extracting(ProcessFilterDto::getClass)
        .containsExactly(
            CompletedInstancesOnlyFilterDto.class,
            HasAgentInstancesFilterDto.class,
            ResolvedIncidentFilterDto.class);
  }

  @Test
  void shouldSeedAgentInventoryAsRawAgentRunTableOverCompletedRuns() {
    // when
    underTest.reconcile();

    // then
    final ProcessReportDataDto data = capturedReportData(AGENT_INVENTORY_REPORT_ID);
    assertThat(data.isAgentInsightsReport()).isTrue();
    assertThat(data.getDefinitions()).isEmpty();
    assertThat(data.getView().getEntity()).isEqualTo(ProcessViewEntity.AGENT_RUN);
    assertThat(data.getView().getProperties()).containsExactly(ViewProperty.RAW_DATA);
    assertThat(data.getVisualization()).isEqualTo(ProcessVisualization.TABLE);
    assertThat(data.getFilter())
        .extracting(ProcessFilterDto::getClass)
        .containsExactly(CompletedInstancesOnlyFilterDto.class);
  }

  private ProcessReportDataDto capturedReportData(final String reportId) {
    final ArgumentCaptor<ProcessReportDataDto> captor =
        ArgumentCaptor.forClass(ProcessReportDataDto.class);
    verify(reportWriter)
        .createOrUpdateSingleProcessReport(
            eq(reportId), isNull(), captor.capture(), any(), isNull(), isNull());
    return captor.getValue();
  }
}
