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
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.HasAgentInstancesFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.EndDateGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessDefinitionVersionGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
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
import java.util.List;
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
    assertThat(saved.getTiles()).hasSize(11);
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
            eq(AgenticControlDashboardService.KPI_COMPLETED_REPORT_ID),
            isNull(),
            any(),
            eq(AgenticControlDashboardService.KPI_EXECUTION_COMPLETED_NAME),
            eq(AgenticControlDashboardService.KPI_EXECUTION_COMPLETED_DESCRIPTION),
            isNull());
    verify(reportWriter)
        .createOrUpdateSingleProcessReport(
            eq(AgenticControlDashboardService.KPI_AVG_DURATION_REPORT_ID),
            isNull(),
            any(),
            eq(AgenticControlDashboardService.KPI_EXECUTION_AVG_DURATION_NAME),
            eq(AgenticControlDashboardService.KPI_EXECUTION_AVG_DURATION_DESCRIPTION),
            isNull());
    verify(reportWriter)
        .createOrUpdateSingleProcessReport(
            eq(AgenticControlDashboardService.KPI_INCIDENT_RATE_REPORT_ID),
            isNull(),
            any(),
            eq(AgenticControlDashboardService.KPI_EXECUTION_INCIDENT_RATE_NAME),
            eq(AgenticControlDashboardService.KPI_EXECUTION_INCIDENT_RATE_DESCRIPTION),
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

    // then — reports are upserted on every call, dashboard is updated but never recreated
    verify(reportWriter, times(2))
        .createOrUpdateSingleProcessReport(
            eq(AgenticControlDashboardService.KPI_COMPLETED_REPORT_ID),
            any(),
            any(),
            any(),
            any(),
            any());
    verify(dashboardWriter, never()).saveDashboard(any());
    verify(dashboardWriter, times(2)).updateDashboard(any(), any());
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
            AgenticControlDashboardService.KPI_INCIDENT_RATE_REPORT_ID,
            AgenticControlDashboardService.KPI_AVG_TOKENS_REPORT_ID,
            AgenticControlDashboardService.KPI_MEDIAN_TOKENS_REPORT_ID,
            AgenticControlDashboardService.DURATION_STABILITY_REPORT_ID,
            AgenticControlDashboardService.TOKEN_TREND_REPORT_ID,
            AgenticControlDashboardService.TOKEN_CONSUMERS_REPORT_ID,
            AgenticControlDashboardService.KPI_DURATION_P50_REPORT_ID,
            AgenticControlDashboardService.KPI_DURATION_P95_REPORT_ID,
            AgenticControlDashboardService.FAILURE_RATE_BY_VERSION_REPORT_ID);
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
    verify(reportWriter, times(11))
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
              AgenticControlDashboardService.KPI_EXECUTION_INCIDENT_RATE_DESCRIPTION,
              AgenticControlDashboardService.KPI_TOKEN_TREND_NAME,
              AgenticControlDashboardService.KPI_TOKEN_TREND_FOOTNOTE,
              AgenticControlDashboardService.KPI_TOKEN_CONSUMERS_NAME,
              AgenticControlDashboardService.KPI_TOKEN_CONSUMERS_FOOTNOTE,
              AgenticControlDashboardService.KPI_DURATION_P50_NAME,
              AgenticControlDashboardService.KPI_DURATION_P50_DESCRIPTION,
              AgenticControlDashboardService.KPI_DURATION_P95_NAME,
              AgenticControlDashboardService.KPI_DURATION_P95_DESCRIPTION,
              AgenticControlDashboardService.DURATION_STABILITY_NAME,
              AgenticControlDashboardService.DURATION_STABILITY_DESCRIPTION,
              AgenticControlDashboardService.FAILURE_RATE_BY_VERSION_NAME,
              AgenticControlDashboardService.FAILURE_RATE_BY_VERSION_DESCRIPTION);
    }
  }

  @Test
  void shouldSeedDurationStabilityReportWithCorrectDefinition() {
    // given
    when(dashboardReader.getDashboard(AGENTIC_DASHBOARD_ID)).thenReturn(Optional.empty());

    // when
    underTest.reconcile();

    // then the stability report is upserted with the deterministic ID
    final ArgumentCaptor<ProcessReportDataDto> dataCaptor =
        ArgumentCaptor.forClass(ProcessReportDataDto.class);
    verify(reportWriter)
        .createOrUpdateSingleProcessReport(
            org.mockito.ArgumentMatchers.eq(
                AgenticControlDashboardService.DURATION_STABILITY_REPORT_ID),
            isNull(),
            dataCaptor.capture(),
            org.mockito.ArgumentMatchers.eq(AgenticControlDashboardService.DURATION_STABILITY_NAME),
            org.mockito.ArgumentMatchers.eq(
                AgenticControlDashboardService.DURATION_STABILITY_DESCRIPTION),
            isNull());

    final ProcessReportDataDto data = dataCaptor.getValue();

    // view: PROCESS_INSTANCE + DURATION
    assertThat(data.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(data.getView().getFirstProperty()).isEqualTo(ViewProperty.DURATION);

    // groupBy: EndDateGroupByDto with AUTOMATIC unit
    assertThat(data.getGroupBy()).isInstanceOf(EndDateGroupByDto.class);
    final EndDateGroupByDto endDateGroupBy = (EndDateGroupByDto) data.getGroupBy();
    assertThat(endDateGroupBy.getValue().getUnit()).isEqualTo(AggregateByDateUnit.AUTOMATIC);

    // aggregation types: exactly P50 then P95, in order
    final List<AggregationDto> aggTypes =
        List.copyOf(data.getConfiguration().getAggregationTypes());
    assertThat(aggTypes).hasSize(2);
    assertThat(aggTypes.get(0).getType()).isEqualTo(AggregationType.PERCENTILE);
    assertThat(aggTypes.get(0).getValue()).isEqualTo(50.0);
    assertThat(aggTypes.get(1).getType()).isEqualTo(AggregationType.PERCENTILE);
    assertThat(aggTypes.get(1).getValue()).isEqualTo(95.0);

    // visualization: LINE
    assertThat(data.getVisualization()).isEqualTo(ProcessVisualization.LINE);

    // filters: completedInstancesOnly + hasAgentInstances
    assertThat(data.getFilter()).hasAtLeastOneElementOfType(CompletedInstancesOnlyFilterDto.class);
    assertThat(data.getFilter()).hasAtLeastOneElementOfType(HasAgentInstancesFilterDto.class);

    // flagged as agentic control report
    assertThat(data.isAgenticControlReport()).isTrue();

    // deterministic ID matches the seed string
    assertThat(AgenticControlDashboardService.DURATION_STABILITY_REPORT_ID)
        .isEqualTo(
            java.util
                .UUID
                .nameUUIDFromBytes(
                    "agentic-duration-stability".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString());
  }

  @Test
  void shouldSeedDurationStabilityTileAtCorrectPositionAndDimensions() {
    // given
    when(dashboardReader.getDashboard(AGENTIC_DASHBOARD_ID)).thenReturn(Optional.empty());

    // when
    underTest.reconcile();

    // then
    final DashboardDefinitionRestDto saved = captureSavedDashboard();
    final var stabilityTile =
        saved.getTiles().stream()
            .filter(
                t -> t.getId().equals(AgenticControlDashboardService.DURATION_STABILITY_REPORT_ID))
            .findFirst()
            .orElseThrow();

    assertThat(stabilityTile.getPosition().getX()).isEqualTo(0);
    assertThat(stabilityTile.getPosition().getY()).isEqualTo(12);
    assertThat(stabilityTile.getDimensions().getWidth()).isEqualTo(18);
    assertThat(stabilityTile.getDimensions().getHeight()).isEqualTo(2);
    assertThat(stabilityTile.getConfiguration()).isEqualTo(Map.of("section", "duration"));
  }

  @Test
  void shouldDefineTopNNoticeLabelInEveryLocale() throws IOException {
    for (final String locale : new String[] {"en", "de"}) {
      final Map<String, Object> agenticControlPlane =
          readLocalizationNode(locale, "agenticControlPlane");
      assertThat(agenticControlPlane)
          .as("locale '%s' must define agenticControlPlane.topNNotice", locale)
          .containsKey("topNNotice");
    }
  }

  @Test
  void shouldSeedTokenTrendReportWithBothTokenMeasures() {
    // given
    when(dashboardReader.getDashboard(AGENTIC_DASHBOARD_ID)).thenReturn(Optional.empty());

    // when
    underTest.reconcile();

    // then a single multi-measure report is upserted with a deterministic ID
    verify(reportWriter)
        .createOrUpdateSingleProcessReport(
            eq(AgenticControlDashboardService.TOKEN_TREND_REPORT_ID),
            isNull(),
            any(),
            eq(AgenticControlDashboardService.KPI_TOKEN_TREND_NAME),
            isNull(),
            isNull());
  }

  @Test
  void shouldSumBothTokenMeasuresPerWeekInTokenTrendReport() {
    // given
    when(dashboardReader.getDashboard(AGENTIC_DASHBOARD_ID)).thenReturn(Optional.empty());

    // when
    underTest.reconcile();

    // then the token-trend report exposes input and output tokens as SUM measures so weekly totals
    // are not averaged
    assertThat(captureTokenTrendReport())
        .satisfies(
            data -> {
              assertThat(data.getView().getProperties())
                  .containsExactly(ViewProperty.INPUT_TOKENS, ViewProperty.OUTPUT_TOKENS);
              assertThat(data.getConfiguration().getAggregationTypes())
                  .extracting(agg -> agg.getType())
                  .containsExactly(AggregationType.SUM);
            });
  }

  private ProcessReportDataDto captureTokenTrendReport() {
    final ArgumentCaptor<ProcessReportDataDto> captor =
        ArgumentCaptor.forClass(ProcessReportDataDto.class);
    verify(reportWriter)
        .createOrUpdateSingleProcessReport(
            eq(AgenticControlDashboardService.TOKEN_TREND_REPORT_ID),
            isNull(),
            captor.capture(),
            any(),
            isNull(),
            isNull());
    return captor.getValue();
  }

  @Test
  void shouldUpsertTokenTrendReportOnWarmRestart() {
    // given
    when(dashboardReader.getDashboard(AGENTIC_DASHBOARD_ID))
        .thenReturn(Optional.of(new DashboardDefinitionRestDto()));

    // when
    underTest.reconcile();
    underTest.reconcile();

    // then the token-trend report is upserted on every reconcile
    verify(reportWriter, times(2))
        .createOrUpdateSingleProcessReport(
            eq(AgenticControlDashboardService.TOKEN_TREND_REPORT_ID),
            any(),
            any(),
            any(),
            any(),
            any());
  }

  @Test
  void shouldSeedTopTokenConsumersReportGroupedByProcessSortedDesc() {
    // given
    when(dashboardReader.getDashboard(AGENTIC_DASHBOARD_ID)).thenReturn(Optional.empty());

    // when
    underTest.reconcile();

    // then a single total-tokens report grouped by process is seeded as a descending horizontal bar
    assertThat(captureTopTokenConsumersReport())
        .satisfies(
            data -> {
              assertThat(data.getView().getEntity()).isEqualTo(ProcessViewEntity.AGENT_INSTANCE);
              assertThat(data.getView().getProperties()).containsExactly(ViewProperty.TOTAL_TOKENS);
              assertThat(data.getGroupBy().getType())
                  .isEqualTo(ProcessGroupByType.PROCESS_DEFINITION_KEY);
              assertThat(data.getVisualization()).isEqualTo(ProcessVisualization.BAR);
              assertThat(data.getConfiguration().getHorizontalBar()).isTrue();
              assertThat(data.getConfiguration().getAggregationTypes())
                  .extracting(AggregationDto::getType)
                  .containsExactly(AggregationType.SUM);
              assertThat(data.getConfiguration().getSorting())
                  .hasValueSatisfying(
                      sorting -> {
                        assertThat(sorting.getBy()).contains(ReportSortingDto.SORT_BY_VALUE);
                        assertThat(sorting.getOrder()).contains(SortOrder.DESC);
                      });
            });
  }

  @Test
  void shouldPlaceTopTokenConsumersTileFullWidthBelowFailureRateByVersion() {
    // given
    when(dashboardReader.getDashboard(AGENTIC_DASHBOARD_ID)).thenReturn(Optional.empty());

    // when
    underTest.reconcile();

    // then the consumers tile spans the full grid width directly below the failure-rate-by-version
    // tile
    final DashboardDefinitionRestDto saved = captureSavedDashboard();
    final DashboardReportTileDto failureRate =
        tileById(saved, AgenticControlDashboardService.FAILURE_RATE_BY_VERSION_REPORT_ID);
    final DashboardReportTileDto consumers =
        tileById(saved, AgenticControlDashboardService.TOKEN_CONSUMERS_REPORT_ID);

    assertThat(consumers.getPosition().getX()).isZero();
    assertThat(consumers.getPosition().getY())
        .isEqualTo(failureRate.getPosition().getY() + failureRate.getDimensions().getHeight());
    assertThat(consumers.getDimensions().getWidth()).isEqualTo(18);

    // and it advertises the server-side top-N limit for the frontend to request, and is only
    // shown at the dashboard root (L0)
    @SuppressWarnings("unchecked")
    final Map<String, Object> configuration = (Map<String, Object>) consumers.getConfiguration();
    assertThat(configuration)
        .containsEntry("topN", String.valueOf(AgenticControlDashboardService.TOKEN_CONSUMERS_LIMIT))
        .containsEntry("visibleInL0Only", Boolean.TRUE);
  }

  private DashboardReportTileDto tileById(
      final DashboardDefinitionRestDto dashboard, final String id) {
    return dashboard.getTiles().stream()
        .filter(tile -> id.equals(tile.getId()))
        .findFirst()
        .orElseThrow();
  }

  private ProcessReportDataDto captureTopTokenConsumersReport() {
    final ArgumentCaptor<ProcessReportDataDto> captor =
        ArgumentCaptor.forClass(ProcessReportDataDto.class);
    verify(reportWriter)
        .createOrUpdateSingleProcessReport(
            eq(AgenticControlDashboardService.TOKEN_CONSUMERS_REPORT_ID),
            isNull(),
            captor.capture(),
            eq(AgenticControlDashboardService.KPI_TOKEN_CONSUMERS_NAME),
            isNull(),
            isNull());
    return captor.getValue();
  }

  @Test
  void shouldSeedP50DurationReportWithCorrectConfig() {
    assertDurationReportSeeded(
        AgenticControlDashboardService.KPI_DURATION_P50_REPORT_ID,
        AgenticControlDashboardService.KPI_DURATION_P50_NAME,
        AgenticControlDashboardService.KPI_DURATION_P50_DESCRIPTION,
        50.0);
  }

  @Test
  void shouldSeedP95DurationReportWithCorrectConfig() {
    assertDurationReportSeeded(
        AgenticControlDashboardService.KPI_DURATION_P95_REPORT_ID,
        AgenticControlDashboardService.KPI_DURATION_P95_NAME,
        AgenticControlDashboardService.KPI_DURATION_P95_DESCRIPTION,
        95.0);
  }

  @Test
  void shouldUpsertP50AndP95ReportsOnWarmRestart() {
    // given — dashboard already exists (warm restart)
    when(dashboardReader.getDashboard(AGENTIC_DASHBOARD_ID))
        .thenReturn(Optional.of(new DashboardDefinitionRestDto()));

    // when
    underTest.reconcile();

    // then both duration percentile reports are upserted even when the dashboard is not recreated
    verify(reportWriter)
        .createOrUpdateSingleProcessReport(
            eq(AgenticControlDashboardService.KPI_DURATION_P50_REPORT_ID),
            any(),
            any(),
            any(),
            any(),
            any());
    verify(reportWriter)
        .createOrUpdateSingleProcessReport(
            eq(AgenticControlDashboardService.KPI_DURATION_P95_REPORT_ID),
            any(),
            any(),
            any(),
            any(),
            any());
    verify(dashboardWriter, never()).saveDashboard(any());
  }

  @Test
  void shouldSeedFailureRateByVersionReportWithGroupedBarShape() {
    // given
    when(dashboardReader.getDashboard(AGENTIC_DASHBOARD_ID)).thenReturn(Optional.empty());
    final ArgumentCaptor<ProcessReportDataDto> dataCaptor =
        ArgumentCaptor.forClass(ProcessReportDataDto.class);

    // when
    underTest.reconcile();

    // then the report is upserted with the correct deterministic ID and localization keys
    verify(reportWriter)
        .createOrUpdateSingleProcessReport(
            eq(AgenticControlDashboardService.FAILURE_RATE_BY_VERSION_REPORT_ID),
            isNull(),
            dataCaptor.capture(),
            eq(AgenticControlDashboardService.FAILURE_RATE_BY_VERSION_NAME),
            eq(AgenticControlDashboardService.FAILURE_RATE_BY_VERSION_DESCRIPTION),
            isNull());

    final ProcessReportDataDto reportData = dataCaptor.getValue();
    assertThat(reportData.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(reportData.getView().getProperties()).contains(ViewProperty.PERCENTAGE);
    assertThat(reportData.getGroupBy()).isInstanceOf(ProcessDefinitionVersionGroupByDto.class);
    assertThat(reportData.getVisualization()).isEqualTo(ProcessVisualization.BAR);
    assertThat(reportData.isAgenticControlReport()).isTrue();
  }

  @Test
  void shouldMarkFailureRateByVersionTileAsL1Only() {
    // given
    when(dashboardReader.getDashboard(AGENTIC_DASHBOARD_ID)).thenReturn(Optional.empty());

    // when
    underTest.reconcile();

    // then the tile carries the L1-only visibility flag so the frontend hides it in the L0 view
    final DashboardDefinitionRestDto saved = captureSavedDashboard();
    final DashboardReportTileDto failureRateTile =
        saved.getTiles().stream()
            .filter(
                tile ->
                    AgenticControlDashboardService.FAILURE_RATE_BY_VERSION_REPORT_ID.equals(
                        tile.getId()))
            .findFirst()
            .orElseThrow();
    assertThat(failureRateTile.getConfiguration())
        .isEqualTo(Map.of("section", "reliabilityAndToolCalls", "visibleInL1Only", true));
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

  private void assertDurationReportSeeded(
      final String reportId,
      final String nameKey,
      final String descKey,
      final double expectedPercentile) {
    // given
    when(dashboardReader.getDashboard(AGENTIC_DASHBOARD_ID)).thenReturn(Optional.empty());
    final ArgumentCaptor<ProcessReportDataDto> dataCaptor =
        ArgumentCaptor.forClass(ProcessReportDataDto.class);

    // when
    underTest.reconcile();

    // then the report is upserted with the correct deterministic ID and localization keys
    verify(reportWriter)
        .createOrUpdateSingleProcessReport(
            eq(reportId), isNull(), dataCaptor.capture(), eq(nameKey), eq(descKey), isNull());

    final ProcessReportDataDto reportData = dataCaptor.getValue();
    assertThat(reportData.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(reportData.getView().getProperties()).contains(ViewProperty.DURATION);
    assertThat(reportData.getConfiguration().getAggregationTypes())
        .containsExactly(new AggregationDto(AggregationType.PERCENTILE, expectedPercentile));
    assertThat(reportData.isAgenticControlReport()).isTrue();
  }
}
