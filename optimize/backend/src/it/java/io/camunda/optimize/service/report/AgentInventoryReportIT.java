/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.report;

import static io.camunda.optimize.service.dashboard.AgentInsightsDashboardService.AGENT_INVENTORY_REPORT_ID;
import static io.camunda.optimize.service.db.DatabaseConstants.ZEEBE_DATA_SOURCE;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.TenantDto;
import io.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import io.camunda.optimize.dto.optimize.query.agent.AgentDefinitionDto;
import io.camunda.optimize.dto.optimize.query.agent.AgentRunDto;
import io.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.SingleReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.RollingDateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceEndDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataAgentDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import io.camunda.optimize.rest.exceptions.NotFoundException;
import io.camunda.optimize.service.dashboard.AgentInsightsDashboardService;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.AgentDefinitionWriter;
import io.camunda.optimize.service.db.writer.AgentRunWriter;
import io.camunda.optimize.service.db.writer.ProcessDefinitionWriter;
import io.camunda.optimize.service.security.CCSMTokenService;
import io.camunda.optimize.service.security.util.tenant.CamundaCCSMTenantAuthorizationService;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Evaluates the seeded agent inventory report the way Hub does: by report id, with the date range
 * in the evaluate body and a page size large enough for every agent.
 */
class AgentInventoryReportIT extends AbstractBrokerlessZeebeCCSMIT {

  private static final String USER_ID = "testUser";

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
  void shouldReturnOneRowPerAgentSortedByAgentInstances() {
    // given two agents of one process: the first with one run, the second with two
    final String processKey = uniqueKey();
    persistProcessDefinition(processKey, "Customer onboarding", ZEEBE_DEFAULT_TENANT_ID);
    persistAgentDefinition(
        processKey, "kyc", ZEEBE_DEFAULT_TENANT_ID, "KYC review agent", "AI_AGENT_SUB_PROCESS");
    persistAgentDefinition(
        processKey, "fraud", ZEEBE_DEFAULT_TENANT_ID, "Fraud signal agent", "EXTERNAL_AGENT");
    final OffsetDateTime lastEnd =
        OffsetDateTime.now().minusHours(1).truncatedTo(ChronoUnit.MILLIS);
    persistAgentRuns(
        completedRun(processKey, "kyc", ZEEBE_DEFAULT_TENANT_ID, 100L, lastEnd.minusHours(1)),
        completedRun(processKey, "fraud", ZEEBE_DEFAULT_TENANT_ID, 30L, lastEnd.minusHours(2)),
        completedRun(processKey, "fraud", ZEEBE_DEFAULT_TENANT_ID, 10L, lastEnd));

    // when
    final CommandEvaluationResult<?> result = evaluate(endedInLast30Days(), page(0, 1000));

    // then
    assertThat(rows(result))
        .extracting(
            RawDataAgentDto::getAgentId,
            RawDataAgentDto::getAgentName,
            RawDataAgentDto::getAgentType,
            RawDataAgentDto::getProcessDefinitionKey,
            RawDataAgentDto::getProcessName,
            RawDataAgentDto::getAgentInstances,
            RawDataAgentDto::getTotalTokens,
            RawDataAgentDto::getAverageTokensPerAgentInstance)
        .containsExactly(
            tuple(
                processKey + ":fraud",
                "Fraud signal agent",
                "EXTERNAL_AGENT",
                processKey,
                "Customer onboarding",
                2L,
                40L,
                20.0),
            tuple(
                processKey + ":kyc",
                "KYC review agent",
                "AI_AGENT_SUB_PROCESS",
                processKey,
                "Customer onboarding",
                1L,
                100L,
                100.0));
    assertThat(rows(result).get(0).getLastAgentInstanceEndDate().toInstant())
        .isEqualTo(lastEnd.toInstant());
    assertThat(result.getInstanceCount()).isEqualTo(3L);
  }

  @Test
  void shouldAddUpTheRunsOfOneAgentAcrossTenants() {
    // given one agent deployed to two tenants the user may see, renamed in tenant B's newer version
    final String tenantA = "tenant-a-" + UUID.randomUUID();
    final String tenantB = "tenant-b-" + UUID.randomUUID();
    final String processKey = uniqueKey();
    persistProcessDefinition(processKey, "Onboarding", tenantA);
    persistProcessDefinition(processKey, "Onboarding", tenantB);
    persistAgentDefinition(processKey, "kyc", tenantA, "Old name", "AI_AGENT_TASK", "1");
    persistAgentDefinition(processKey, "kyc", tenantB, "New name", "AI_AGENT_TASK", "2");
    final OffsetDateTime end = OffsetDateTime.now().minusHours(1);
    persistAgentRuns(
        completedRun(processKey, "kyc", tenantA, 10L, end),
        completedRun(processKey, "kyc", tenantB, 20L, end));

    // when
    final CommandEvaluationResult<?> result =
        withUserAuthorizedFor(
            List.of(tenantA, tenantB),
            userId -> evaluate(userId, endedInLast30Days(), page(0, 1000)));

    // then
    assertThat(rows(result))
        .extracting(
            RawDataAgentDto::getAgentId,
            RawDataAgentDto::getAgentName,
            RawDataAgentDto::getAgentInstances,
            RawDataAgentDto::getTotalTokens)
        .containsExactly(tuple(processKey + ":kyc", "New name", 2L, 30L));
  }

