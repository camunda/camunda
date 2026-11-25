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
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardTileType;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DimensionDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.PositionDto;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.NoneDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
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
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Seeds the agent insights dashboard that the Hub agents page reads, following {@link
 * BusinessValueDashboardService}: fixed report ids, upserted on every startup. Hub finds a tile by
 * its {@code configuration.tile} value and evaluates the report with the date range in the body.
 *
 * <p>Seeded only while agentic Phase 2 is switched on.
 */
@Component
public class AgentInsightsDashboardService {

  public static final String AGENT_INSIGHTS_DASHBOARD_ID = "agent-insights-dashboard";
  public static final String AGENT_INSIGHTS_DASHBOARD_NAME = "Agent insights";

  public static final String TILE_CONFIG_SECTION = "section";
  public static final String TILE_CONFIG_TILE = "tile";
  public static final String SECTION_SUMMARY = "summary";
  public static final String TILE_TOTAL_TOKENS = "totalTokens";
  public static final String TILE_PROCESS_DURATION = "processDuration";
  public static final String TILE_INCIDENT_RATE = "incidentRate";
  public static final String SECTION_INVENTORY = "inventory";
  public static final String TILE_AGENT_INVENTORY = "agentInventory";

  // Ids are UUIDv3 over these seed strings, so a seed is part of the persisted contract with Hub:
  // changing one orphans its report on every existing installation.
  public static final String TOTAL_TOKENS_REPORT_ID = idFromSeed("agent-insights-total-tokens");
  public static final String PROCESS_DURATION_REPORT_ID =
      idFromSeed("agent-insights-process-duration");
  public static final String INCIDENT_RATE_REPORT_ID = idFromSeed("agent-insights-incident-rate");
  public static final String AGENT_INVENTORY_REPORT_ID =
      idFromSeed("agent-insights-agent-inventory");

  private static final Logger LOG = LoggerFactory.getLogger(AgentInsightsDashboardService.class);

  private final DashboardWriter dashboardWriter;
  private final DashboardReader dashboardReader;
  private final ReportWriter reportWriter;
  private final ConfigurationService configurationService;

  public AgentInsightsDashboardService(
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
    if (configurationService.getEntityConfiguration().getCreateOnStartup()
        && configurationService.getAgenticPhase2Configuration().isEnabled()) {
      LOG.info("Reconciling agent insights dashboard");
      reconcile();
      LOG.info("Finished reconciling agent insights dashboard");
    }
  }

  public void reconcile() {
    final List<DashboardReportTileDto> tiles = new ArrayList<>();
    tiles.add(buildTotalTokensTile());
    tiles.add(buildProcessDurationTile());
    tiles.add(buildIncidentRateTile());
    tiles.add(buildAgentInventoryTile());

    final DashboardDefinitionRestDto dashboard = buildDashboard(tiles);
    if (dashboardReader.getDashboard(AGENT_INSIGHTS_DASHBOARD_ID).isEmpty()) {
      dashboardWriter.saveDashboard(dashboard);
    } else {
      dashboardWriter.updateDashboard(toUpdateDto(dashboard), AGENT_INSIGHTS_DASHBOARD_ID);
    }
  }

  // Run count, total tokens (sum and average per run) and the token breakdown of completed agent
  // runs. Every property gets both aggregations, so the breakdown also returns averages; Hub picks
  // measures by property and aggregation and ignores the ones it does not show.
  private DashboardReportTileDto buildTotalTokensTile() {
    final ProcessReportDataDto reportData =
        reportData(
            new ProcessViewDto(
                ProcessViewEntity.AGENT_RUN,
                List.of(
                    ViewProperty.FREQUENCY,
                    ViewProperty.TOTAL_TOKENS,
                    ViewProperty.INPUT_TOKENS,
                    ViewProperty.OUTPUT_TOKENS,
                    ViewProperty.CACHE_READ_TOKENS,
                    ViewProperty.CACHE_WRITE_TOKENS,
                    ViewProperty.REASONING_TOKENS)),
            List.of(
                new AggregationDto(AggregationType.SUM),
                new AggregationDto(AggregationType.AVERAGE)),
            ProcessFilterBuilder.filter().completedInstancesOnly().add().buildList());
    reportWriter.createOrUpdateSingleProcessReport(
        TOTAL_TOKENS_REPORT_ID, null, reportData, "Total tokens", null, null);
    return buildTile(TOTAL_TOKENS_REPORT_ID, 0, TILE_TOTAL_TOKENS);
  }

  private DashboardReportTileDto buildProcessDurationTile() {
    final ProcessReportDataDto reportData =
        reportData(
            new ProcessViewDto(ProcessViewEntity.PROCESS_INSTANCE, ViewProperty.DURATION),
            List.of(
                new AggregationDto(AggregationType.PERCENTILE, 50.0),
                new AggregationDto(AggregationType.PERCENTILE, 95.0)),
            completedAgenticInstances().buildList());
    reportWriter.createOrUpdateSingleProcessReport(
        PROCESS_DURATION_REPORT_ID, null, reportData, "Process duration", null, null);
    return buildTile(PROCESS_DURATION_REPORT_ID, 6, TILE_PROCESS_DURATION);
  }

