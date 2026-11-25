/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.dashboard;

import static io.camunda.optimize.AgenticInstanceFixtures.agenticInstanceWithDuration;
import static io.camunda.optimize.AgenticInstanceFixtures.agenticInstanceWithTokens;
import static io.camunda.optimize.AgenticInstanceFixtures.repeat;
import static io.camunda.optimize.AgenticInstanceFixtures.resolvedIncident;
import static io.camunda.optimize.dto.optimize.query.report.single.ViewProperty.CACHE_READ_TOKENS;
import static io.camunda.optimize.dto.optimize.query.report.single.ViewProperty.CACHE_WRITE_TOKENS;
import static io.camunda.optimize.dto.optimize.query.report.single.ViewProperty.DURATION;
import static io.camunda.optimize.dto.optimize.query.report.single.ViewProperty.FREQUENCY;
import static io.camunda.optimize.dto.optimize.query.report.single.ViewProperty.INPUT_TOKENS;
import static io.camunda.optimize.dto.optimize.query.report.single.ViewProperty.OUTPUT_TOKENS;
import static io.camunda.optimize.dto.optimize.query.report.single.ViewProperty.REASONING_TOKENS;
import static io.camunda.optimize.dto.optimize.query.report.single.ViewProperty.TOTAL_TOKENS;
import static io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.AVERAGE;
import static io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.PERCENTILE;
import static io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.SUM;
import static io.camunda.optimize.service.dashboard.AgentInsightsDashboardService.AGENT_INSIGHTS_DASHBOARD_ID;
import static io.camunda.optimize.service.dashboard.AgentInsightsDashboardService.AGENT_INVENTORY_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgentInsightsDashboardService.INCIDENT_RATE_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgentInsightsDashboardService.PROCESS_DURATION_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgentInsightsDashboardService.TOTAL_TOKENS_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticReportFilters.rollingEndDateFilter;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import io.camunda.optimize.dto.optimize.query.agent.AgentDefinitionDto;
import io.camunda.optimize.dto.optimize.query.agent.AgentRunDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import io.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import io.camunda.optimize.dto.optimize.query.report.SingleReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.result.MeasureDto;
import io.camunda.optimize.dto.optimize.rest.AuthorizedDashboardDefinitionResponseDto;
import io.camunda.optimize.rest.exceptions.NotFoundException;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.reader.DashboardReader;
import io.camunda.optimize.service.db.reader.EntitiesReader;
import io.camunda.optimize.service.db.writer.AgentDefinitionWriter;
import io.camunda.optimize.service.db.writer.AgentRunWriter;
import io.camunda.optimize.service.db.writer.ProcessDefinitionWriter;
import io.camunda.optimize.service.report.ReportEvaluationService;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Evaluates the seeded agent insights summary tiles the way Hub does: by report id, with the date
 * range in the evaluate body.
 */
class AgentInsightsDashboardIT extends AbstractBrokerlessZeebeCCSMIT {

  private static final String USER_ID = "testUser";
  private static final long HOUR_MS = 3_600_000L;

  @BeforeEach
  void setUp() {
    setAgenticPhase2Enabled(true);
    // cleanupOptimizeData() wipes the seed after every test
    embeddedOptimizeExtension.getBean(AgentInsightsDashboardService.class).reconcile();
  }

  @AfterEach
  void tearDown() {
    setAgenticPhase2Enabled(false);
  }