  @Test
  void shouldOnlyListAgentsWithACompletedRunInTheRange() {
    // given an agent that only ran before the range or is still running
    final String processKey = uniqueKey();
    persistProcessDefinition(processKey, "Onboarding", ZEEBE_DEFAULT_TENANT_ID);
    persistAgentDefinition(processKey, "kyc", ZEEBE_DEFAULT_TENANT_ID, "KYC", "AI_AGENT_TASK");
    persistAgentDefinition(processKey, "idle", ZEEBE_DEFAULT_TENANT_ID, "Idle", "AI_AGENT_TASK");
    final AgentRunDto runningRun =
        completedRun(processKey, "idle", ZEEBE_DEFAULT_TENANT_ID, 5L, null);
    runningRun.setStatus("ACTIVE");
    persistAgentRuns(
        completedRun(
            processKey, "kyc", ZEEBE_DEFAULT_TENANT_ID, 10L, OffsetDateTime.now().minusHours(1)),
        completedRun(
            processKey, "idle", ZEEBE_DEFAULT_TENANT_ID, 5L, OffsetDateTime.now().minusDays(60)),
        runningRun);

    // when
    final CommandEvaluationResult<?> result = evaluate(endedInLast30Days(), page(0, 1000));

    // then
    assertThat(rows(result))
        .extracting(RawDataAgentDto::getAgentId)
        .containsExactly(processKey + ":kyc");
  }

  @Test
  void shouldPageTheAgentRows() {
    // given three agents with three, two and one runs
    final String processKey = uniqueKey();
    persistProcessDefinition(processKey, "Onboarding", ZEEBE_DEFAULT_TENANT_ID);
    final OffsetDateTime end = OffsetDateTime.now().minusHours(1);
    for (final String agent : List.of("a", "b", "c")) {
      persistAgentDefinition(processKey, agent, ZEEBE_DEFAULT_TENANT_ID, agent, "AI_AGENT_TASK");
    }
    persistAgentRuns(
        completedRun(processKey, "a", ZEEBE_DEFAULT_TENANT_ID, 1L, end),
        completedRun(processKey, "a", ZEEBE_DEFAULT_TENANT_ID, 1L, end),
        completedRun(processKey, "a", ZEEBE_DEFAULT_TENANT_ID, 1L, end),
        completedRun(processKey, "b", ZEEBE_DEFAULT_TENANT_ID, 1L, end),
        completedRun(processKey, "b", ZEEBE_DEFAULT_TENANT_ID, 1L, end),
        completedRun(processKey, "c", ZEEBE_DEFAULT_TENANT_ID, 1L, end));

    // when
    final CommandEvaluationResult<?> result = evaluate(endedInLast30Days(), page(1, 1));

    // then
    assertThat(rows(result))
        .extracting(RawDataAgentDto::getAgentId)
        .containsExactly(processKey + ":b");
  }

  @Test
  void shouldHideTheInventoryWhenPhase2IsDisabled() {
    // given
    setAgenticPhase2Enabled(false);

    // when / then
    assertThatThrownBy(() -> evaluate(endedInLast30Days(), page(0, 1000)))
        .isInstanceOf(NotFoundException.class);
  }

  private CommandEvaluationResult<?> evaluate(
      final AdditionalProcessReportEvaluationFilterDto filters, final PaginationDto pagination) {
    return evaluate(USER_ID, filters, pagination);
  }

  private CommandEvaluationResult<?> evaluate(
      final String userId,
      final AdditionalProcessReportEvaluationFilterDto filters,
      final PaginationDto pagination) {
    return ((SingleReportEvaluationResult<?>)
            embeddedOptimizeExtension
                .getBean(ReportEvaluationService.class)
                .evaluateSavedReportWithAdditionalFilters(
                    userId, ZoneId.of("UTC"), AGENT_INVENTORY_REPORT_ID, filters, pagination)
                .getEvaluationResult())
        .getFirstCommandResult();
  }

  @SuppressWarnings("unchecked")
  private static List<RawDataAgentDto> rows(final CommandEvaluationResult<?> result) {
    return (List<RawDataAgentDto>) result.getFirstMeasureData();
  }

  private static PaginationDto page(final int offset, final int limit) {
    return new PaginationDto(limit, offset);
  }

