/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.report;

import static io.camunda.optimize.dto.optimize.query.report.single.ViewProperty.CACHE_READ_TOKENS;
import static io.camunda.optimize.dto.optimize.query.report.single.ViewProperty.CACHE_WRITE_TOKENS;
import static io.camunda.optimize.dto.optimize.query.report.single.ViewProperty.FREQUENCY;
import static io.camunda.optimize.dto.optimize.query.report.single.ViewProperty.INPUT_TOKENS;
import static io.camunda.optimize.dto.optimize.query.report.single.ViewProperty.OUTPUT_TOKENS;
import static io.camunda.optimize.dto.optimize.query.report.single.ViewProperty.REASONING_TOKENS;
import static io.camunda.optimize.dto.optimize.query.report.single.ViewProperty.TOTAL_TOKENS;
import static io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.AVERAGE;
import static io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.SUM;
import static io.camunda.optimize.service.db.DatabaseConstants.ZEEBE_DATA_SOURCE;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.ReportType;
import io.camunda.optimize.dto.optimize.TenantDto;
import io.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import io.camunda.optimize.dto.optimize.query.agent.AgentDefinitionDto;
import io.camunda.optimize.dto.optimize.query.agent.AgentRunDto;
import io.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.SingleReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.RollingDateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceEndDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import io.camunda.optimize.dto.optimize.query.report.single.result.MeasureDto;
import io.camunda.optimize.rest.exceptions.NotFoundException;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.reader.ReportReader;
import io.camunda.optimize.service.db.writer.AgentDefinitionWriter;
import io.camunda.optimize.service.db.writer.AgentRunWriter;
import io.camunda.optimize.service.db.writer.ProcessDefinitionWriter;
import io.camunda.optimize.service.db.writer.ReportWriter;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import io.camunda.optimize.service.exceptions.evaluation.ReportEvaluationException;
import io.camunda.optimize.service.security.CCSMTokenService;
import io.camunda.optimize.service.security.util.tenant.CamundaCCSMTenantAuthorizationService;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Evaluates agentRun views against the agent run index the way the seeded agent insights reports
 * do: no stored definitions (the user's definitions are filled in at evaluation), completed runs
 * only through a stored filter, and the date range from the evaluate body.
 */
class AgentRunReportIT extends AbstractBrokerlessZeebeCCSMIT {

  private static final String USER_ID = "testUser";
  private static final List<ViewProperty> TOKEN_PROPERTIES =
      List.of(
          TOTAL_TOKENS,
          INPUT_TOKENS,
          OUTPUT_TOKENS,
          CACHE_READ_TOKENS,
          CACHE_WRITE_TOKENS,
          REASONING_TOKENS);

  @BeforeEach
  void enablePhase2() {
    setAgenticPhase2Enabled(true);
  }

  @AfterEach
  void disablePhase2() {
    setAgenticPhase2Enabled(false);
  }

  @Test
  void shouldSumAndAverageEveryTokenViewOverCompletedRuns() {
    // given
    final String processKey = uniqueKey();
    persistAgenticProcess(processKey, ZEEBE_DEFAULT_TENANT_ID);
    persistAgentRuns(
        completedRun(processKey, 1L), completedRun(processKey, 3L), runningRun(processKey, 100L));
    final String reportId = createAgentRunReport(TOKEN_PROPERTIES, completedRunsOnly());

    // when
    final List<MeasureDto<?>> measures = evaluateMeasures(reportId, noExtraFilters());

    // then each token field of the two completed runs is summed and averaged; the running run
    // is left out
    final List<org.assertj.core.groups.Tuple> expected = new ArrayList<>();
    for (final ViewProperty property : TOKEN_PROPERTIES) {
      final double factor = tokenFactor(property);
      expected.add(tuple(property, SUM, factor * 4));
      expected.add(tuple(property, AVERAGE, factor * 2));
    }
    assertThat(measures)
        .extracting(
            MeasureDto::getProperty,
            measure -> measure.getAggregationType().getType(),
            MeasureDto::getData)
        .containsExactlyInAnyOrderElementsOf(expected);
  }

  @Test
  void shouldCountCompletedRuns() {
    // given
    final String processKey = uniqueKey();
    persistAgenticProcess(processKey, ZEEBE_DEFAULT_TENANT_ID);
    persistAgentRuns(
        completedRun(processKey, 1L), completedRun(processKey, 2L), runningRun(processKey, 3L));
    final String reportId = createAgentRunReport(List.of(FREQUENCY), completedRunsOnly());

    // when
    final CommandEvaluationResult<?> result =
        evaluate(reportId, noExtraFilters()).getFirstCommandResult();

    // then
    assertThat(result.getMeasures()).singleElement().extracting(MeasureDto::getData).isEqualTo(2.0);
    assertThat(result.getInstanceCount()).isEqualTo(2L);
  }

  @Test
  void shouldOnlyCountRunsThatEndedInTheDateRange() {
    // given
    final String processKey = uniqueKey();
    persistAgenticProcess(processKey, ZEEBE_DEFAULT_TENANT_ID);
    final AgentRunDto oldRun = completedRun(processKey, 5L);
    oldRun.setEndDate(OffsetDateTime.now().minusDays(30));
    persistAgentRuns(completedRun(processKey, 1L), oldRun);
    final String reportId = createAgentRunReport(List.of(FREQUENCY), completedRunsOnly());

    // when
    final CommandEvaluationResult<?> result =
        evaluate(reportId, endedInLast(7, DateUnit.DAYS)).getFirstCommandResult();

    // then
    assertThat(result.getMeasures()).singleElement().extracting(MeasureDto::getData).isEqualTo(1.0);
    assertThat(result.getInstanceCount()).isEqualTo(1L);
  }

  @Test
  void shouldOnlyCountRunsOfProcessDefinitionsInTheUsersTenants() {
    // given a visible process, and runs of a process the user's tenants do not contain
    final String visibleKey = uniqueKey();
    final String hiddenKey = uniqueKey();
    persistAgenticProcess(visibleKey, ZEEBE_DEFAULT_TENANT_ID);
    final AgentRunDto hiddenTenantRun = completedRun(visibleKey, 7L);
    hiddenTenantRun.setTenantId("tenant-the-user-cannot-see");
    persistAgentRuns(completedRun(visibleKey, 1L), completedRun(hiddenKey, 5L), hiddenTenantRun);
    final String reportId = createAgentRunReport(List.of(TOTAL_TOKENS), completedRunsOnly());

    // when
    final List<MeasureDto<?>> measures = evaluateMeasures(reportId, noExtraFilters());

    // then
    assertThat(measures)
        .extracting(measure -> measure.getAggregationType().getType(), MeasureDto::getData)
        .containsExactlyInAnyOrder(
            tuple(SUM, tokenFactor(TOTAL_TOKENS)), tuple(AVERAGE, tokenFactor(TOTAL_TOKENS)));
  }

  @Test
  void shouldReturnNoRunsWhenTheUserSeesNoProcessDefinition() {
    // given runs, but no imported process definition
    persistAgentRuns(completedRun(uniqueKey(), 1L));
    final String tokensReportId = createAgentRunReport(List.of(TOTAL_TOKENS), completedRunsOnly());
    final String countReportId = createAgentRunReport(List.of(FREQUENCY), completedRunsOnly());

    // when
    final List<MeasureDto<?>> tokenMeasures = evaluateMeasures(tokensReportId, noExtraFilters());
    final List<MeasureDto<?>> countMeasures = evaluateMeasures(countReportId, noExtraFilters());

    // then sums and counts are 0, averages have no value
    assertThat(tokenMeasures)
        .extracting(measure -> measure.getAggregationType().getType(), MeasureDto::getData)
        .containsExactlyInAnyOrder(tuple(SUM, 0.0), tuple(AVERAGE, null));
    assertThat(countMeasures).singleElement().extracting(MeasureDto::getData).isEqualTo(0.0);
  }

  @Test
  void shouldOnlyCountRunsOfAgentsWithAnImportedDefinition() {
    // given two agents of one process, and only the first agent's definition imported
    final String processKey = uniqueKey();
    persistAgenticProcess(processKey, ZEEBE_DEFAULT_TENANT_ID);
    final AgentRunDto runOfUndefinedAgent = completedRun(processKey, 5L);
    runOfUndefinedAgent.setAgentId(processKey + ":agent-without-definition");
    persistAgentRuns(completedRun(processKey, 1L), runOfUndefinedAgent);
    final String reportId = createAgentRunReport(List.of(FREQUENCY), completedRunsOnly());

    // when
    final CommandEvaluationResult<?> result =
        evaluate(reportId, noExtraFilters()).getFirstCommandResult();

    // then only the run of the defined agent is counted
    assertThat(result.getMeasures()).singleElement().extracting(MeasureDto::getData).isEqualTo(1.0);
    assertThat(result.getInstanceCount()).isEqualTo(1L);
  }

  @Test
  void shouldOnlyCountRunsOfAgentsDefinedOnTheRunsTenant() {
    // given a process on two tenants the user may see, with the agent only defined on tenant A
    final String tenantA = "tenant-a-" + UUID.randomUUID();
    final String tenantB = "tenant-b-" + UUID.randomUUID();
    final String processKey = uniqueKey();
    persistAgenticProcess(processKey, tenantA);
    persistProcessDefinition(processKey, tenantB);
    final AgentRunDto runOnTenantA = completedRun(processKey, 1L);
    runOnTenantA.setTenantId(tenantA);
    final AgentRunDto runOnTenantB = completedRun(processKey, 5L);
    runOnTenantB.setTenantId(tenantB);
    persistAgentRuns(runOnTenantA, runOnTenantB);
    final String reportId = createAgentRunReport(List.of(TOTAL_TOKENS), completedRunsOnly());

    // when
    final List<MeasureDto<?>> measures =
        withUserAuthorizedFor(
            List.of(tenantA, tenantB), () -> evaluateMeasures(reportId, noExtraFilters()));

    // then only the run on tenant A, where the agent has a definition, is counted
    assertThat(measures)
        .extracting(measure -> measure.getAggregationType().getType(), MeasureDto::getData)
        .containsExactlyInAnyOrder(
            tuple(SUM, tokenFactor(TOTAL_TOKENS)), tuple(AVERAGE, tokenFactor(TOTAL_TOKENS)));
  }

  @Test
  void shouldRejectFiltersThatAgentRunsDoNotSupport() {
    // given
    final String processKey = uniqueKey();
    persistAgenticProcess(processKey, ZEEBE_DEFAULT_TENANT_ID);
    final String reportId =
        createAgentRunReport(
            List.of(FREQUENCY),
            ProcessFilterBuilder.filter().runningInstancesOnly().add().buildList());

    // when / then
    assertThatThrownBy(() -> evaluate(reportId, noExtraFilters()))
        .isInstanceOf(ReportEvaluationException.class)
        .hasRootCauseInstanceOf(OptimizeValidationException.class);
  }

  @Test
  void shouldHideAgentRunReportsWhenPhase2IsOff() {
    // given
    final String reportId = createAgentRunReport(List.of(FREQUENCY), completedRunsOnly());
    setAgenticPhase2Enabled(false);

    // when / then
    assertThatThrownBy(() -> evaluate(reportId, noExtraFilters()))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void shouldRejectEditingAndDeletingAgentInsightsReports() {
    // given
    final String reportId = createAgentRunReport(List.of(FREQUENCY), completedRunsOnly());
    final ReportService reportService = embeddedOptimizeExtension.getBean(ReportService.class);
    final SingleProcessReportDefinitionRequestDto update =
        new SingleProcessReportDefinitionRequestDto(
            agentRunReportData(List.of(TOTAL_TOKENS), List.of()));
    update.setName("Edited agent runs");

    // when / then
    assertThatThrownBy(
            () -> reportService.updateSingleProcessReport(reportId, update, USER_ID, true))
        .isInstanceOf(OptimizeValidationException.class)
        .hasMessageContaining("System-generated reports cannot be updated");
    assertThatThrownBy(() -> reportService.deleteReportAsUser(USER_ID, reportId, true))
        .isInstanceOf(OptimizeValidationException.class)
        .hasMessageContaining("System-generated reports cannot be deleted");
  }

  @Test
  void shouldKeepAgentInsightsReportsOutOfTheReportPickerAndCount() {
    // given
    final String reportId = createAgentRunReport(List.of(FREQUENCY), completedRunsOnly());
    final ReportReader reportReader = embeddedOptimizeExtension.getBean(ReportReader.class);
    assertThat(reportReader.getReport(reportId)).isPresent();

    // when
    final List<ReportDefinitionDto> pickerReports = reportReader.getAllPrivateReportsOmitXml();
    final long processReportCount = reportReader.getReportCount(ReportType.PROCESS);

    // then
    assertThat(pickerReports).extracting(ReportDefinitionDto::getId).doesNotContain(reportId);
    assertThat(processReportCount).isZero();
  }

  private String createAgentRunReport(
      final List<ViewProperty> properties, final List<ProcessFilterDto<?>> filters) {
    final String reportId = "agent-run-report-" + UUID.randomUUID();
    embeddedOptimizeExtension
        .getBean(ReportWriter.class)
        .createOrUpdateSingleProcessReport(
            reportId, null, agentRunReportData(properties, filters), "Agent runs", null, null);
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();
    return reportId;
  }

  private static ProcessReportDataDto agentRunReportData(
      final List<ViewProperty> properties, final List<ProcessFilterDto<?>> filters) {
    final ProcessReportDataDto data =
        ProcessReportDataDto.builder()
            .definitions(new ArrayList<>())
            .view(new ProcessViewDto(ProcessViewEntity.AGENT_RUN, properties))
            .groupBy(new NoneGroupByDto())
            .filter(new ArrayList<>(filters))
            .agentInsightsReport(true)
            .build();
    data.getConfiguration()
        .setAggregationTypes(new AggregationDto(SUM), new AggregationDto(AVERAGE));
    return data;
  }

  private List<MeasureDto<?>> evaluateMeasures(
      final String reportId, final AdditionalProcessReportEvaluationFilterDto filters) {
    final List<MeasureDto<?>> measures = new ArrayList<>();
    evaluate(reportId, filters)
        .getCommandEvaluationResults()
        .forEach(result -> measures.addAll(result.getMeasures()));
    return measures;
  }

  private SingleReportEvaluationResult<?> evaluate(
      final String reportId, final AdditionalProcessReportEvaluationFilterDto filters) {
    return (SingleReportEvaluationResult<?>)
        embeddedOptimizeExtension
            .getBean(ReportEvaluationService.class)
            .evaluateSavedReportWithAdditionalFilters(
                USER_ID, ZoneId.of("UTC"), reportId, filters, null)
            .getEvaluationResult();
  }

  /** A process definition with one agent, {@code <key>:agent}, the agent id of the test runs. */
  private void persistAgenticProcess(final String key, final String tenantId) {
    persistProcessDefinition(key, tenantId);
    final AgentDefinitionDto agentDefinition = new AgentDefinitionDto();
    agentDefinition.setAgentId(key + ":agent");
    agentDefinition.setTenantId(tenantId);
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

  private void persistProcessDefinition(final String key, final String tenantId) {
    embeddedOptimizeExtension
        .getBean(ProcessDefinitionWriter.class)
        .importProcessDefinitions(
            List.of(
                ProcessDefinitionOptimizeDto.builder()
                    .id(key + ":1:" + tenantId)
                    .key(key)
                    .version("1")
                    .name(key)
                    .dataSource(new ZeebeDataSourceDto("test-source", 1))
                    .tenantId(tenantId)
                    .bpmn20Xml("<definitions/>")
                    .build()));
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
   * Gives every token field of a run a different multiple of {@code unit}, so a wrong field mapping
   * changes the result.
   */
  private static AgentRunDto completedRun(final String processKey, final long unit) {
    final AgentRunDto run = runningRun(processKey, unit);
    run.setEndDate(OffsetDateTime.now().minusHours(1));
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
    run.setTotalTokens((long) tokenFactor(TOTAL_TOKENS) * unit);
    run.setInputTokens((long) tokenFactor(INPUT_TOKENS) * unit);
    run.setOutputTokens((long) tokenFactor(OUTPUT_TOKENS) * unit);
    run.setCacheReadTokens((long) tokenFactor(CACHE_READ_TOKENS) * unit);
    run.setCacheWriteTokens((long) tokenFactor(CACHE_WRITE_TOKENS) * unit);
    run.setReasoningTokens((long) tokenFactor(REASONING_TOKENS) * unit);
    return run;
  }

  private static double tokenFactor(final ViewProperty property) {
    return 10.0 * (TOKEN_PROPERTIES.indexOf(property) + 1);
  }

  private static List<ProcessFilterDto<?>> completedRunsOnly() {
    return ProcessFilterBuilder.filter().completedInstancesOnly().add().buildList();
  }

  private static AdditionalProcessReportEvaluationFilterDto noExtraFilters() {
    return new AdditionalProcessReportEvaluationFilterDto(List.of());
  }

  private static AdditionalProcessReportEvaluationFilterDto endedInLast(
      final long value, final DateUnit unit) {
    final InstanceEndDateFilterDto filter = new InstanceEndDateFilterDto();
    filter.setData(new RollingDateFilterDataDto(new RollingDateFilterStartDto(value, unit)));
    filter.setFilterLevel(FilterApplicationLevel.INSTANCE);
    return new AdditionalProcessReportEvaluationFilterDto(List.of(filter));
  }

  private void setAgenticPhase2Enabled(final boolean enabled) {
    embeddedOptimizeExtension
        .getConfigurationService()
        .getAgenticPhase2Configuration()
        .setEnabled(enabled);
    embeddedOptimizeExtension.reloadConfiguration();
  }

  /**
   * Tenant authorizations come from Identity through {@link CCSMTokenService}; it is swapped on the
   * shared authorization service for the call instead of replaced as a bean, which would start a
   * second Spring context.
   */
  private <T> T withUserAuthorizedFor(final List<String> tenantIds, final Supplier<T> call) {
    final CamundaCCSMTenantAuthorizationService tenantAuthorizationService =
        embeddedOptimizeExtension.getBean(CamundaCCSMTenantAuthorizationService.class);
    final Object originalTokenService =
        ReflectionTestUtils.getField(tenantAuthorizationService, "ccsmTokenService");
    final String token = "token-" + USER_ID;
    final CCSMTokenService tokenService = mock(CCSMTokenService.class);
    when(tokenService.getCurrentUserIdFromAuthToken()).thenReturn(Optional.of(USER_ID));
    when(tokenService.getCurrentUserAuthToken()).thenReturn(Optional.of(token));
    when(tokenService.getAuthorizedTenantsFromToken(token))
        .thenReturn(
            tenantIds.stream()
                .map(tenantId -> new TenantDto(tenantId, tenantId, ZEEBE_DATA_SOURCE))
                .toList());
    embeddedOptimizeExtension.getConfigurationService().setMultiTenancyEnabled(true);
    ReflectionTestUtils.setField(tenantAuthorizationService, "ccsmTokenService", tokenService);
    try {
      return call.get();
    } finally {
      ReflectionTestUtils.setField(
          tenantAuthorizationService, "ccsmTokenService", originalTokenService);
      embeddedOptimizeExtension.getConfigurationService().setMultiTenancyEnabled(false);
    }
  }

  private static String uniqueKey() {
    return "agentic-process-" + UUID.randomUUID();
  }
}