  // Share of completed agentic process instances with a resolved incident, as the Phase 1
  // incident rate. The agentic baseline keeps non-agentic instances out of the denominator.
  private DashboardReportTileDto buildIncidentRateTile() {
    final ProcessReportDataDto reportData =
        reportData(
            new ProcessViewDto(ProcessViewEntity.PROCESS_INSTANCE, ViewProperty.PERCENTAGE),
            List.of(),
            completedAgenticInstances().withResolvedIncident().add().buildList());
    reportWriter.createOrUpdateSingleProcessReport(
        INCIDENT_RATE_REPORT_ID, null, reportData, "Incident rate", null, null);
    return buildTile(INCIDENT_RATE_REPORT_ID, 12, TILE_INCIDENT_RATE);
  }

  // One row per agent with a completed run in the range, sorted by agent instances; Hub asks for
  // every row and searches and sorts client-side.
  private DashboardReportTileDto buildAgentInventoryTile() {
    final ProcessReportDataDto reportData =
        reportData(
            new ProcessViewDto(ProcessViewEntity.AGENT_RUN, ViewProperty.RAW_DATA),
            List.of(),
            ProcessFilterBuilder.filter().completedInstancesOnly().add().buildList(),
            ProcessVisualization.TABLE);
    reportWriter.createOrUpdateSingleProcessReport(
        AGENT_INVENTORY_REPORT_ID, null, reportData, "Agent inventory", null, null);
    return DashboardReportTileDto.builder()
        .id(AGENT_INVENTORY_REPORT_ID)
        .type(DashboardTileType.OPTIMIZE_REPORT)
        .position(new PositionDto(0, 2))
        .dimensions(new DimensionDto(18, 6))
        .configuration(
            Map.of(TILE_CONFIG_SECTION, SECTION_INVENTORY, TILE_CONFIG_TILE, TILE_AGENT_INVENTORY))
        .build();
  }

  private static ProcessFilterBuilder completedAgenticInstances() {
    return ProcessFilterBuilder.filter().completedInstancesOnly().add().hasAgentInstances().add();
  }

  private static ProcessReportDataDto reportData(
      final ProcessViewDto view,
      final List<AggregationDto> aggregations,
      final List<ProcessFilterDto<?>> filters) {
    return reportData(view, aggregations, filters, ProcessVisualization.NUMBER);
  }

  private static ProcessReportDataDto reportData(
      final ProcessViewDto view,
      final List<AggregationDto> aggregations,
      final List<ProcessFilterDto<?>> filters,
      final ProcessVisualization visualization) {
    final SingleReportConfigurationDto configuration = new SingleReportConfigurationDto();
    if (!aggregations.isEmpty()) {
      configuration.setAggregationTypes(aggregations.toArray(AggregationDto[]::new));
    }
    return ProcessReportDataDto.builder()
        .definitions(Collections.emptyList())
        .view(view)
        .groupBy(new NoneGroupByDto())
        .distributedBy(new NoneDistributedByDto())
        .visualization(visualization)
        .configuration(configuration)
        .filter(filters)
        .agentInsightsReport(true)
        .build();
  }

  private static DashboardReportTileDto buildTile(
      final String reportId, final int x, final String tile) {
    return DashboardReportTileDto.builder()
        .id(reportId)
        .type(DashboardTileType.OPTIMIZE_REPORT)
        .position(new PositionDto(x, 0))
        .dimensions(new DimensionDto(6, 2))
        .configuration(Map.of(TILE_CONFIG_SECTION, SECTION_SUMMARY, TILE_CONFIG_TILE, tile))
        .build();
  }

  private static DashboardDefinitionRestDto buildDashboard(
      final List<DashboardReportTileDto> tiles) {
    final DashboardDefinitionRestDto dashboard = new DashboardDefinitionRestDto();
    dashboard.setId(AGENT_INSIGHTS_DASHBOARD_ID);
    dashboard.setName(AGENT_INSIGHTS_DASHBOARD_NAME);
    dashboard.setAgentInsightsDashboard(true);
    dashboard.setCollectionId(null);
    dashboard.setTiles(new ArrayList<>(tiles));
    return dashboard;
  }

  private static DashboardDefinitionUpdateDto toUpdateDto(final DashboardDefinitionRestDto source) {
    final DashboardDefinitionUpdateDto updateDto = new DashboardDefinitionUpdateDto();
    updateDto.setName(source.getName());
    updateDto.setTiles(source.getTiles());
    return updateDto;
  }

  private static String idFromSeed(final String seed) {
    return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
  }
}
