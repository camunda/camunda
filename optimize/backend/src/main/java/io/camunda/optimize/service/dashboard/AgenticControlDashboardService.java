/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.dashboard;

import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionUpdateDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardFilterDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardInstanceEndDateFilterDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardProcessScopeFilterDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardDateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardProcessScopeFilterDataDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardTileType;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DimensionDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.PositionDto;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.RollingDateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.NoneDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.AgentGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.ModelGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.StartDateGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.ToolGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import io.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import io.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import io.camunda.optimize.service.db.reader.DashboardReader;
import io.camunda.optimize.service.db.writer.DashboardWriter;
import io.camunda.optimize.service.db.writer.ReportWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AgenticControlDashboardService {

  public static final String AGENTIC_DASHBOARD_ID = "agentic-control-plane-dashboard";

  // Localization code resolved under the "agenticControl" category by DashboardRestMapper ->
  // LocalizationService.
  public static final String AGENTIC_DASHBOARD_NAME = "agenticControlPlaneDashboardName";

  public static final String KPI_EXECUTION_COMPLETED_NAME = "agenticKpiExecutionCompletedName";
  public static final String KPI_EXECUTION_COMPLETED_DESCRIPTION =
      "agenticKpiExecutionCompletedDescription";
  public static final String KPI_EXECUTION_AVG_DURATION_NAME = "agenticKpiExecutionAvgDurationName";
  public static final String KPI_EXECUTION_AVG_DURATION_DESCRIPTION =
      "agenticKpiExecutionAvgDurationDescription";
  public static final String KPI_EXECUTION_INCIDENT_RATE_NAME =
      "agenticKpiExecutionIncidentRateName";
  public static final String KPI_EXECUTION_INCIDENT_RATE_DESCRIPTION =
      "agenticKpiExecutionIncidentRateDescription";
  public static final String KPI_EXECUTION_AVG_TOKENS_NAME = "agenticKpiExecutionAvgTokensName";
  public static final String KPI_EXECUTION_AVG_TOKENS_DESCRIPTION =
      "agenticKpiExecutionAvgTokensDescription";
  public static final String KPI_EXECUTION_MEDIAN_TOKENS_NAME =
      "agenticKpiExecutionMedianTokensName";
  public static final String KPI_EXECUTION_MEDIAN_TOKENS_DESCRIPTION =
      "agenticKpiExecutionMedianTokensDescription";
  public static final String KPI_TOKEN_TREND_NAME = "agenticKpiTokenTrendName";
  public static final String KPI_TOKEN_TREND_FOOTNOTE = "agenticKpiTokenTrendFootnote";
  public static final String KPI_TOKEN_TREND_FOOTNOTE_KEY =
      "agenticControl.report." + KPI_TOKEN_TREND_FOOTNOTE;
  public static final String KPI_TOKEN_OUTLIER_BANDS_NAME = "agenticKpiTokenOutlierBandsName";
  public static final String KPI_TOKEN_OUTLIER_BANDS_DESCRIPTION =
      "agenticKpiTokenOutlierBandsDescription";
  public static final String KPI_TOKEN_OUTLIER_BANDS_FOOTNOTE =
      "agenticKpiTokenOutlierBandsFootnote";
  public static final String KPI_TOKEN_OUTLIER_BANDS_FOOTNOTE_KEY =
      "agenticControl.report." + KPI_TOKEN_OUTLIER_BANDS_FOOTNOTE;
  public static final String KPI_TOKEN_CONSUMERS_NAME = "agenticKpiTokenConsumersName";
  public static final String KPI_TOKEN_CONSUMERS_FOOTNOTE = "agenticKpiTokenConsumersFootnote";
  public static final String KPI_TOKEN_CONSUMERS_FOOTNOTE_KEY =
      "agenticControl.report." + KPI_TOKEN_CONSUMERS_FOOTNOTE;
  // Only the top N process definitions by total tokens are returned by the tile; the backend
  // computes this server-side via report pagination so it scales to very large data sets.
  public static final int TOKEN_CONSUMERS_LIMIT = 10;
  public static final String KPI_DURATION_P50_NAME = "agenticKpiDurationP50Name";
  public static final String KPI_DURATION_P50_DESCRIPTION = "agenticKpiDurationP50Description";
  public static final String KPI_DURATION_P95_NAME = "agenticKpiDurationP95Name";
  public static final String KPI_DURATION_P95_DESCRIPTION = "agenticKpiDurationP95Description";
  public static final String FAILURE_RATE_BY_VERSION_NAME = "agenticFailureRateByVersionName";
  public static final String FAILURE_RATE_BY_VERSION_DESCRIPTION =
      "agenticFailureRateByVersionDescription";
  public static final String KPI_TOOL_CALLS_NAME = "agenticKpiToolCallsName";
  public static final String KPI_TOOL_CALLS_DESCRIPTION = "agenticKpiToolCallsDescription";
  public static final String KPI_TOOL_CALLS_FOOTNOTE = "agenticKpiToolCallsFootnote";
  public static final String KPI_TOOL_CALLS_FOOTNOTE_KEY =
      "agenticControl.report." + KPI_TOOL_CALLS_FOOTNOTE;

  public static final String TOOL_CALLS_HEATMAP_NAME = "agenticToolCallsHeatmapName";
  public static final String TOOL_CALLS_HEATMAP_DESCRIPTION = "agenticToolCallsHeatmapDescription";

  public static final String DURATION_STABILITY_NAME = "agenticDurationStabilityName";
  public static final String DURATION_STABILITY_DESCRIPTION = "agenticDurationStabilityDescription";

  public static final String DURATION_HEATMAP_NAME = "agenticDurationHeatmapName";
  public static final String DURATION_HEATMAP_DESCRIPTION = "agenticDurationHeatmapDescription";

  // Deterministic report IDs — derived from fixed seed strings so IDs are stable across restarts
  // and DB reimports. Same seed always produces the same UUID (UUID v3 / name-based).
  public static final String KPI_COMPLETED_REPORT_ID =
      UUID.nameUUIDFromBytes("agentic-kpi-completed-instances".getBytes(StandardCharsets.UTF_8))
          .toString();
  public static final String KPI_AVG_DURATION_REPORT_ID =
      UUID.nameUUIDFromBytes("agentic-kpi-avg-duration".getBytes(StandardCharsets.UTF_8))
          .toString();
  public static final String KPI_INCIDENT_RATE_REPORT_ID =
      UUID.nameUUIDFromBytes("agentic-kpi-incident-rate".getBytes(StandardCharsets.UTF_8))
          .toString();
  public static final String KPI_AVG_TOKENS_REPORT_ID =
      UUID.nameUUIDFromBytes("agentic-kpi-avg-tokens".getBytes(StandardCharsets.UTF_8)).toString();
  public static final String KPI_MEDIAN_TOKENS_REPORT_ID =
      UUID.nameUUIDFromBytes("agentic-kpi-median-tokens".getBytes(StandardCharsets.UTF_8))
          .toString();
  public static final String DURATION_STABILITY_REPORT_ID =
      UUID.nameUUIDFromBytes("agentic-duration-stability".getBytes(StandardCharsets.UTF_8))
          .toString();
  public static final String TOKEN_TREND_REPORT_ID =
      UUID.nameUUIDFromBytes("agentic-token-trend".getBytes(StandardCharsets.UTF_8)).toString();
  public static final String TOKEN_CONSUMERS_REPORT_ID =
      UUID.nameUUIDFromBytes("agentic-token-consumers".getBytes(StandardCharsets.UTF_8)).toString();
  public static final String TOKEN_OUTLIER_BANDS_REPORT_ID =
      UUID.nameUUIDFromBytes("agentic-token-outlier-bands".getBytes(StandardCharsets.UTF_8))
          .toString();
  public static final String KPI_DURATION_P50_REPORT_ID =
      UUID.nameUUIDFromBytes("agentic-duration-p50".getBytes(StandardCharsets.UTF_8)).toString();
  public static final String KPI_DURATION_P95_REPORT_ID =
      UUID.nameUUIDFromBytes("agentic-duration-p95".getBytes(StandardCharsets.UTF_8)).toString();
  public static final String FAILURE_RATE_BY_VERSION_REPORT_ID =
      UUID.nameUUIDFromBytes("agentic-failure-rate-by-version".getBytes(StandardCharsets.UTF_8))
          .toString();
  public static final String KPI_TOOL_CALLS_REPORT_ID =
      UUID.nameUUIDFromBytes("agentic-tool-calls-total".getBytes(StandardCharsets.UTF_8))
          .toString();
  public static final String TOOL_CALLS_HEATMAP_REPORT_ID =
      UUID.nameUUIDFromBytes("agentic-tool-calls-heatmap".getBytes(StandardCharsets.UTF_8))
          .toString();
  public static final String DURATION_HEATMAP_REPORT_ID =
      UUID.nameUUIDFromBytes("agentic-duration-heatmap".getBytes(StandardCharsets.UTF_8))
          .toString();

  // Phase 2 agent run tiles.
  public static final String AGENT_INCIDENT_RATE_REPORT_ID =
      UUID.nameUUIDFromBytes("agentic-run-incident-rate".getBytes(StandardCharsets.UTF_8))
          .toString();
  public static final String AGENT_INCIDENT_RATE_NAME = "agenticRunIncidentRateName";
  public static final String AGENT_INCIDENT_RATE_DESCRIPTION = "agenticRunIncidentRateDescription";
  public static final String AGENT_LIMIT_HIT_RATE_REPORT_ID =
      UUID.nameUUIDFromBytes("agentic-run-limit-hit-rate".getBytes(StandardCharsets.UTF_8))
          .toString();
  public static final String AGENT_LIMIT_HIT_RATE_NAME = "agenticRunLimitHitRateName";
  public static final String AGENT_LIMIT_HIT_RATE_DESCRIPTION = "agenticRunLimitHitRateDescription";
  public static final String AGENT_DURATION_P50_REPORT_ID =
      UUID.nameUUIDFromBytes("agentic-run-duration-p50".getBytes(StandardCharsets.UTF_8))
          .toString();
  public static final String AGENT_DURATION_P50_NAME = "agenticRunDurationP50Name";
  public static final String AGENT_DURATION_P50_DESCRIPTION = "agenticRunDurationP50Description";
  public static final String AGENT_DURATION_P95_REPORT_ID =
      UUID.nameUUIDFromBytes("agentic-run-duration-p95".getBytes(StandardCharsets.UTF_8))
          .toString();
  public static final String AGENT_DURATION_P95_NAME = "agenticRunDurationP95Name";
  public static final String AGENT_DURATION_P95_DESCRIPTION = "agenticRunDurationP95Description";
  public static final String AGENT_ITERATIONS_REPORT_ID =
      UUID.nameUUIDFromBytes("agentic-run-iterations".getBytes(StandardCharsets.UTF_8)).toString();
  public static final String AGENT_ITERATIONS_NAME = "agenticRunIterationsName";
  public static final String AGENT_ITERATIONS_DESCRIPTION = "agenticRunIterationsDescription";
  public static final String AGENT_HUMAN_STEPS_REPORT_ID =
      UUID.nameUUIDFromBytes("agentic-run-human-steps".getBytes(StandardCharsets.UTF_8)).toString();
  public static final String AGENT_HUMAN_STEPS_NAME = "agenticRunHumanStepsName";
  public static final String AGENT_HUMAN_STEPS_DESCRIPTION = "agenticRunHumanStepsDescription";
  public static final String AGENT_RUNS_REPORT_ID =
      UUID.nameUUIDFromBytes("agentic-run-count".getBytes(StandardCharsets.UTF_8)).toString();
  public static final String AGENT_RUNS_NAME = "agenticRunCountName";
  public static final String AGENT_RUNS_DESCRIPTION = "agenticRunCountDescription";
  public static final String AGENT_RUNS_OVER_TIME_REPORT_ID =
      UUID.nameUUIDFromBytes("agentic-run-count-over-time".getBytes(StandardCharsets.UTF_8))
          .toString();
  public static final String AGENT_RUNS_OVER_TIME_NAME = "agenticRunCountOverTimeName";
  public static final String AGENT_RUNS_OVER_TIME_DESCRIPTION =
      "agenticRunCountOverTimeDescription";
  public static final String AGENT_RUNS_BY_AGENT_REPORT_ID =
      UUID.nameUUIDFromBytes("agentic-run-count-by-agent".getBytes(StandardCharsets.UTF_8))
          .toString();
  public static final String AGENT_RUNS_BY_AGENT_NAME = "agenticRunCountByAgentName";
  public static final String AGENT_RUNS_BY_AGENT_DESCRIPTION = "agenticRunCountByAgentDescription";
  public static final String AGENT_TOOL_CALLS_BY_TOOL_REPORT_ID =
      UUID.nameUUIDFromBytes("agentic-run-tool-calls-by-tool".getBytes(StandardCharsets.UTF_8))
          .toString();
  public static final String AGENT_TOOL_CALLS_BY_TOOL_NAME = "agenticRunToolCallsByToolName";
  public static final String AGENT_TOOL_CALLS_BY_TOOL_DESCRIPTION =
      "agenticRunToolCallsByToolDescription";
  public static final String AGENT_ADOPTION_RATE_REPORT_ID =
      UUID.nameUUIDFromBytes("agentic-run-adoption-rate".getBytes(StandardCharsets.UTF_8))
          .toString();
  public static final String AGENT_ADOPTION_RATE_NAME = "agenticRunAdoptionRateName";
  public static final String AGENT_ADOPTION_RATE_DESCRIPTION = "agenticRunAdoptionRateDescription";
  public static final String AGENT_AVG_TOKENS_REPORT_ID =
      UUID.nameUUIDFromBytes("agentic-run-avg-tokens".getBytes(StandardCharsets.UTF_8)).toString();
  public static final String AGENT_AVG_TOKENS_NAME = "agenticRunAvgTokensName";
  public static final String AGENT_AVG_TOKENS_DESCRIPTION = "agenticRunAvgTokensDescription";
  public static final String AGENT_TOKENS_OVER_TIME_REPORT_ID =
      UUID.nameUUIDFromBytes("agentic-run-tokens-over-time".getBytes(StandardCharsets.UTF_8))
          .toString();
  public static final String AGENT_TOKENS_OVER_TIME_NAME = "agenticRunTokensOverTimeName";
  public static final String AGENT_TOKENS_OVER_TIME_DESCRIPTION =
      "agenticRunTokensOverTimeDescription";
  public static final String AGENT_TOKENS_BY_MODEL_REPORT_ID =
      UUID.nameUUIDFromBytes("agentic-run-tokens-by-model".getBytes(StandardCharsets.UTF_8))
          .toString();
  public static final String AGENT_TOKENS_BY_MODEL_NAME = "agenticRunTokensByModelName";
  public static final String AGENT_TOKENS_BY_MODEL_DESCRIPTION =
      "agenticRunTokensByModelDescription";

  private static final long INSTANCE_END_DATE_ROLLING_DAYS = 30L;

  // Tile configuration keys consumed by the Agentic Control Plane frontend.
  private static final String TILE_CONFIG_SECTION = "section";
  private static final String TILE_CONFIG_FOOTNOTE = "footnote";
  private static final String TILE_CONFIG_TOP_N = "topN";
  // When set, the frontend only renders the tile at the dashboard root (L0) and hides it once a
  // single process is selected, where a per-process breakdown is no longer meaningful.
  private static final String TILE_CONFIG_VISIBLE_IN_L0_ONLY = "visibleInL0Only";
  // When set, the frontend only renders the tile once a single process is selected (L1).
  private static final String TILE_CONFIG_VISIBLE_IN_L1_ONLY = "visibleInL1Only";
  // Section values that group tiles within the dashboard layout.
  private static final String TILE_SECTION_TOKEN = "token";
  private static final String TILE_SECTION_DURATION = "duration";
  private static final String TILE_SECTION_RELIABILITY_AND_TOOL_CALLS = "reliabilityAndToolCalls";
  private static final String TILE_SECTION_AGENT_HEALTH = "agentHealth";
  private static final String TILE_SECTION_VOLUME = "volume";
  private static final String TILE_SECTION_COST_AND_USAGE = "costAndUsage";

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(AgenticControlDashboardService.class);

  private final DashboardWriter dashboardWriter;
  private final DashboardReader dashboardReader;
  private final ReportWriter reportWriter;
  private final ConfigurationService configurationService;

  public AgenticControlDashboardService(
      final DashboardWriter dashboardWriter,
      final DashboardReader dashboardReader,
      final ReportWriter reportWriter,
      final ConfigurationService configurationService) {
    this.dashboardWriter = dashboardWriter;
    this.dashboardReader = dashboardReader;
    this.reportWriter = reportWriter;
    this.configurationService = configurationService;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void init() {
    if (configurationService.getEntityConfiguration().getCreateOnStartup()) {
      LOG.info("Reconciling Agentic Control Plane dashboard");
      reconcile();
      LOG.info("Finished reconciling Agentic Control Plane dashboard");
    }
  }

  public void reconcile() {
    // Always upsert all KPI reports so config changes are applied on every restart.
    // Report IDs are deterministic so upserts are safe and tile references never orphan.
    final List<DashboardReportTileDto> tiles = new ArrayList<>();
    tiles.addAll(buildAgentHealthTiles());
    tiles.addAll(buildVolumeTiles());
    tiles.addAll(buildCostAndUsageTiles());

    // Always upsert the dashboard too — tile list can grow across versions and the cold-start
    // guard would leave an existing deployment stuck on the old layout.
    final DashboardDefinitionRestDto dashboard = buildAgentDashboard(tiles);
    if (dashboardReader.getDashboard(AGENTIC_DASHBOARD_ID).isEmpty()) {
      dashboardWriter.saveDashboard(dashboard);
    } else {
      dashboardWriter.updateDashboard(toUpdateDto(dashboard), AGENTIC_DASHBOARD_ID);
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Phase 2 tiles: agent-level metrics, one document per agent run in the flat agent-instance
  // index. Every report is restricted to runs of completed process instances.
  // ---------------------------------------------------------------------------------------------

  private List<DashboardReportTileDto> buildAgentHealthTiles() {
    return List.of(
        agentRunTile(
            AGENT_INCIDENT_RATE_REPORT_ID,
            AGENT_INCIDENT_RATE_NAME,
            AGENT_INCIDENT_RATE_DESCRIPTION,
            ViewProperty.INCIDENT_RATE,
            new NoneGroupByDto(),
            ProcessVisualization.NUMBER,
            List.of(),
            TILE_SECTION_AGENT_HEALTH,
            new PositionDto(0, 0),
            new DimensionDto(6, 2)),
        agentRunTile(
            AGENT_LIMIT_HIT_RATE_REPORT_ID,
            AGENT_LIMIT_HIT_RATE_NAME,
            AGENT_LIMIT_HIT_RATE_DESCRIPTION,
            ViewProperty.LIMIT_HIT_RATE,
            new NoneGroupByDto(),
            ProcessVisualization.NUMBER,
            List.of(),
            TILE_SECTION_AGENT_HEALTH,
            new PositionDto(6, 0),
            new DimensionDto(6, 2)),
        agentRunTile(
            AGENT_ITERATIONS_REPORT_ID,
            AGENT_ITERATIONS_NAME,
            AGENT_ITERATIONS_DESCRIPTION,
            ViewProperty.MODEL_CALLS,
            new NoneGroupByDto(),
            ProcessVisualization.NUMBER,
            List.of(new AggregationDto(AggregationType.AVERAGE)),
            TILE_SECTION_AGENT_HEALTH,
            new PositionDto(12, 0),
            new DimensionDto(6, 2)),
        agentRunTile(
            AGENT_DURATION_P50_REPORT_ID,
            AGENT_DURATION_P50_NAME,
            AGENT_DURATION_P50_DESCRIPTION,
            ViewProperty.DURATION,
            new NoneGroupByDto(),
            ProcessVisualization.NUMBER,
            List.of(new AggregationDto(AggregationType.PERCENTILE, 50.0)),
            TILE_SECTION_AGENT_HEALTH,
            new PositionDto(0, 2),
            new DimensionDto(6, 2)),
        agentRunTile(
            AGENT_DURATION_P95_REPORT_ID,
            AGENT_DURATION_P95_NAME,
            AGENT_DURATION_P95_DESCRIPTION,
            ViewProperty.DURATION,
            new NoneGroupByDto(),
            ProcessVisualization.NUMBER,
            List.of(new AggregationDto(AggregationType.PERCENTILE, 95.0)),
            TILE_SECTION_AGENT_HEALTH,
            new PositionDto(6, 2),
            new DimensionDto(6, 2)),
        agentRunTile(
            AGENT_HUMAN_STEPS_REPORT_ID,
            AGENT_HUMAN_STEPS_NAME,
            AGENT_HUMAN_STEPS_DESCRIPTION,
            ViewProperty.HUMAN_STEPS,
            new NoneGroupByDto(),
            ProcessVisualization.NUMBER,
            List.of(new AggregationDto(AggregationType.AVERAGE)),
            TILE_SECTION_AGENT_HEALTH,
            new PositionDto(12, 2),
            new DimensionDto(6, 2)));
  }

  private List<DashboardReportTileDto> buildVolumeTiles() {
    return List.of(
        agentRunTile(
            AGENT_RUNS_REPORT_ID,
            AGENT_RUNS_NAME,
            AGENT_RUNS_DESCRIPTION,
            ViewProperty.FREQUENCY,
            new NoneGroupByDto(),
            ProcessVisualization.NUMBER,
            List.of(),
            TILE_SECTION_VOLUME,
            new PositionDto(0, 0),
            new DimensionDto(9, 2)),
        agentRunTile(
            AGENT_ADOPTION_RATE_REPORT_ID,
            AGENT_ADOPTION_RATE_NAME,
            AGENT_ADOPTION_RATE_DESCRIPTION,
            ViewProperty.ADOPTION_RATE,
            new NoneGroupByDto(),
            ProcessVisualization.NUMBER,
            List.of(),
            TILE_SECTION_VOLUME,
            new PositionDto(9, 0),
            new DimensionDto(9, 2)),
        agentRunTile(
            AGENT_RUNS_OVER_TIME_REPORT_ID,
            AGENT_RUNS_OVER_TIME_NAME,
            AGENT_RUNS_OVER_TIME_DESCRIPTION,
            ViewProperty.FREQUENCY,
            weeklyStartDate(),
            ProcessVisualization.LINE,
            List.of(),
            TILE_SECTION_VOLUME,
            new PositionDto(0, 2),
            new DimensionDto(9, 4)),
        agentRunTile(
            AGENT_RUNS_BY_AGENT_REPORT_ID,
            AGENT_RUNS_BY_AGENT_NAME,
            AGENT_RUNS_BY_AGENT_DESCRIPTION,
            ViewProperty.FREQUENCY,
            new AgentGroupByDto(),
            ProcessVisualization.BAR,
            List.of(),
            TILE_SECTION_VOLUME,
            new PositionDto(9, 2),
            new DimensionDto(9, 4)),
        agentRunTile(
            AGENT_TOOL_CALLS_BY_TOOL_REPORT_ID,
            AGENT_TOOL_CALLS_BY_TOOL_NAME,
            AGENT_TOOL_CALLS_BY_TOOL_DESCRIPTION,
            ViewProperty.TOOL_CALLS,
            new ToolGroupByDto(),
            ProcessVisualization.BAR,
            List.of(new AggregationDto(AggregationType.SUM)),
            TILE_SECTION_VOLUME,
            new PositionDto(0, 6),
            new DimensionDto(18, 4)));
  }

  private List<DashboardReportTileDto> buildCostAndUsageTiles() {
    return List.of(
        agentRunTile(
            AGENT_AVG_TOKENS_REPORT_ID,
            AGENT_AVG_TOKENS_NAME,
            AGENT_AVG_TOKENS_DESCRIPTION,
            ViewProperty.TOTAL_TOKENS,
            new NoneGroupByDto(),
            ProcessVisualization.NUMBER,
            List.of(new AggregationDto(AggregationType.AVERAGE)),
            TILE_SECTION_COST_AND_USAGE,
            new PositionDto(0, 0),
            new DimensionDto(18, 2)),
        agentRunTile(
            AGENT_TOKENS_OVER_TIME_REPORT_ID,
            AGENT_TOKENS_OVER_TIME_NAME,
            AGENT_TOKENS_OVER_TIME_DESCRIPTION,
            List.of(ViewProperty.TOTAL_TOKENS, ViewProperty.FREQUENCY),
            weeklyStartDate(),
            ProcessVisualization.LINE,
            List.of(new AggregationDto(AggregationType.SUM)),
            TILE_SECTION_COST_AND_USAGE,
            new PositionDto(0, 2),
            new DimensionDto(9, 4)),
        agentRunTile(
            AGENT_TOKENS_BY_MODEL_REPORT_ID,
            AGENT_TOKENS_BY_MODEL_NAME,
            AGENT_TOKENS_BY_MODEL_DESCRIPTION,
            ViewProperty.TOTAL_TOKENS,
            new ModelGroupByDto(),
            ProcessVisualization.BAR,
            List.of(new AggregationDto(AggregationType.SUM)),
            TILE_SECTION_COST_AND_USAGE,
            new PositionDto(9, 2),
            new DimensionDto(9, 4)));
  }

  private DashboardReportTileDto agentRunTile(
      final String reportId,
      final String nameKey,
      final String descriptionKey,
      final ViewProperty viewProperty,
      final ProcessGroupByDto<?> groupBy,
      final ProcessVisualization visualization,
      final List<AggregationDto> aggregationTypes,
      final String section,
      final PositionDto position,
      final DimensionDto dimensions) {
    return agentRunTile(
        reportId,
        nameKey,
        descriptionKey,
        List.of(viewProperty),
        groupBy,
        visualization,
        aggregationTypes,
        section,
        position,
        dimensions);
  }

  private DashboardReportTileDto agentRunTile(
      final String reportId,
      final String nameKey,
      final String descriptionKey,
      final List<ViewProperty> viewProperties,
      final ProcessGroupByDto<?> groupBy,
      final ProcessVisualization visualization,
      final List<AggregationDto> aggregationTypes,
      final String section,
      final PositionDto position,
      final DimensionDto dimensions) {
    final SingleReportConfigurationDto configuration =
        SingleReportConfigurationDto.builder().subtitle(descriptionKey).build();
    configuration.setValueFormat(valueFormatFor(viewProperties.getFirst()));
    if (!aggregationTypes.isEmpty()) {
      configuration.setAggregationTypes(aggregationTypes.toArray(AggregationDto[]::new));
    }
    if (visualization == ProcessVisualization.BAR) {
      configuration.setHorizontalBar(true);
      configuration.setSorting(
          new ReportSortingDto(ReportSortingDto.SORT_BY_VALUE, SortOrder.DESC));
    }
    final ProcessReportDataDto reportData =
        ProcessReportDataDto.builder()
            .definitions(Collections.emptyList())
            .view(new ProcessViewDto(ProcessViewEntity.AGENT_RUN, viewProperties))
            .groupBy(groupBy)
            .distributedBy(new NoneDistributedByDto())
            .visualization(visualization)
            .configuration(configuration)
            .filter(ProcessFilterBuilder.filter().completedInstancesOnly().add().buildList())
            .agenticControlReport(true)
            .build();
    reportWriter.createOrUpdateSingleProcessReport(
        reportId, null, reportData, nameKey, descriptionKey, null);
    return buildTile(reportId, position, dimensions, Map.of(TILE_CONFIG_SECTION, section));
  }

  // Rates are percentages; averages such as iterations per run need decimals, which the raw
  // frequency formatter keeps. Durations use the default duration format of the view property.
  private static String valueFormatFor(final ViewProperty viewProperty) {
    if (ViewProperty.INCIDENT_RATE.equals(viewProperty)
        || ViewProperty.LIMIT_HIT_RATE.equals(viewProperty)
        || ViewProperty.ADOPTION_RATE.equals(viewProperty)) {
      return "percentage";
    }
    if (ViewProperty.DURATION.equals(viewProperty)) {
      return null;
    }
    return "frequency";
  }

  private static StartDateGroupByDto weeklyStartDate() {
    final StartDateGroupByDto groupBy = new StartDateGroupByDto();
    groupBy.setValue(new DateGroupByValueDto(AggregateByDateUnit.WEEK));
    return groupBy;
  }

  private DashboardDefinitionRestDto buildAgentDashboard(final List<DashboardReportTileDto> tiles) {
    final DashboardDefinitionRestDto dashboard = new DashboardDefinitionRestDto();
    dashboard.setId(AGENTIC_DASHBOARD_ID);
    dashboard.setName(AGENTIC_DASHBOARD_NAME);
    dashboard.setAgenticControlDashboard(true);
    dashboard.setCollectionId(null);
    dashboard.setTiles(new ArrayList<>(tiles));
    dashboard.setAvailableFilters(buildAvailableFilters());
    return dashboard;
  }

  private List<DashboardFilterDto<?>> buildAvailableFilters() {
    final DashboardInstanceEndDateFilterDto endDateFilter = new DashboardInstanceEndDateFilterDto();
    final RollingDateFilterDataDto rolling =
        new RollingDateFilterDataDto(
            new RollingDateFilterStartDto(INSTANCE_END_DATE_ROLLING_DAYS, DateUnit.DAYS));
    endDateFilter.setData(new DashboardDateFilterDataDto(rolling));
    final DashboardProcessScopeFilterDto processScopeFilter = new DashboardProcessScopeFilterDto();
    processScopeFilter.setData(new DashboardProcessScopeFilterDataDto(null));
    final List<DashboardFilterDto<?>> availableFilters = new ArrayList<>();
    availableFilters.add(endDateFilter);
    availableFilters.add(processScopeFilter);
    return availableFilters;
  }

  private DashboardReportTileDto buildTile(
      final String reportId, final PositionDto position, final DimensionDto dimensions) {
    return buildTile(reportId, position, dimensions, null);
  }

  private DashboardReportTileDto buildTile(
      final String reportId,
      final PositionDto position,
      final DimensionDto dimensions,
      final Object configuration) {
    return DashboardReportTileDto.builder()
        .id(reportId)
        .type(DashboardTileType.OPTIMIZE_REPORT)
        .position(position)
        .dimensions(dimensions)
        .configuration(configuration)
        .build();
  }

  private DashboardDefinitionUpdateDto toUpdateDto(final DashboardDefinitionRestDto source) {
    final DashboardDefinitionUpdateDto updateDto = new DashboardDefinitionUpdateDto();
    updateDto.setName(source.getName());
    updateDto.setTiles(source.getTiles());
    updateDto.setAvailableFilters(source.getAvailableFilters());
    return updateDto;
  }
}
