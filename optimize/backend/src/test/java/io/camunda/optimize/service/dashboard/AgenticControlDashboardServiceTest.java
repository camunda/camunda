/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.dashboard;

import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENTIC_DASHBOARD_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENTIC_DASHBOARD_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardFilterDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardInstanceEndDateFilterDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardProcessScopeFilterDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardDateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.RollingDateFilterDataDto;
import io.camunda.optimize.service.db.reader.DashboardReader;
import io.camunda.optimize.service.db.writer.DashboardWriter;
import io.camunda.optimize.service.db.writer.ReportWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.EntityConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class AgenticControlDashboardServiceTest {

  private final DashboardWriter dashboardWriter = mock(DashboardWriter.class);
  private final DashboardReader dashboardReader = mock(DashboardReader.class);
  private final ReportWriter reportWriter = mock(ReportWriter.class);
  private final ConfigurationService configurationService = mock(ConfigurationService.class);
  private final EntityConfiguration entityConfiguration = mock(EntityConfiguration.class);

  private final AgenticControlDashboardService underTest =
      new AgenticControlDashboardService(
          dashboardWriter, dashboardReader, reportWriter, configurationService);

  @BeforeEach
  void setUp() {
    // createOrUpdateSingleProcessReport is called with (reportId, userId, data, name, desc,
    // collectionId)
    when(reportWriter.createOrUpdateSingleProcessReport(
            any(), isNull(), any(), any(), any(), isNull()))
        .thenAnswer(invocation -> new IdResponseDto(invocation.getArgument(0)));
  }

  @Test
  void shouldCreateDashboardWithExpectedShapeOnColdStart() {
    // given
    when(dashboardReader.getDashboard(AGENTIC_DASHBOARD_ID)).thenReturn(Optional.empty());

    // when
    underTest.reconcile();

    // then
    final DashboardDefinitionRestDto saved = captureSavedDashboard();
    assertThat(saved.getId()).isEqualTo(AGENTIC_DASHBOARD_ID);
    assertThat(saved.isAgenticControlDashboard()).isTrue();
    assertThat(saved.isManagementDashboard()).isFalse();
    assertThat(saved.getCollectionId()).isNull();
    assertThat(saved.getTiles()).hasSize(3);
  }

  @Test
  void shouldSeedThreeKpiReportsWithDeterministicIdsOnColdStart() {
    // given
    when(dashboardReader.getDashboard(AGENTIC_DASHBOARD_ID)).thenReturn(Optional.empty());

    // when
    underTest.reconcile();

    // then three reports are upserted with deterministic IDs and correct localization keys
    verify(reportWriter)
        .createOrUpdateSingleProcessReport(
            org.mockito.ArgumentMatchers.eq(AgenticControlDashboardService.KPI_COMPLETED_REPORT_ID),
            isNull(),
            any(),
            org.mockito.ArgumentMatchers.eq(
                AgenticControlDashboardService.KPI_EXECUTION_COMPLETED_NAME),
            org.mockito.ArgumentMatchers.eq(
                AgenticControlDashboardService.KPI_EXECUTION_COMPLETED_DESCRIPTION),
            isNull());
    verify(reportWriter)
        .createOrUpdateSingleProcessReport(
            org.mockito.ArgumentMatchers.eq(
                AgenticControlDashboardService.KPI_AVG_DURATION_REPORT_ID),
            isNull(),
            any(),
            org.mockito.ArgumentMatchers.eq(
                AgenticControlDashboardService.KPI_EXECUTION_AVG_DURATION_NAME),
            org.mockito.ArgumentMatchers.eq(
                AgenticControlDashboardService.KPI_EXECUTION_AVG_DURATION_DESCRIPTION),
            isNull());
    verify(reportWriter)
        .createOrUpdateSingleProcessReport(
            org.mockito.ArgumentMatchers.eq(
                AgenticControlDashboardService.KPI_INCIDENT_RATE_REPORT_ID),
            isNull(),
            any(),
            org.mockito.ArgumentMatchers.eq(
                AgenticControlDashboardService.KPI_EXECUTION_INCIDENT_RATE_NAME),
            org.mockito.ArgumentMatchers.eq(
                AgenticControlDashboardService.KPI_EXECUTION_INCIDENT_RATE_DESCRIPTION),
            isNull());
  }

  @Test
  void shouldUpsertReportDefinitionsOnWarmRestart() {
    // given — dashboard already exists (warm restart)
    when(dashboardReader.getDashboard(AGENTIC_DASHBOARD_ID))
        .thenReturn(Optional.of(new DashboardDefinitionRestDto()));

    // when — reconcile is called twice (simulating two restarts)
    underTest.reconcile();
    underTest.reconcile();

    // then — reports are upserted on every call, dashboard is never recreated
    verify(reportWriter, org.mockito.Mockito.times(2))
        .createOrUpdateSingleProcessReport(
            org.mockito.ArgumentMatchers.eq(AgenticControlDashboardService.KPI_COMPLETED_REPORT_ID),
            any(),
            any(),
            any(),
            any(),
            any());
    verify(dashboardWriter, never()).saveDashboard(any());
  }

  @Test
  void shouldUseDeterministicTileIdsMatchingReportIds() {
    // given
    when(dashboardReader.getDashboard(AGENTIC_DASHBOARD_ID)).thenReturn(Optional.empty());

    // when
    underTest.reconcile();

    // then tile IDs in the saved dashboard match the deterministic report ID constants
    final DashboardDefinitionRestDto saved = captureSavedDashboard();
    assertThat(saved.getTiles())
        .extracting(
            io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto::getId)
        .containsExactlyInAnyOrder(
            AgenticControlDashboardService.KPI_COMPLETED_REPORT_ID,
            AgenticControlDashboardService.KPI_AVG_DURATION_REPORT_ID,
            AgenticControlDashboardService.KPI_INCIDENT_RATE_REPORT_ID);
  }

  @Test
  void shouldSeedInstanceEndDateFilterOnColdStart() {
    // given
    when(dashboardReader.getDashboard(AGENTIC_DASHBOARD_ID)).thenReturn(Optional.empty());

    // when
    underTest.reconcile();

    // then
    final DashboardDefinitionRestDto saved = captureSavedDashboard();
    assertThat(saved.getAvailableFilters())
        .hasAtLeastOneElementOfType(DashboardInstanceEndDateFilterDto.class);
  }

  @Test
  void shouldSeedProcessScopeFilterOnColdStart() {
    // given
    when(dashboardReader.getDashboard(AGENTIC_DASHBOARD_ID)).thenReturn(Optional.empty());

    // when
    underTest.reconcile();

    // then
    final DashboardDefinitionRestDto saved = captureSavedDashboard();
    assertThat(saved.getAvailableFilters())
        .hasAtLeastOneElementOfType(DashboardProcessScopeFilterDto.class);
  }

  @Test
  void shouldDefaultInstanceEndDateFilterToRollingLast30Days() {
    // given
    when(dashboardReader.getDashboard(AGENTIC_DASHBOARD_ID)).thenReturn(Optional.empty());

    // when
    underTest.reconcile();

    // then the default range is a rolling last-30-days range
    final DashboardDefinitionRestDto saved = captureSavedDashboard();
    final DashboardFilterDto<?> filter = saved.getAvailableFilters().get(0);
    final DashboardDateFilterDataDto data = (DashboardDateFilterDataDto) filter.getData();
    assertThat(data.getDefaultValues()).isInstanceOf(RollingDateFilterDataDto.class);

    final RollingDateFilterDataDto rolling = (RollingDateFilterDataDto) data.getDefaultValues();
    final RollingDateFilterStartDto start = rolling.getStart();
    assertThat(start.getValue()).isEqualTo(30L);
    assertThat(start.getUnit()).isEqualTo(DateUnit.DAYS);
  }

  @Test
  void shouldUpsertReportsButNotRecreateDashboardOnWarmRestart() {
    // given the dashboard already exists (warm restart)
    when(dashboardReader.getDashboard(AGENTIC_DASHBOARD_ID))
        .thenReturn(Optional.of(new DashboardDefinitionRestDto()));

    // when
    underTest.reconcile();

    // then reports are upserted (so config changes are applied) but dashboard is not touched
    verify(reportWriter, org.mockito.Mockito.times(3))
        .createOrUpdateSingleProcessReport(any(), any(), any(), any(), any(), any());
    verify(dashboardWriter, never()).saveDashboard(any());
    verify(dashboardWriter, never()).updateDashboard(any(), any());
    verify(dashboardWriter, never()).deleteDashboard(any());
  }

  @Test
  void shouldSeedDashboardWhenCreateOnStartupEnabled() {
    // given the startup flag is enabled and no dashboard exists
    when(configurationService.getEntityConfiguration()).thenReturn(entityConfiguration);
    when(entityConfiguration.getCreateOnStartup()).thenReturn(true);
    when(dashboardReader.getDashboard(AGENTIC_DASHBOARD_ID)).thenReturn(Optional.empty());

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
  void shouldNotTouchManagementDashboardWhenSeeding() {
    // given
    when(dashboardReader.getDashboard(AGENTIC_DASHBOARD_ID)).thenReturn(Optional.empty());

    // when
    underTest.reconcile();

    // then seeding stays isolated from the management dashboard lifecycle
    verify(dashboardWriter, never()).deleteManagementDashboard();
    verify(dashboardWriter, never()).deleteDashboard(any());
    final DashboardDefinitionRestDto saved = captureSavedDashboard();
    assertThat(saved.getId()).isEqualTo(AGENTIC_DASHBOARD_ID);
  }

  @Test
  void shouldSeedAppendableTileListForFutureReconcileExtensions() {
    // given
    when(dashboardReader.getDashboard(AGENTIC_DASHBOARD_ID)).thenReturn(Optional.empty());

    // when
    underTest.reconcile();

    // then the tile list is mutable (can be extended)
    final DashboardDefinitionRestDto saved = captureSavedDashboard();
    assertThatNoException().isThrownBy(() -> saved.getTiles().add(new DashboardReportTileDto()));
  }

  @Test
  void shouldResolveDashboardNameLocalizationCodeInEveryLocale() throws IOException {
    for (final String locale : new String[] {"en", "de"}) {
      final Map<String, Object> agenticControl = readAgenticControlLocalization(locale);
      assertThat(agenticControl)
          .as("locale '%s' must define agenticControl.%s", locale, AGENTIC_DASHBOARD_NAME)
          .containsKey(AGENTIC_DASHBOARD_NAME);
      assertThat(agenticControl.get(AGENTIC_DASHBOARD_NAME)).isEqualTo("Agentic Control Dashboard");
    }
  }

  @Test
  void shouldResolveKpiReportLocalizationCodesInEveryLocale() throws IOException {
    for (final String locale : new String[] {"en", "de"}) {
      final Map<String, Object> agenticControl = readAgenticControlLocalization(locale);
      @SuppressWarnings("unchecked")
      final Map<String, Object> report = (Map<String, Object>) agenticControl.get("report");
      assertThat(report)
          .as("locale '%s' must define all KPI report localization codes", locale)
          .containsKeys(
              AgenticControlDashboardService.KPI_EXECUTION_COMPLETED_NAME,
              AgenticControlDashboardService.KPI_EXECUTION_COMPLETED_DESCRIPTION,
              AgenticControlDashboardService.KPI_EXECUTION_AVG_DURATION_NAME,
              AgenticControlDashboardService.KPI_EXECUTION_AVG_DURATION_DESCRIPTION,
              AgenticControlDashboardService.KPI_EXECUTION_INCIDENT_RATE_NAME,
              AgenticControlDashboardService.KPI_EXECUTION_INCIDENT_RATE_DESCRIPTION);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> readAgenticControlLocalization(final String locale)
      throws IOException {
    try (final InputStream in =
        getClass().getClassLoader().getResourceAsStream("localization/" + locale + ".json")) {
      final Map<String, Object> root = new ObjectMapper().readValue(in, Map.class);
      return (Map<String, Object>) root.get("agenticControl");
    }
  }

  private DashboardDefinitionRestDto captureSavedDashboard() {
    final ArgumentCaptor<DashboardDefinitionRestDto> captor =
        ArgumentCaptor.forClass(DashboardDefinitionRestDto.class);
    verify(dashboardWriter).saveDashboard(captor.capture());
    return captor.getValue();
  }
}
