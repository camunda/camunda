/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auditlog;

import static io.camunda.it.auditlog.AuditLogUtils.DEFAULT_USERNAME;
import static io.camunda.it.auditlog.AuditLogUtils.PROCESS_A_ID;
import static io.camunda.it.auditlog.AuditLogUtils.TENANT_A;
import static io.camunda.it.util.TestHelper.waitForBatchOperationCompleted;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.AuditLogActorTypeEnum;
import io.camunda.client.api.search.enums.AuditLogCategoryEnum;
import io.camunda.client.api.search.enums.AuditLogEntityTypeEnum;
import io.camunda.client.api.search.enums.AuditLogOperationTypeEnum;
import io.camunda.client.api.search.enums.AuditLogResultEnum;
import io.camunda.client.api.search.enums.BatchOperationType;
import io.camunda.client.api.search.filter.AuditLogFilter;
import io.camunda.client.api.search.response.AuditLogResult;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class AuditLogSearchClientIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withMultiTenancyEnabled()
          .withAuthorizationsEnabled()
          .withAuthenticatedAccess();

  private static CamundaClient adminClient;
  private static AuditLogUtils utils;

  @BeforeAll
  static void setup() {
    utils = new AuditLogUtils(adminClient);
    utils.generateDefaults();
    utils.await();
  }

  @Test
  void shouldGetAuditLogByKey(@Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given
    final var search = client.newAuditLogSearchRequest().page(p -> p.limit(1)).send().join();

    // when
    final var auditLog = search.items().getFirst();
    final var log = client.newAuditLogGetRequest(auditLog.getAuditLogKey()).send().join();

    // then
    assertThat(log).isNotNull();
    assertThat(log.getAuditLogKey()).isEqualTo(auditLog.getAuditLogKey());
    assertThat(log.getEntityKey()).isEqualTo(auditLog.getEntityKey());
    assertThat(log.getEntityType()).isEqualTo(auditLog.getEntityType());
    assertThat(log.getOperationType()).isEqualTo(auditLog.getOperationType());
    assertThat(log.getCategory()).isEqualTo(auditLog.getCategory());
  }

  @Test
  void shouldSearchAuditLogByProcessInstanceKeyAndOperationType(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given
    final var processInstance = utils.getProcessInstances().getFirst();

    // when
    final var auditLogItems =
        client
            .newAuditLogSearchRequest()
            .filter(
                fn ->
                    fn.processInstanceKey(String.valueOf(processInstance.getProcessInstanceKey()))
                        .operationType(AuditLogOperationTypeEnum.CREATE))
            .send()
            .join();

    // then
    final var auditLog = auditLogItems.items().getFirst();
    assertThat(auditLog).isNotNull();
    assertCommonAuditLogFields(
        auditLog,
        AuditLogEntityTypeEnum.PROCESS_INSTANCE,
        AuditLogOperationTypeEnum.CREATE,
        AuditLogCategoryEnum.DEPLOYED_RESOURCES);
    assertThat(auditLog.getProcessDefinitionId()).isEqualTo(processInstance.getBpmnProcessId());
    assertThat(auditLog.getProcessDefinitionKey())
        .isEqualTo(String.valueOf(processInstance.getProcessDefinitionKey()));
    assertThat(auditLog.getProcessInstanceKey())
        .isEqualTo(String.valueOf(processInstance.getProcessInstanceKey()));
  }

  @Test
  void shouldSearchAuditLogByEntityKeyAndEntityType(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given
    final String tenantId = AuditLogUtils.TENANT_A;

    // when
    // look for tenant creation audit log as the entity key setter is overridden in transformer
    final var auditLogItems =
        awaitAuditLogEntryWithFilters(
            client,
            fn ->
                fn.entityKey(f -> f.in(tenantId))
                    .entityType(f -> f.like("TENANT"))
                    .operationType(AuditLogOperationTypeEnum.CREATE));

    // then
    final var auditLog = auditLogItems.getFirst();
    assertThat(auditLogItems.size()).isEqualTo(1);
    assertThat(auditLog).isNotNull();
    assertCommonAuditLogFields(
        auditLog,
        AuditLogEntityTypeEnum.TENANT,
        AuditLogOperationTypeEnum.CREATE,
        AuditLogCategoryEnum.ADMIN);
  }

  @Test
  void shouldSearchAuditLogByProcessDefinitionId(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given
    final var processInstance = utils.getProcessInstances().getFirst();

    // when
    final var auditLogItems =
        awaitAuditLogEntryWithFilters(
            client, fn -> fn.processDefinitionId(processInstance.getBpmnProcessId()));

    // then
    final var auditLog =
        auditLogItems.stream()
            .filter(
                auditLogResult ->
                    auditLogResult.getEntityType() == AuditLogEntityTypeEnum.PROCESS_INSTANCE)
            .findFirst()
            .orElse(null);
    assertThat(auditLogItems).isNotEmpty();
    assertThat(auditLogItems)
        .allMatch(
            auditLogResult ->
                auditLogResult.getProcessDefinitionId().equals(processInstance.getBpmnProcessId()));
    assertCommonAuditLogFields(
        auditLog,
        AuditLogEntityTypeEnum.PROCESS_INSTANCE,
        AuditLogOperationTypeEnum.CREATE,
        AuditLogCategoryEnum.DEPLOYED_RESOURCES);
  }

  @Test
  void shouldSearchAuditLogByProcessDefinitionKey(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given
    final var processInstance = utils.getProcessInstances().getFirst();

    // when
    final var auditLogItems =
        awaitAuditLogEntryWithFilters(
            client,
            fn ->
                fn.processDefinitionKey(String.valueOf(processInstance.getProcessDefinitionKey())));

    // then
    assertThat(auditLogItems).isNotEmpty();
    final var auditLog =
        auditLogItems.stream()
            .filter(
                auditLogResult ->
                    auditLogResult.getEntityType() == AuditLogEntityTypeEnum.PROCESS_INSTANCE)
            .findFirst()
            .orElse(null);
    assertThat(auditLogItems)
        .allMatch(
            auditLogResult ->
                auditLogResult.getProcessDefinitionId().equals(processInstance.getBpmnProcessId()));
    assertCommonAuditLogFields(
        auditLog,
        AuditLogEntityTypeEnum.PROCESS_INSTANCE,
        AuditLogOperationTypeEnum.CREATE,
        AuditLogCategoryEnum.DEPLOYED_RESOURCES);
  }

  @Test
  void shouldSearchAuditLogByElementInstanceKey(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given
    final var userTask = utils.getUserTasks().getFirst();
    final long elementInstanceKey = userTask.getElementInstanceKey();

    // when
    final var auditLogItems =
        awaitAuditLogEntryWithFilters(
            client, fn -> fn.elementInstanceKey(String.valueOf(elementInstanceKey)));

    // then
    assertThat(auditLogItems).isNotEmpty();
    final var auditLog = auditLogItems.getFirst();
    assertThat(auditLogItems)
        .allMatch(
            auditLogResult ->
                auditLogResult.getElementInstanceKey().equals(String.valueOf(elementInstanceKey)));
    assertCommonAuditLogFields(
        auditLog,
        AuditLogEntityTypeEnum.USER_TASK,
        AuditLogOperationTypeEnum.ASSIGN,
        AuditLogCategoryEnum.USER_TASKS);
  }

  @Test
  void shouldSearchAuditLogByUserTaskKey(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given
    final var userTask = utils.getUserTasks().getFirst();
    final long userTaskKey = userTask.getUserTaskKey();

    // when
    final var auditLogItems =
        awaitAuditLogEntryWithFilters(client, fn -> fn.userTaskKey(String.valueOf(userTaskKey)));

    // then
    assertThat(auditLogItems).isNotEmpty();
    final var auditLog = auditLogItems.getFirst();
    assertThat(auditLogItems)
        .allMatch(
            auditLogResult -> auditLogResult.getUserTaskKey().equals(String.valueOf(userTaskKey)));
    assertCommonAuditLogFields(
        auditLog,
        AuditLogEntityTypeEnum.USER_TASK,
        AuditLogOperationTypeEnum.ASSIGN,
        AuditLogCategoryEnum.USER_TASKS);
  }

  @Test
  void shouldSearchAuditLogByDecisionRequirementsId(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // when - deploy a decision (also deploys decision requirements)
    final var deployment =
        client
            .newDeployResourceCommand()
            .addResourceFromClasspath("decisions/decision_model.dmn")
            .tenantId(TENANT_A)
            .send()
            .join();

    final var decision = deployment.getDecisionRequirements().getFirst();

    // then - wait for audit log entry and verify (decision requirements map to RESOURCE entity)
    final var auditLogItems =
        awaitAuditLogEntryWithFilters(
            client,
            f ->
                f.operationType(AuditLogOperationTypeEnum.CREATE)
                    .decisionRequirementsId(
                        String.valueOf(decision.getDmnDecisionRequirementsId())));

    assertThat(auditLogItems).isNotEmpty();
    assertThat(auditLogItems)
        .allMatch(
            auditLogResult ->
                auditLogResult
                    .getDecisionRequirementsId()
                    .equals(decision.getDmnDecisionRequirementsId()));
    assertCommonAuditLogFields(
        auditLogItems.getFirst(),
        AuditLogEntityTypeEnum.RESOURCE,
        AuditLogOperationTypeEnum.CREATE,
        AuditLogCategoryEnum.DEPLOYED_RESOURCES);
  }

  @Test
  void shouldSearchAuditLogByDecisionRequirementsKey(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // when - deploy a decision (also deploys decision requirements)
    final var deployment =
        client
            .newDeployResourceCommand()
            .addResourceFromClasspath("decisions/decision_model.dmn")
            .tenantId(TENANT_A)
            .send()
            .join();

    final var decision = deployment.getDecisionRequirements().getFirst();
    final var decisionRequirementsKey = decision.getDecisionRequirementsKey();

    // then - wait for audit log entry and verify (decision requirements map to RESOURCE entity)
    final var auditLogItems =
        awaitAuditLogEntryWithFilters(
            client,
            f ->
                f.operationType(AuditLogOperationTypeEnum.CREATE)
                    .decisionRequirementsKey(String.valueOf(decisionRequirementsKey)));

    assertThat(auditLogItems).isNotEmpty();
    assertThat(auditLogItems)
        .allMatch(
            auditLogResult ->
                auditLogResult
                    .getDecisionRequirementsKey()
                    .equals(String.valueOf(decisionRequirementsKey)));
    assertCommonAuditLogFields(
        auditLogItems.getFirst(),
        AuditLogEntityTypeEnum.RESOURCE,
        AuditLogOperationTypeEnum.CREATE,
        AuditLogCategoryEnum.DEPLOYED_RESOURCES);
  }

  @Test
  void shouldSearchAuditLogByDecisionDefinitionId(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // when - deploy a decision
    final var deployment =
        client
            .newDeployResourceCommand()
            .addResourceFromClasspath("decisions/decision_model.dmn")
            .tenantId(TENANT_A)
            .send()
            .join();

    final var decision = deployment.getDecisions().getFirst();

    // then - wait for audit log entry and verify
    final var auditLogItems =
        awaitAuditLogEntryWithFilters(
            client, f -> f.decisionDefinitionId(decision.getDmnDecisionId()));

    assertThat(auditLogItems).isNotEmpty();
    assertThat(auditLogItems)
        .allMatch(
            auditLogResult ->
                auditLogResult.getDecisionDefinitionId().equals(decision.getDmnDecisionId()));
    assertCommonAuditLogFields(
        auditLogItems.getFirst(),
        AuditLogEntityTypeEnum.DECISION,
        AuditLogOperationTypeEnum.CREATE,
        AuditLogCategoryEnum.DEPLOYED_RESOURCES);
  }

  @Test
  void shouldSearchAuditLogByDecisionDefinitionKey(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // when - deploy a decision
    final var deployment =
        client
            .newDeployResourceCommand()
            .addResourceFromClasspath("decisions/decision_model.dmn")
            .tenantId(TENANT_A)
            .send()
            .join();

    final var decision = deployment.getDecisions().getFirst();
    final var decisionKey = decision.getDecisionKey();

    // then - wait for audit log entry and verify
    final var auditLogItems =
        awaitAuditLogEntryWithFilters(
            client, f -> f.decisionDefinitionKey(String.valueOf(decisionKey)));

    assertThat(auditLogItems).isNotEmpty();
    assertThat(auditLogItems)
        .allMatch(
            auditLogResult ->
                auditLogResult.getDecisionDefinitionKey().equals(String.valueOf(decisionKey)));
    assertCommonAuditLogFields(
        auditLogItems.getFirst(),
        AuditLogEntityTypeEnum.DECISION,
        AuditLogOperationTypeEnum.CREATE,
        AuditLogCategoryEnum.DEPLOYED_RESOURCES);
  }

  @Test
  void shouldSearchAuditLogsByBatchOperationType(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given - create a batch operation
    final var processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_A_ID)
            .latestVersion()
            .tenantId(AuditLogUtils.TENANT_A)
            .send()
            .join();
    final var processInstanceKey = processInstance.getProcessInstanceKey();

    waitForProcessInstancesToStart(
        client, f -> f.processInstanceKey(processInstanceKey).tenantId(TENANT_A), 1);

    // when - cancel using batch operation
    final var batchResult =
        client
            .newCreateBatchOperationCommand()
            .processInstanceCancel()
            .filter(f -> f.processInstanceKey(k -> k.in(List.of(processInstanceKey))))
            .send()
            .join();

    final var batchOperationKey = batchResult.getBatchOperationKey();
    waitForBatchOperationCompleted(client, batchOperationKey, 1, 0);

    // when - search audit logs by batch operation type
    final var auditLogItems =
        awaitAuditLogEntryWithFilters(
            adminClient, f -> f.batchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE));

    // then - verify that audit logs are returned
    assertThat(auditLogItems.size()).isEqualTo(1);
    assertThat(auditLogItems)
        .allMatch(
            auditLogResult ->
                auditLogResult.getBatchOperationKey().equals(String.valueOf(batchOperationKey)));
    assertCommonAuditLogFields(
        auditLogItems.getFirst(),
        AuditLogEntityTypeEnum.BATCH,
        AuditLogOperationTypeEnum.CREATE,
        AuditLogCategoryEnum.DEPLOYED_RESOURCES);
  }

  @Test
  void shouldSearchAuditLogsByFormKey(@Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // when - deploy a decision with form
    final var deployment =
        client.newDeployResourceCommand().addResourceFromClasspath("form/form.form").send().join();
    final var formKey = deployment.getForm().getFirst().getFormKey();
    // then - wait for audit log entry and verify
    final var auditLogItems =
        awaitAuditLogEntryWithFilters(client, f -> f.formKey(String.valueOf(formKey)));

    assertThat(auditLogItems.size()).isEqualTo(1);
    assertThat(auditLogItems).isNotEmpty();
    assertCommonAuditLogFields(
        auditLogItems.getFirst(),
        AuditLogEntityTypeEnum.RESOURCE,
        AuditLogOperationTypeEnum.CREATE,
        AuditLogCategoryEnum.DEPLOYED_RESOURCES);
  }

  @Test
  void shouldSearchAuditLogsByResourceKey(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // when - deploy a decision with resource
    final var deployment =
        client
            .newDeployResourceCommand()
            .addResourceFromClasspath("rpa/test-rpa.rpa")
            .tenantId(TENANT_A)
            .send()
            .join();
    final long resourceKey = deployment.getResource().getFirst().getResourceKey();
    // then - wait for audit log entry and verify
    final var auditLogItems =
        awaitAuditLogEntryWithFilters(client, f -> f.resourceKey(String.valueOf(resourceKey)));

    assertThat(auditLogItems.size()).isEqualTo(1);
    assertThat(auditLogItems).isNotEmpty();
    assertCommonAuditLogFields(
        auditLogItems.getFirst(),
        AuditLogEntityTypeEnum.RESOURCE,
        AuditLogOperationTypeEnum.CREATE,
        AuditLogCategoryEnum.DEPLOYED_RESOURCES);
  }

  @Test
  void shouldSearchAuditLogsByDeploymentKey(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // when
    final var deployment = utils.getDeployments().getFirst();
    final var deploymentKey = deployment.getKey();

    // then - wait for audit log entry and verify
    final var auditLogItems =
        awaitAuditLogEntryWithFilters(client, f -> f.deploymentKey(String.valueOf(deploymentKey)));

    assertThat(auditLogItems.size()).isEqualTo(1);
    assertThat(auditLogItems).isNotEmpty();
    assertCommonAuditLogFields(
        auditLogItems.getFirst(),
        AuditLogEntityTypeEnum.RESOURCE,
        AuditLogOperationTypeEnum.CREATE,
        AuditLogCategoryEnum.DEPLOYED_RESOURCES);
  }

  @Test
  void shouldSearchAuditLogWithActorAndProcessDefinitionKey(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // when
    final ProcessInstanceEvent processInstance = utils.getProcessInstances().getFirst();

    // then
    final var auditLogItems =
        awaitAuditLogEntryWithFilters(
            client,
            fn ->
                fn.actorId(DEFAULT_USERNAME)
                    .processDefinitionKey(
                        String.valueOf(processInstance.getProcessDefinitionKey())));

    final var auditLog = auditLogItems.getFirst();
    assertThat(auditLog).isNotNull();
    assertThat(auditLog.getProcessDefinitionKey())
        .isEqualTo(String.valueOf(processInstance.getProcessDefinitionKey()));
    assertCommonAuditLogFields(
        auditLog,
        AuditLogEntityTypeEnum.RESOURCE,
        AuditLogOperationTypeEnum.CREATE,
        AuditLogCategoryEnum.DEPLOYED_RESOURCES);
  }

  @Test
  void shouldSearchAuditLogWithEntityTypeAndResult(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // when
    final var auditLogItems =
        awaitAuditLogEntryWithFilters(
            client,
            fn ->
                fn.entityType(AuditLogEntityTypeEnum.PROCESS_INSTANCE)
                    .result(AuditLogResultEnum.SUCCESS));

    assertThat(auditLogItems)
        .allMatch(
            auditLogResult ->
                auditLogResult.getEntityType() == AuditLogEntityTypeEnum.PROCESS_INSTANCE);
    assertThat(auditLogItems)
        .allMatch(auditLogResult -> auditLogResult.getResult() == AuditLogResultEnum.SUCCESS);
  }

  @Test
  void shouldSearchAuditLogWithTenantAndCategory(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // when
    final var auditLogItems =
        awaitAuditLogEntryWithFilters(
            client, fn -> fn.tenantId(TENANT_A).category(AuditLogCategoryEnum.USER_TASKS));

    assertThat(auditLogItems)
        .allMatch(auditLogResult -> auditLogResult.getTenantId().equals(TENANT_A));
    assertThat(auditLogItems)
        .allMatch(
            auditLogResult -> auditLogResult.getCategory() == AuditLogCategoryEnum.USER_TASKS);
  }

  @Test
  void shouldSearchAuditLogWithTimestampRangeAndDecisionDefinition(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // when
    final var deployment =
        client
            .newDeployResourceCommand()
            .addResourceFromClasspath("decisions/decision_model.dmn")
            .tenantId(TENANT_A)
            .send()
            .join();

    final var decision = deployment.getDecisions().getFirst();

    final var auditLogItems =
        awaitAuditLogEntryWithFilters(
            adminClient,
            filter ->
                filter
                    .timestamp(
                        fn -> fn.lte(OffsetDateTime.now()).gte(OffsetDateTime.now().minusHours(1)))
                    .decisionDefinitionKey(String.valueOf(decision.getDecisionKey())));

    final var auditLog = auditLogItems.getFirst();
    assertThat(auditLog).isNotNull();
    assertThat(auditLog.getDecisionDefinitionKey())
        .isEqualTo(String.valueOf(decision.getDecisionKey()));
    assertThat(
            OffsetDateTime.parse(auditLog.getTimestamp())
                .isAfter(OffsetDateTime.now().minusHours(1)))
        .isTrue();
    assertCommonAuditLogFields(
        auditLog,
        AuditLogEntityTypeEnum.DECISION,
        AuditLogOperationTypeEnum.CREATE,
        AuditLogCategoryEnum.DEPLOYED_RESOURCES);
  }

  @Test
  void shouldSearchAuditLogByUserTaskKeyAndActorId(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given
    final var userTask = utils.getUserTasks().getFirst();
    final long userTaskKey = userTask.getUserTaskKey();

    // when
    final var auditLogItems =
        awaitAuditLogEntryWithFilters(
            client, fn -> fn.userTaskKey(String.valueOf(userTaskKey)).actorId(DEFAULT_USERNAME));

    // then
    assertThat(auditLogItems).isNotEmpty();
    assertThat(auditLogItems)
        .allMatch(
            auditLogResult -> auditLogResult.getUserTaskKey().equals(String.valueOf(userTaskKey)));
    assertThat(auditLogItems)
        .allMatch(auditLogResult -> auditLogResult.getActorId().equals(DEFAULT_USERNAME));
  }

  @Test
  void shouldSearchUserTaskAuditLogByKey(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given
    final long userTask = utils.getUserTasks().getFirst().getUserTaskKey();

    // when
    final var auditLogItems = client.newUserTaskAuditLogSearchRequest(userTask).send().join();

    // then
    final var auditLog = auditLogItems.items().getFirst();
    assertThat(auditLog).isNotNull();
    assertThat(auditLog.getUserTaskKey()).isEqualTo(String.valueOf(userTask));
    assertCommonAuditLogFields(
        auditLog,
        AuditLogEntityTypeEnum.USER_TASK,
        AuditLogOperationTypeEnum.ASSIGN,
        AuditLogCategoryEnum.USER_TASKS);
  }

  @Test
  void shouldSearchUserTaskAuditLogByKeyWithActorId(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given
    final long userTask = utils.getUserTasks().getFirst().getUserTaskKey();

    // when
    final var auditLogItems =
        client
            .newUserTaskAuditLogSearchRequest(userTask)
            .filter(f -> f.actorId(DEFAULT_USERNAME))
            .send()
            .join();

    // then
    final var auditLog = auditLogItems.items().getFirst();
    assertThat(auditLog).isNotNull();
    assertThat(auditLog.getUserTaskKey()).isEqualTo(String.valueOf(userTask));
    assertCommonAuditLogFields(
        auditLog,
        AuditLogEntityTypeEnum.USER_TASK,
        AuditLogOperationTypeEnum.ASSIGN,
        AuditLogCategoryEnum.USER_TASKS);
  }

  // ========================================================================================
  // Helper Methods
  // ========================================================================================

  /**
   * Asserts common audit log fields that are present in all audit logs.
   *
   * @param auditLog the audit log to verify
   * @param entityType the expected entity type
   * @param operationType the expected operation type
   * @param category the expected category
   */
  private void assertCommonAuditLogFields(
      final io.camunda.client.api.search.response.AuditLogResult auditLog,
      final AuditLogEntityTypeEnum entityType,
      final AuditLogOperationTypeEnum operationType,
      final AuditLogCategoryEnum category) {
    assertThat(auditLog.getEntityType()).isEqualTo(entityType);
    assertThat(auditLog.getOperationType()).isEqualTo(operationType);
    assertThat(auditLog.getCategory()).isEqualTo(category);
    assertThat(auditLog.getResult()).isEqualTo(AuditLogResultEnum.SUCCESS);
    assertThat(auditLog.getActorId()).isEqualTo(DEFAULT_USERNAME);
    assertThat(auditLog.getActorType()).isEqualTo(AuditLogActorTypeEnum.USER);
  }

  private List<AuditLogResult> awaitAuditLogEntryWithFilters(
      final CamundaClient client, final Consumer<AuditLogFilter> auditLogFilterConsumer) {
    return Awaitility.await("Audit log entry is created")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .atMost(Duration.ofSeconds(20))
        .until(
            () -> {
              final var auditLogItems =
                  client.newAuditLogSearchRequest().filter(auditLogFilterConsumer).send().join();
              return !auditLogItems.items().isEmpty() ? auditLogItems.items() : null;
            },
            Objects::nonNull);
  }
}
