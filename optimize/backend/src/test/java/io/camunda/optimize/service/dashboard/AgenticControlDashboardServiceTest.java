/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.dashboard;

import static io.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType.AGENT;
import static io.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType.MODEL;
import static io.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType.NONE;
import static io.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType.START_DATE;
import static io.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType.TOOL;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENTIC_DASHBOARD_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENTIC_DASHBOARD_NAME;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_ADOPTION_RATE_DESCRIPTION;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_ADOPTION_RATE_NAME;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_ADOPTION_RATE_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_AVG_TOKENS_DESCRIPTION;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_AVG_TOKENS_NAME;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_AVG_TOKENS_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_DURATION_P50_DESCRIPTION;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_DURATION_P50_NAME;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_DURATION_P50_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_DURATION_P95_DESCRIPTION;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_DURATION_P95_NAME;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_DURATION_P95_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_HUMAN_STEPS_DESCRIPTION;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_HUMAN_STEPS_NAME;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_HUMAN_STEPS_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_INCIDENT_RATE_DESCRIPTION;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_INCIDENT_RATE_NAME;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_INCIDENT_RATE_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_ITERATIONS_DESCRIPTION;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_ITERATIONS_NAME;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_ITERATIONS_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_LIMIT_HIT_RATE_DESCRIPTION;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_LIMIT_HIT_RATE_NAME;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_LIMIT_HIT_RATE_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_RUNS_BY_AGENT_DESCRIPTION;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_RUNS_BY_AGENT_NAME;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_RUNS_BY_AGENT_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_RUNS_DESCRIPTION;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_RUNS_NAME;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_RUNS_OVER_TIME_DESCRIPTION;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_RUNS_OVER_TIME_NAME;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_RUNS_OVER_TIME_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_RUNS_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_TOKENS_BY_MODEL_DESCRIPTION;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_TOKENS_BY_MODEL_NAME;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_TOKENS_BY_MODEL_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_TOKENS_OVER_TIME_DESCRIPTION;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_TOKENS_OVER_TIME_NAME;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_TOKENS_OVER_TIME_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_TOOL_CALLS_BY_TOOL_DESCRIPTION;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_TOOL_CALLS_BY_TOOL_NAME;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENT_TOOL_CALLS_BY_TOOL_REPORT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.RollingDateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedInstancesOnlyFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import io.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import io.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import io.camunda.optimize.service.db.reader.DashboardReader;
import io.camunda.optimize.service.db.writer.DashboardWriter;
import io.camunda.optimize.service.db.writer.ReportWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.EntityConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.assertj.core.api.ListAssert;
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
    assertThat(saved.getTiles()).hasSize(14);
  }

  @Test
  void shouldUpsertReportDefinitionsOnWarmRestart() {
    // given — dashboard already exists (warm restart)
    when(dashboardReader.getDashboard(AGENTIC_DASHBOARD_ID))
        .thenReturn(Optional.of(new DashboardDefinitionRestDto()));

    // when — reconcile is called twice (simulating two restarts)
    underTest.reconcile();
    underTest.reconcile();

    // then — reports are upserted on every call, dashboard is updated but never recreated
    verify(reportWriter, times(2))
        .createOrUpdateSingleProcessReport(
            eq(AgenticControlDashboardService.AGENT_RUNS_REPORT_ID),
            any(),
            any(),
            any(),
            any(),
            any());
    verify(dashboardWriter, never()).saveDashboard(any());
    verify(dashboardWriter, times(2)).updateDashboard(any(), any());
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

    // then reports are upserted and dashboard tiles are updated, but dashboard is not recreated
    verify(reportWriter, times(14))
        .createOrUpdateSingleProcessReport(any(), any(), any(), any(), any(), any());
    verify(dashboardWriter, never()).saveDashboard(any());
    verify(dashboardWriter).updateDashboard(any(), any());
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
  void shouldSeedPhase2TilesInThreeSections() {
    // given
    when(dashboardReader.getDashboard(AGENTIC_DASHBOARD_ID)).thenReturn(Optional.empty());

    // when
    underTest.reconcile();

    // then
    final DashboardDefinitionRestDto saved = captureSavedDashboard();
    final Map<Object, Long> tilesPerSection =
        saved.getTiles().stream()
            .collect(
                Collectors.groupingBy(
                    tile -> ((Map<?, ?>) tile.getConfiguration()).get("section"),
                    Collectors.counting()));
    assertThat(tilesPerSection)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("agentHealth", 6L, "volume", 5L, "costAndUsage", 3L));
  }

  @Test
  void shouldReadEveryTileFromTheAgentRunIndexForCompletedProcessInstancesOnly() {
    // when
    final List<ProcessReportDataDto> reports = seedAndCaptureReports();

    // then
    assertThat(reports).hasSize(14);
    assertThat(reports)
        .allSatisfy(
            report -> {
              assertThat(report.getView().getEntity()).isEqualTo(ProcessViewEntity.AGENT_RUN);
              assertThat(report.isAgenticControlReport()).isTrue();
              assertThat(report.getFilter())
                  .hasSize(1)
                  .first()
                  .isInstanceOf(CompletedInstancesOnlyFilterDto.class);
            });
  }

  @Test
  void shouldSeedOneReportPerMetric() {
    // when
    final Map<String, ProcessReportDataDto> reports = seedAndCaptureReportsById();

    // then
    assertReport(reports, AGENT_INCIDENT_RATE_REPORT_ID, ViewProperty.INCIDENT_RATE, NONE);
    assertReport(reports, AGENT_LIMIT_HIT_RATE_REPORT_ID, ViewProperty.LIMIT_HIT_RATE, NONE);
    assertReport(reports, AGENT_DURATION_P50_REPORT_ID, ViewProperty.DURATION, NONE)
        .containsExactly(new AggregationDto(AggregationType.PERCENTILE, 50.0));
    assertReport(reports, AGENT_DURATION_P95_REPORT_ID, ViewProperty.DURATION, NONE)
        .containsExactly(new AggregationDto(AggregationType.PERCENTILE, 95.0));
    assertReport(reports, AGENT_ITERATIONS_REPORT_ID, ViewProperty.MODEL_CALLS, NONE)
        .containsExactly(new AggregationDto(AggregationType.AVERAGE));
    assertReport(reports, AGENT_HUMAN_STEPS_REPORT_ID, ViewProperty.HUMAN_STEPS, NONE)
        .containsExactly(new AggregationDto(AggregationType.AVERAGE));
    assertReport(reports, AGENT_RUNS_REPORT_ID, ViewProperty.FREQUENCY, NONE);
    assertReport(reports, AGENT_RUNS_OVER_TIME_REPORT_ID, ViewProperty.FREQUENCY, START_DATE);
    assertReport(reports, AGENT_RUNS_BY_AGENT_REPORT_ID, ViewProperty.FREQUENCY, AGENT);
    assertReport(reports, AGENT_TOOL_CALLS_BY_TOOL_REPORT_ID, ViewProperty.TOOL_CALLS, TOOL)
        .containsExactly(new AggregationDto(AggregationType.SUM));
    assertReport(reports, AGENT_ADOPTION_RATE_REPORT_ID, ViewProperty.ADOPTION_RATE, NONE);
    assertReport(reports, AGENT_AVG_TOKENS_REPORT_ID, ViewProperty.TOTAL_TOKENS, NONE)
        .containsExactly(new AggregationDto(AggregationType.AVERAGE));
    assertReport(reports, AGENT_TOKENS_BY_MODEL_REPORT_ID, ViewProperty.TOTAL_TOKENS, MODEL)
        .containsExactly(new AggregationDto(AggregationType.SUM));
  }

  @Test
  void shouldShowTokensAndRunCountOnTheSameTimeTile() {
    // when
    final ProcessReportDataDto report =
        seedAndCaptureReportsById().get(AGENT_TOKENS_OVER_TIME_REPORT_ID);

    // then
    assertThat(report.getView().getProperties())
        .containsExactly(ViewProperty.TOTAL_TOKENS, ViewProperty.FREQUENCY);
    assertThat(report.getGroupBy().getType()).isEqualTo(START_DATE);
    assertThat(((DateGroupByValueDto) report.getGroupBy().getValue()).getUnit())
        .isEqualTo(AggregateByDateUnit.WEEK);
    assertThat(report.getVisualization()).isEqualTo(ProcessVisualization.LINE);
  }

  @Test
  void shouldFormatRatesAsPercentagesAndKeepDecimalsForAverages() {
    // when
    final Map<String, ProcessReportDataDto> reports = seedAndCaptureReportsById();

    // then
    assertThat(reports.get(AGENT_INCIDENT_RATE_REPORT_ID).getConfiguration().getValueFormat())
        .isEqualTo("percentage");
    assertThat(reports.get(AGENT_ADOPTION_RATE_REPORT_ID).getConfiguration().getValueFormat())
        .isEqualTo("percentage");
    assertThat(reports.get(AGENT_ITERATIONS_REPORT_ID).getConfiguration().getValueFormat())
        .isEqualTo("frequency");
    assertThat(reports.get(AGENT_DURATION_P50_REPORT_ID).getConfiguration().getValueFormat())
        .isNull();
  }

  @Test
  void shouldSortBarTilesByValueDescending() {
    // when
    final Map<String, ProcessReportDataDto> reports = seedAndCaptureReportsById();

    // then
    for (final String barReportId :
        List.of(
            AGENT_RUNS_BY_AGENT_REPORT_ID,
            AGENT_TOOL_CALLS_BY_TOOL_REPORT_ID,
            AGENT_TOKENS_BY_MODEL_REPORT_ID)) {
      final ProcessReportDataDto report = reports.get(barReportId);
      assertThat(report.getVisualization()).isEqualTo(ProcessVisualization.BAR);
      assertThat(report.getConfiguration().getSorting())
          .contains(new ReportSortingDto(ReportSortingDto.SORT_BY_VALUE, SortOrder.DESC));
    }
  }

  @Test
  void shouldResolvePhase2LocalizationCodesInEveryLocale() throws IOException {
    for (final String locale : new String[] {"en", "de"}) {
      @SuppressWarnings("unchecked")
      final Map<String, Object> report =
          (Map<String, Object>) readAgenticControlLocalization(locale).get("report");
      assertThat(report)
          .as("locale '%s' must define all Phase 2 tile localization codes", locale)
          .containsKeys(
              AGENT_INCIDENT_RATE_NAME,
              AGENT_INCIDENT_RATE_DESCRIPTION,
              AGENT_LIMIT_HIT_RATE_NAME,
              AGENT_LIMIT_HIT_RATE_DESCRIPTION,
              AGENT_DURATION_P50_NAME,
              AGENT_DURATION_P50_DESCRIPTION,
              AGENT_DURATION_P95_NAME,
              AGENT_DURATION_P95_DESCRIPTION,
              AGENT_ITERATIONS_NAME,
              AGENT_ITERATIONS_DESCRIPTION,
              AGENT_HUMAN_STEPS_NAME,
              AGENT_HUMAN_STEPS_DESCRIPTION,
              AGENT_RUNS_NAME,
              AGENT_RUNS_DESCRIPTION,
              AGENT_RUNS_OVER_TIME_NAME,
              AGENT_RUNS_OVER_TIME_DESCRIPTION,
              AGENT_RUNS_BY_AGENT_NAME,
              AGENT_RUNS_BY_AGENT_DESCRIPTION,
              AGENT_TOOL_CALLS_BY_TOOL_NAME,
              AGENT_TOOL_CALLS_BY_TOOL_DESCRIPTION,
              AGENT_ADOPTION_RATE_NAME,
              AGENT_ADOPTION_RATE_DESCRIPTION,
              AGENT_AVG_TOKENS_NAME,
              AGENT_AVG_TOKENS_DESCRIPTION,
              AGENT_TOKENS_OVER_TIME_NAME,
              AGENT_TOKENS_OVER_TIME_DESCRIPTION,
              AGENT_TOKENS_BY_MODEL_NAME,
              AGENT_TOKENS_BY_MODEL_DESCRIPTION);
    }
  }

  private static ListAssert<AggregationDto> assertReport(
      final Map<String, ProcessReportDataDto> reports,
      final String reportId,
      final ViewProperty property,
      final ProcessGroupByType groupByType) {
    final ProcessReportDataDto report = reports.get(reportId);
    assertThat(report).as("report %s", reportId).isNotNull();
    assertThat(report.getView().getProperties()).containsExactly(property);
    assertThat(report.getGroupBy().getType()).isEqualTo(groupByType);
    return assertThat(List.copyOf(report.getConfiguration().getAggregationTypes()));
  }

  private List<ProcessReportDataDto> seedAndCaptureReports() {
    return List.copyOf(seedAndCaptureReportsById().values());
  }

  private Map<String, ProcessReportDataDto> seedAndCaptureReportsById() {
    when(dashboardReader.getDashboard(AGENTIC_DASHBOARD_ID)).thenReturn(Optional.empty());
    final ArgumentCaptor<String> ids = ArgumentCaptor.forClass(String.class);
    final ArgumentCaptor<ProcessReportDataDto> data =
        ArgumentCaptor.forClass(ProcessReportDataDto.class);
    underTest.reconcile();
    verify(reportWriter, times(14))
        .createOrUpdateSingleProcessReport(
            ids.capture(), isNull(), data.capture(), any(), any(), isNull());
    final Map<String, ProcessReportDataDto> byId = new LinkedHashMap<>();
    for (int i = 0; i < ids.getAllValues().size(); i++) {
      byId.put(ids.getAllValues().get(i), data.getAllValues().get(i));
    }
    return byId;
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

  @SuppressWarnings("unchecked")
  private Map<String, Object> readLocalizationNode(final String locale, final String node)
      throws IOException {
    try (final InputStream in =
        getClass().getClassLoader().getResourceAsStream("localization/" + locale + ".json")) {
      final Map<String, Object> root = new ObjectMapper().readValue(in, Map.class);
      return (Map<String, Object>) root.get(node);
    }
  }

  private DashboardDefinitionRestDto captureSavedDashboard() {
    final ArgumentCaptor<DashboardDefinitionRestDto> captor =
        ArgumentCaptor.forClass(DashboardDefinitionRestDto.class);
    verify(dashboardWriter).saveDashboard(captor.capture());
    return captor.getValue();
  }
}
