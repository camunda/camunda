/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.dashboard;

import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.BUSINESS_VALUE_DASHBOARD_ID;
import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.BUSINESS_VALUE_DASHBOARD_NAME;
import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.L0_SECTION_ACTIVITY;
import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.L0_SECTION_AGENTIC_ADOPTION;
import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.L0_SECTION_AUTOMATION;
import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.L0_SECTION_CYCLE_TIME;
import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.L1_SECTION_OVERVIEW;
import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.L1_SECTION_UNGROUPED;
import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.TILE_CONFIG_L0_SECTION;
import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.TILE_CONFIG_L1_SECTION;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.reader.DashboardReader;
import io.camunda.optimize.service.db.writer.DashboardWriter;
import io.camunda.optimize.service.db.writer.ReportWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.EntityConfiguration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class BusinessValueDashboardServiceTest {

  private static final List<String> SEED_REPORT_IDS =
      List.of(
          BusinessValueDashboardService.WORK_HANDLED_TOTAL_REPORT_ID,
          BusinessValueDashboardService.DURATION_AVG_TOTAL_REPORT_ID,
          BusinessValueDashboardService.COUNT_BY_PROCESS_REPORT_ID,
          BusinessValueDashboardService.COUNT_BY_DATE_REPORT_ID,
          BusinessValueDashboardService.DURATION_BY_PROCESS_REPORT_ID,
          BusinessValueDashboardService.DURATION_PERCENTILES_REPORT_ID,
          BusinessValueDashboardService.DURATION_BY_DATE_REPORT_ID,
          BusinessValueDashboardService.AGENT_PRESENCE_BY_PROCESS_REPORT_ID,
          BusinessValueDashboardService.AUTOMATION_RATE_AGGREGATE_REPORT_ID,
          BusinessValueDashboardService.AUTOMATION_RATE_BY_PROCESS_REPORT_ID);

  private final DashboardWriter dashboardWriter = mock(DashboardWriter.class);
  private final DashboardReader dashboardReader = mock(DashboardReader.class);
  private final ReportWriter reportWriter = mock(ReportWriter.class);
  private final ConfigurationService configurationService = mock(ConfigurationService.class);
  private final EntityConfiguration entityConfiguration = mock(EntityConfiguration.class);

  private final BusinessValueDashboardService underTest =
      new BusinessValueDashboardService(
          dashboardWriter, dashboardReader, reportWriter, configurationService);

  @BeforeEach
  void setUp() {
    when(reportWriter.createOrUpdateSingleProcessReport(
            any(), isNull(), any(), any(), any(), isNull()))
        .thenAnswer(invocation -> new IdResponseDto(invocation.getArgument(0)));
  }

  @Test
  void shouldCreateDashboardWithExpectedShapeOnColdStart() {
    // given
    when(dashboardReader.getDashboard(BUSINESS_VALUE_DASHBOARD_ID)).thenReturn(Optional.empty());

    // when
    underTest.reconcile();

    // then
    final DashboardDefinitionRestDto saved = captureSavedDashboard();
    assertThat(saved.getId()).isEqualTo(BUSINESS_VALUE_DASHBOARD_ID);
    assertThat(saved.getName()).isEqualTo(BUSINESS_VALUE_DASHBOARD_NAME);
    assertThat(saved.isBusinessValueDashboard()).isTrue();
    assertThat(saved.isManagementDashboard()).isFalse();
    assertThat(saved.isAgenticControlDashboard()).isFalse();
    assertThat(saved.getCollectionId()).isNull();
    assertThat(saved.getTiles()).hasSize(SEED_REPORT_IDS.size());
  }

  @Test
  void shouldSeedAllReportsWithDeterministicIdsOnColdStart() {
    // given
    when(dashboardReader.getDashboard(BUSINESS_VALUE_DASHBOARD_ID)).thenReturn(Optional.empty());

    // when
    underTest.reconcile();

    // then every seeded report id is upserted exactly once
    for (final String reportId : SEED_REPORT_IDS) {
      verify(reportWriter)
          .createOrUpdateSingleProcessReport(eq(reportId), isNull(), any(), any(), any(), isNull());
    }
  }

  @Test
  void shouldUseDeterministicTileIdsMatchingReportIds() {
    // given
    when(dashboardReader.getDashboard(BUSINESS_VALUE_DASHBOARD_ID)).thenReturn(Optional.empty());

    // when
    underTest.reconcile();

    // then
    final DashboardDefinitionRestDto saved = captureSavedDashboard();
    assertThat(saved.getTiles())
        .extracting(DashboardReportTileDto::getId)
        .containsExactlyInAnyOrderElementsOf(SEED_REPORT_IDS);
  }

  @Test
  void shouldFlagEverySeededReportAsBusinessValueReport() {
    // given
    when(dashboardReader.getDashboard(BUSINESS_VALUE_DASHBOARD_ID)).thenReturn(Optional.empty());

    // when
    underTest.reconcile();

    // then every captured report data payload carries the businessValueReport flag so
    // ValidationHelper skips the definitionKey null-check on evaluation
    for (final String reportId : SEED_REPORT_IDS) {
      final ProcessReportDataDto data = captureReportData(reportId);
      assertThat(data.isBusinessValueReport())
          .as("report %s must be flagged as businessValueReport", reportId)
          .isTrue();
      assertThat(data.isSystemGeneratedReport()).isTrue();
      assertThat(data.getDefinitions()).isEmpty();
    }
  }

  @Test
  void shouldUpdateDashboardOnWarmRestartWithoutRecreating() {
    // given the dashboard already exists (warm restart)
    when(dashboardReader.getDashboard(BUSINESS_VALUE_DASHBOARD_ID))
        .thenReturn(Optional.of(new DashboardDefinitionRestDto()));

    // when
    underTest.reconcile();

    // then reports are still upserted, dashboard is updated (not saved), and never deleted
    verify(reportWriter, times(SEED_REPORT_IDS.size()))
        .createOrUpdateSingleProcessReport(any(), any(), any(), any(), any(), any());
    verify(dashboardWriter, never()).saveDashboard(any());
    verify(dashboardWriter).updateDashboard(any(), any());
    verify(dashboardWriter, never()).deleteDashboard(any());
  }

  @Test
  void shouldReconcileIdempotentlyAcrossMultipleRuns() {
    // given dashboard already exists across every run (idempotent warm path)
    when(dashboardReader.getDashboard(BUSINESS_VALUE_DASHBOARD_ID))
        .thenReturn(Optional.of(new DashboardDefinitionRestDto()));

    // when reconciled twice
    underTest.reconcile();
    underTest.reconcile();

    // then reports are upserted on every call and the dashboard is only ever updated
    verify(reportWriter, times(SEED_REPORT_IDS.size() * 2))
        .createOrUpdateSingleProcessReport(any(), any(), any(), any(), any(), any());
    verify(dashboardWriter, never()).saveDashboard(any());
    verify(dashboardWriter, times(2)).updateDashboard(any(), any());
  }

  @Test
  void shouldSeedDashboardWithoutAvailableFilters() {
    // given
    when(dashboardReader.getDashboard(BUSINESS_VALUE_DASHBOARD_ID)).thenReturn(Optional.empty());

    // when
    underTest.reconcile();

    // then no dashboard-level filters are seeded — Hub owns the filter bar and passes
    // filters at evaluate time via AdditionalProcessReportEvaluationFilterDto
    final DashboardDefinitionRestDto saved = captureSavedDashboard();
    assertThat(saved.getAvailableFilters()).isEmpty();
  }

  @Test
  void shouldSeedDashboardWhenCreateOnStartupEnabled() {
    // given
    when(configurationService.getEntityConfiguration()).thenReturn(entityConfiguration);
    when(entityConfiguration.getCreateOnStartup()).thenReturn(true);
    when(dashboardReader.getDashboard(BUSINESS_VALUE_DASHBOARD_ID)).thenReturn(Optional.empty());

    // when
    underTest.init();

    // then the dashboard is seeded
    verify(dashboardWriter).saveDashboard(any());
  }

  @Test
  void shouldSeedNothingWhenCreateOnStartupDisabled() {
    // given the startup flag is disabled
    when(configurationService.getEntityConfiguration()).thenReturn(entityConfiguration);
    when(entityConfiguration.getCreateOnStartup()).thenReturn(false);

    // when
    underTest.init();

    // then nothing is read or written
    verifyNoInteractions(dashboardReader);
    verifyNoInteractions(dashboardWriter);
    verifyNoInteractions(reportWriter);
  }

  @Test
  void shouldRenderEverySeededTileOnAtLeastOneLevel() {
    // given
    when(dashboardReader.getDashboard(BUSINESS_VALUE_DASHBOARD_ID)).thenReturn(Optional.empty());

    // when
    underTest.reconcile();

    // then a tile without any section key would be dropped by the frontend
    assertThat(captureSavedDashboard().getTiles())
        .allSatisfy(
            tile ->
                assertThat(
                        sectionOf(tile, TILE_CONFIG_L0_SECTION) != null
                            || sectionOf(tile, TILE_CONFIG_L1_SECTION) != null)
                    .as("tile '%s' must declare an L0 and/or L1 section", tile.getId())
                    .isTrue());
  }

  @Test
  void shouldGroupTilesIntoTheExpectedL0Sections() {
    // given
    when(dashboardReader.getDashboard(BUSINESS_VALUE_DASHBOARD_ID)).thenReturn(Optional.empty());

    // when
    underTest.reconcile();

    // then
    assertThat(sectionsByReportId(TILE_CONFIG_L0_SECTION))
        .containsOnly(
            entry(BusinessValueDashboardService.WORK_HANDLED_TOTAL_REPORT_ID, L0_SECTION_ACTIVITY),
            entry(BusinessValueDashboardService.COUNT_BY_PROCESS_REPORT_ID, L0_SECTION_ACTIVITY),
            entry(BusinessValueDashboardService.COUNT_BY_DATE_REPORT_ID, L0_SECTION_ACTIVITY),
            entry(
                BusinessValueDashboardService.DURATION_BY_PROCESS_REPORT_ID, L0_SECTION_CYCLE_TIME),
            entry(
                BusinessValueDashboardService.AGENT_PRESENCE_BY_PROCESS_REPORT_ID,
                L0_SECTION_AGENTIC_ADOPTION),
            entry(
                BusinessValueDashboardService.AUTOMATION_RATE_AGGREGATE_REPORT_ID,
                L0_SECTION_AUTOMATION),
            entry(
                BusinessValueDashboardService.AUTOMATION_RATE_BY_PROCESS_REPORT_ID,
                L0_SECTION_AUTOMATION));
  }

  @Test
  void shouldGroupTilesIntoTheExpectedL1Sections() {
    // given
    when(dashboardReader.getDashboard(BUSINESS_VALUE_DASHBOARD_ID)).thenReturn(Optional.empty());

    // when
    underTest.reconcile();

    // then
    assertThat(sectionsByReportId(TILE_CONFIG_L1_SECTION))
        .containsOnly(
            entry(BusinessValueDashboardService.WORK_HANDLED_TOTAL_REPORT_ID, L1_SECTION_OVERVIEW),
            entry(BusinessValueDashboardService.DURATION_AVG_TOTAL_REPORT_ID, L1_SECTION_OVERVIEW),
            entry(BusinessValueDashboardService.COUNT_BY_DATE_REPORT_ID, L1_SECTION_UNGROUPED),
            entry(
                BusinessValueDashboardService.DURATION_PERCENTILES_REPORT_ID, L1_SECTION_UNGROUPED),
            entry(BusinessValueDashboardService.DURATION_BY_DATE_REPORT_ID, L1_SECTION_UNGROUPED),
            entry(
                BusinessValueDashboardService.AUTOMATION_RATE_AGGREGATE_REPORT_ID,
                L1_SECTION_OVERVIEW));
  }

  @Test
  void shouldNotPlaceTwoTilesAtTheSamePosition() {
    // given
    when(dashboardReader.getDashboard(BUSINESS_VALUE_DASHBOARD_ID)).thenReturn(Optional.empty());

    // when
    underTest.reconcile();

    // then no two tiles share an (x, y) origin, otherwise one would render on top of another
    assertThat(
            captureSavedDashboard().getTiles().stream()
                .map(tile -> tile.getPosition().getX() + "," + tile.getPosition().getY())
                .toList())
        .doesNotHaveDuplicates();
  }

  private Map<String, String> sectionsByReportId(final String configKey) {
    return captureSavedDashboard().getTiles().stream()
        .filter(tile -> sectionOf(tile, configKey) != null)
        .collect(toMap(DashboardReportTileDto::getId, tile -> sectionOf(tile, configKey)));
  }

  private static String sectionOf(final DashboardReportTileDto tile, final String configKey) {
    assertThat(tile.getConfiguration()).isInstanceOf(Map.class);
    return (String) ((Map<?, ?>) tile.getConfiguration()).get(configKey);
  }

  private DashboardDefinitionRestDto captureSavedDashboard() {
    final ArgumentCaptor<DashboardDefinitionRestDto> captor =
        ArgumentCaptor.forClass(DashboardDefinitionRestDto.class);
    verify(dashboardWriter).saveDashboard(captor.capture());
    return captor.getValue();
  }

  private ProcessReportDataDto captureReportData(final String reportId) {
    final ArgumentCaptor<ProcessReportDataDto> captor =
        ArgumentCaptor.forClass(ProcessReportDataDto.class);
    verify(reportWriter)
        .createOrUpdateSingleProcessReport(
            eq(reportId), isNull(), captor.capture(), any(), any(), isNull());
    return captor.getValue();
  }
}