  private static AdditionalProcessReportEvaluationFilterDto endedInLast30Days() {
    final InstanceEndDateFilterDto filter = new InstanceEndDateFilterDto();
    filter.setData(new RollingDateFilterDataDto(new RollingDateFilterStartDto(30L, DateUnit.DAYS)));
    filter.setFilterLevel(FilterApplicationLevel.INSTANCE);
    return new AdditionalProcessReportEvaluationFilterDto(List.of(filter));
  }

  private void persistProcessDefinition(
      final String key, final String name, final String tenantId) {
    embeddedOptimizeExtension
        .getBean(ProcessDefinitionWriter.class)
        .importProcessDefinitions(
            List.of(
                ProcessDefinitionOptimizeDto.builder()
                    .id(key + ":1:" + tenantId)
                    .key(key)
                    .version("1")
                    .name(name)
                    .dataSource(new ZeebeDataSourceDto("test-source", 1))
                    .tenantId(tenantId)
                    .bpmn20Xml("<definitions/>")
                    .build()));
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private void persistAgentDefinition(
      final String processKey,
      final String flowNodeId,
      final String tenantId,
      final String name,
      final String agentType,
      final String... versions) {
    final AgentDefinitionDto definition = new AgentDefinitionDto();
    definition.setAgentId(processKey + ":" + flowNodeId);
    definition.setTenantId(tenantId);
    definition.setProcessDefinitionKey(processKey);
    definition.setFlowNodeId(flowNodeId);
    definition.setName(name);
    definition.setAgentType(agentType);
    definition.setProcessDefinitionVersions(
        versions.length == 0 ? List.of("1") : List.of(versions));
    embeddedOptimizeExtension
        .getBean(DatabaseClient.class)
        .executeImportRequestsAsBulk(
            "test agent definitions",
            embeddedOptimizeExtension
                .getBean(AgentDefinitionWriter.class)
                .generateAgentDefinitionImports(List.of(definition)),
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

  private static AgentRunDto completedRun(
      final String processKey,
      final String flowNodeId,
      final String tenantId,
      final long totalTokens,
      final OffsetDateTime endDate) {
    final AgentRunDto run = new AgentRunDto();
    run.setAgentInstanceId(UUID.randomUUID().toString());
    run.setAgentId(processKey + ":" + flowNodeId);
    run.setFlowNodeId(flowNodeId);
    run.setProcessDefinitionKey(processKey);
    run.setProcessDefinitionId(processKey + ":1:" + tenantId);
    run.setProcessDefinitionVersion("1");
    run.setProcessInstanceId(UUID.randomUUID().toString());
    run.setTenantId(tenantId);
    run.setStartDate(OffsetDateTime.now().minusDays(90));
    run.setEndDate(endDate);
    run.setStatus("COMPLETED");
    run.setTotalTokens(totalTokens);
    return run;
  }

  /**
   * Tenant authorizations come from Identity through {@link CCSMTokenService}; it is swapped on the
   * shared authorization service for the call instead of replaced as a bean, which would start a
   * second Spring context.
   */
  private <T> T withUserAuthorizedFor(
      final List<String> tenantIds, final Function<String, T> callAsUser) {
    final CamundaCCSMTenantAuthorizationService tenantAuthorizationService =
        embeddedOptimizeExtension.getBean(CamundaCCSMTenantAuthorizationService.class);
    final Object originalTokenService =
        ReflectionTestUtils.getField(tenantAuthorizationService, "ccsmTokenService");
    // a new user per call, because authorized tenants are cached per user
    final String userId = "user-" + UUID.randomUUID();
    final String token = "token-" + userId;
    final CCSMTokenService tokenService = mock(CCSMTokenService.class);
    when(tokenService.getCurrentUserIdFromAuthToken()).thenReturn(Optional.of(userId));
    when(tokenService.getCurrentUserAuthToken()).thenReturn(Optional.of(token));
    when(tokenService.getAuthorizedTenantsFromToken(token))
        .thenReturn(
            tenantIds.stream()
                .map(tenantId -> new TenantDto(tenantId, tenantId, ZEEBE_DATA_SOURCE))
                .toList());
    embeddedOptimizeExtension.getConfigurationService().setMultiTenancyEnabled(true);
    ReflectionTestUtils.setField(tenantAuthorizationService, "ccsmTokenService", tokenService);
    try {
      return callAsUser.apply(userId);
    } finally {
      ReflectionTestUtils.setField(
          tenantAuthorizationService, "ccsmTokenService", originalTokenService);
      embeddedOptimizeExtension.getConfigurationService().setMultiTenancyEnabled(false);
    }
  }

  private void setAgenticPhase2Enabled(final boolean enabled) {
    embeddedOptimizeExtension
        .getConfigurationService()
        .getAgenticPhase2Configuration()
        .setEnabled(enabled);
    embeddedOptimizeExtension.reloadConfiguration();
  }

  private static String uniqueKey() {
    return "agent-inventory-process-" + UUID.randomUUID();
  }
}