  @Test
  void shouldServeDashboardWithOneTilePerReport() {
    // when
    final AuthorizedDashboardDefinitionResponseDto response =
        embeddedOptimizeExtension.getBean(DashboardService.class).getAgentInsightsDashboard();

    // then
    assertThat(response.getDefinitionDto().getId()).isEqualTo(AGENT_INSIGHTS_DASHBOARD_ID);
    assertThat(response.getDefinitionDto().isAgentInsightsDashboard()).isTrue();
    assertThat(response.getDefinitionDto().getTiles())
        .extracting(DashboardReportTileDto::getId, DashboardReportTileDto::getConfiguration)
        .containsExactlyInAnyOrder(
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
  void shouldKeepSeededDashboardOutOfUserDashboardCountAndEntityList() {
    // given the dashboard seeded in setUp
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final long dashboardCount =
        embeddedOptimizeExtension.getBean(DashboardReader.class).getDashboardCount();
    final List<CollectionEntity> entities =
        embeddedOptimizeExtension.getBean(EntitiesReader.class).getAllPrivateEntities();

    // then
    assertThat(dashboardCount).isZero();
    assertThat(entities)
        .extracting(CollectionEntity::getId)
        .doesNotContain(AGENT_INSIGHTS_DASHBOARD_ID);
  }

  @Test
  void shouldSumTokensOfCompletedRunsInTheRange() {
    // given two completed runs in the range, one completed before it, one still running, and one
    // of an agent whose definition is not imported yet
    final String processKey = uniqueKey();
    persistProcessDefinition(processKey);
    persistAgentDefinition(processKey);
    final AgentRunDto runOfUndefinedAgent = completedRun(processKey, 10_000L, 1);
    runOfUndefinedAgent.setAgentId(processKey + ":agent-without-definition");
    persistAgentRuns(
        completedRun(processKey, 1L, 1),
        completedRun(processKey, 3L, 1),
        completedRun(processKey, 100L, 40),
        runningRun(processKey, 1000L),
        runOfUndefinedAgent);

    // when
    final List<MeasureDto<?>> measures =
        evaluateMeasures(TOTAL_TOKENS_REPORT_ID, rollingEndDateFilter(30L, DateUnit.DAYS));

    // then the measures Hub shows add up the two runs in the range
    assertThat(measures)
        .extracting(
            MeasureDto::getProperty,
            measure ->
                measure.getAggregationType() == null
                    ? null
                    : measure.getAggregationType().getType(),
            MeasureDto::getData)
        .contains(
            tuple(FREQUENCY, null, 2.0),
            tuple(TOTAL_TOKENS, SUM, 40.0),
            tuple(TOTAL_TOKENS, AVERAGE, 20.0),
            tuple(INPUT_TOKENS, SUM, 16.0),
            tuple(OUTPUT_TOKENS, SUM, 12.0),
            tuple(CACHE_READ_TOKENS, SUM, 8.0),
            tuple(CACHE_WRITE_TOKENS, SUM, 4.0),
            tuple(REASONING_TOKENS, SUM, 4.0));
  }

  @Test
  void shouldReturnPercentileDurationsOfCompletedAgenticInstances() {
    // given agentic instances of one hour and non-agentic instances of ten hours
    final String processKey = uniqueKey();
    persistProcessInstances(
        Stream.concat(
                repeat(3, () -> agenticInstanceWithDuration(processKey, HOUR_MS).build()).stream(),
                repeat(3, () -> completedInstance(processKey).duration(10 * HOUR_MS).build())
                    .stream())
            .toList());

    // when
    final List<MeasureDto<?>> measures =
        evaluateMeasures(PROCESS_DURATION_REPORT_ID, rollingEndDateFilter(30L, DateUnit.DAYS));

    // then
    assertThat(measures)
        .extracting(
            MeasureDto::getProperty,
            measure -> measure.getAggregationType().getType(),
            measure -> measure.getAggregationType().getValue(),
            MeasureDto::getData)
        .containsExactlyInAnyOrder(
            tuple(DURATION, PERCENTILE, 50.0, (double) HOUR_MS),
            tuple(DURATION, PERCENTILE, 95.0, (double) HOUR_MS));
  }

  @Test
  void shouldDivideIncidentRateByCompletedAgenticInstancesOnly() {
    // given one agentic instance with an incident, three without, and non-agentic instances
    final String processKey = uniqueKey();
    final List<ProcessInstanceDto> instances = new ArrayList<>();
    instances.add(resolvedIncident(agenticInstanceWithTokens(processKey, 1L, 1L)));
    instances.addAll(repeat(3, () -> agenticInstanceWithTokens(processKey, 1L, 1L).build()));
    instances.addAll(repeat(6, () -> completedInstance(processKey).build()));
    persistProcessInstances(instances);

    // when
    final List<MeasureDto<?>> measures =
        evaluateMeasures(INCIDENT_RATE_REPORT_ID, rollingEndDateFilter(30L, DateUnit.DAYS));

    // then
    assertThat(measures).singleElement().extracting(MeasureDto::getData).isEqualTo(25.0);
  }

  @Test
  void shouldHideTheTilesWhenPhase2IsDisabled() {
    // given
    setAgenticPhase2Enabled(false);

    // when / then
    assertThatThrownBy(
            () ->
                evaluateMeasures(TOTAL_TOKENS_REPORT_ID, rollingEndDateFilter(30L, DateUnit.DAYS)))
        .isInstanceOf(NotFoundException.class);
  }

  private List<MeasureDto<?>> evaluateMeasures(
      final String reportId, final AdditionalProcessReportEvaluationFilterDto filters) {
    final List<MeasureDto<?>> measures = new ArrayList<>();
    ((SingleReportEvaluationResult<?>)
            embeddedOptimizeExtension
                .getBean(ReportEvaluationService.class)
                .evaluateSavedReportWithAdditionalFilters(
                    USER_ID, ZoneId.of("UTC"), reportId, filters, null)
                .getEvaluationResult())
        .getCommandEvaluationResults()
        .forEach(result -> measures.addAll(result.getMeasures()));
    return measures;
  }

  private void persistProcessDefinition(final String key) {
    embeddedOptimizeExtension
        .getBean(ProcessDefinitionWriter.class)
        .importProcessDefinitions(
            List.of(
                ProcessDefinitionOptimizeDto.builder()
                    .id(key + ":1:1")
                    .key(key)
                    .version("1")
                    .name(key)
                    .dataSource(new ZeebeDataSourceDto("test-source", 1))
                    .tenantId(ZEEBE_DEFAULT_TENANT_ID)
                    .bpmn20Xml("<definitions/>")
                    .build()));
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  /** The agent {@code <key>:agent}, the agent id of the test runs. */
  private void persistAgentDefinition(final String key) {
    final AgentDefinitionDto agentDefinition = new AgentDefinitionDto();
    agentDefinition.setAgentId(key + ":agent");
    agentDefinition.setTenantId(ZEEBE_DEFAULT_TENANT_ID);
    agentDefinition.setProcessDefinitionKey(key);
    agentDefinition.setFlowNodeId("agent");
    agentDefinition.setName("Agent");
    agentDefinition.setAgentType("AI_AGENT_TASK");
    agentDefinition.setProcessDefinitionVersions(List.of("1"));
    embeddedOptimizeExtension
        .getBean(DatabaseClient.class)
        .executeImportRequestsAsBulk(
            "test agent definitions",
            embeddedOptimizeExtension
                .getBean(AgentDefinitionWriter.class)
                .generateAgentDefinitionImports(List.of(agentDefinition)),
            false);
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private void persistAgentRuns(final AgentRunDto... runs) {
    embeddedOptimizeExtension
        .getBean(DatabaseClient.class)
        .executeImportRequestsAsBulk(
            "test agent runs",
            embeddedOptimizeExtension
                .getBean(AgentRunWriter.class)
                .generateAgentRunImports(List.of(runs)),
            false);
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  /**
   * A run with {@code 10 * unit} total tokens: input 4, output 3, cache read 2 and cache write 1
   * parts of it, and reasoning 1 part (inside output), so a wrong field mapping changes the result.
   */
  private static AgentRunDto completedRun(
      final String processKey, final long unit, final int endedDaysAgo) {
    final AgentRunDto run = runningRun(processKey, unit);
    run.setStartDate(OffsetDateTime.now().minusDays(endedDaysAgo).minusHours(1));
    run.setEndDate(OffsetDateTime.now().minusDays(endedDaysAgo));
    run.setStatus("COMPLETED");
    return run;
  }

  private static AgentRunDto runningRun(final String processKey, final long unit) {
    final AgentRunDto run = new AgentRunDto();
    run.setAgentInstanceId(UUID.randomUUID().toString());
    run.setAgentId(processKey + ":agent");
    run.setProcessDefinitionKey(processKey);
    run.setProcessDefinitionId(processKey + ":1:1");
    run.setProcessDefinitionVersion("1");
    run.setProcessInstanceId(UUID.randomUUID().toString());
    run.setTenantId(ZEEBE_DEFAULT_TENANT_ID);
    run.setStartDate(OffsetDateTime.now().minusHours(2));
    run.setStatus("ACTIVE");
    run.setTotalTokens(10 * unit);
    run.setInputTokens(4 * unit);
    run.setOutputTokens(3 * unit);
    run.setCacheReadTokens(2 * unit);
    run.setCacheWriteTokens(unit);
    run.setReasoningTokens(unit);
    return run;
  }

  private void setAgenticPhase2Enabled(final boolean enabled) {
    embeddedOptimizeExtension
        .getConfigurationService()
        .getAgenticPhase2Configuration()
        .setEnabled(enabled);
    embeddedOptimizeExtension.reloadConfiguration();
  }

  private static String uniqueKey() {
    return "agent-insights-process-" + UUID.randomUUID();
  }
}
