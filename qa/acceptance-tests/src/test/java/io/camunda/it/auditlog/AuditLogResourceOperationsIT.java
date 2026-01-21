/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auditlog;

import static io.camunda.it.auditlog.AuditLogUtils.DEFAULT_USERNAME;
import static io.camunda.it.auditlog.AuditLogUtils.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.AuditLogActorTypeEnum;
import io.camunda.client.api.search.enums.AuditLogCategoryEnum;
import io.camunda.client.api.search.enums.AuditLogEntityTypeEnum;
import io.camunda.client.api.search.enums.AuditLogOperationTypeEnum;
import io.camunda.client.api.search.enums.AuditLogResultEnum;
import io.camunda.client.api.search.response.AuditLogResult;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * Acceptance tests for audit log entries related to resource operations. This test class verifies
 * that audit log entities for resource-related operations (process, decision, decision
 * requirements, form, and RPA resources) are correctly created and searchable through the audit log
 * search REST API endpoint using the Camunda Client.
 */
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class AuditLogResourceOperationsIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withMultiTenancyEnabled()
          .withAuthorizationsEnabled()
          .withAuthenticatedAccess();

  private static final String PROCESS_ID = "auditLogTestProcess";
  private static final BpmnModelInstance SIMPLE_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("task", t -> t.zeebeJobType("testTask"))
          .endEvent()
          .done();

  private static CamundaClient adminClient;
  private static AuditLogUtils utils;

  @BeforeAll
  static void setup() {
    utils = new AuditLogUtils(adminClient).init();
  }

  // ========================================================================================
  // Process Create/Delete Tests
  // ========================================================================================

  @Test
  void shouldTrackProcessCreate(@Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // when - deploy a process
    final var deployment =
        client
            .newDeployResourceCommand()
            .addProcessModel(SIMPLE_PROCESS, "process-create-test.bpmn")
            .tenantId(TENANT_A)
            .send()
            .join();

    final var processDefinitionKey = deployment.getProcesses().getFirst().getProcessDefinitionKey();

    // then - wait for audit log entry and verify
    final var auditLogItems =
        awaitAuditLogEntry(
            client,
            AuditLogEntityTypeEnum.RESOURCE,
            AuditLogOperationTypeEnum.CREATE,
            String.valueOf(processDefinitionKey));

    assertThat(auditLogItems).isNotEmpty();
    final var auditLog = findByEntityKey(auditLogItems, String.valueOf(processDefinitionKey));
    assertResourceAuditLog(
        auditLog, AuditLogOperationTypeEnum.CREATE, String.valueOf(processDefinitionKey));
  }

  @Test
  void shouldTrackProcessDelete(@Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given - deploy a process
    final var deployment =
        client
            .newDeployResourceCommand()
            .addProcessModel(SIMPLE_PROCESS, "process-delete-test.bpmn")
            .tenantId(TENANT_A)
            .send()
            .join();

    final var processDefinitionKey = deployment.getProcesses().getFirst().getProcessDefinitionKey();

    awaitAuditLogEntry(
        client,
        AuditLogEntityTypeEnum.RESOURCE,
        AuditLogOperationTypeEnum.CREATE,
        String.valueOf(processDefinitionKey));

    // when - delete the process
    client.newDeleteResourceCommand(processDefinitionKey).send().join();

    // then - wait for audit log entry and verify
    final var auditLogItems =
        awaitAuditLogEntry(
            client,
            AuditLogEntityTypeEnum.RESOURCE,
            AuditLogOperationTypeEnum.DELETE,
            String.valueOf(processDefinitionKey));

    assertThat(auditLogItems).isNotEmpty();
    final var auditLog = findByEntityKey(auditLogItems, String.valueOf(processDefinitionKey));
    assertResourceAuditLog(
        auditLog, AuditLogOperationTypeEnum.DELETE, String.valueOf(processDefinitionKey));
  }

  // ========================================================================================
  // Decision Create/Delete Tests
  // ========================================================================================

  @Test
  void shouldTrackDecisionCreate(@Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // when - deploy a decision
    final var deployment =
        client
            .newDeployResourceCommand()
            .addResourceFromClasspath("decisions/decision_model.dmn")
            .tenantId(TENANT_A)
            .send()
            .join();

    final var decisionKey = deployment.getDecisions().getFirst().getDecisionKey();

    // then - wait for audit log entry and verify
    final var auditLogItems =
        awaitAuditLogEntry(
            client,
            AuditLogEntityTypeEnum.DECISION,
            AuditLogOperationTypeEnum.CREATE,
            String.valueOf(decisionKey));

    assertThat(auditLogItems).isNotEmpty();
    final var auditLog = findByEntityKey(auditLogItems, String.valueOf(decisionKey));
    assertDecisionAuditLog(auditLog, AuditLogOperationTypeEnum.CREATE, String.valueOf(decisionKey));
  }

  @Test
  void shouldTrackDecisionDelete(@Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given - deploy a decision
    final var deployment =
        client
            .newDeployResourceCommand()
            .addResourceFromClasspath("decisions/decision_model_1.dmn")
            .tenantId(TENANT_A)
            .send()
            .join();

    final var decisionRequirementsKey =
        deployment.getDecisionRequirements().getFirst().getDecisionRequirementsKey();
    final var decisionKey = deployment.getDecisions().getFirst().getDecisionKey();

    awaitAuditLogEntry(
        client,
        AuditLogEntityTypeEnum.DECISION,
        AuditLogOperationTypeEnum.CREATE,
        String.valueOf(decisionKey));

    // when - delete the decision requirements (which deletes associated decisions)
    client.newDeleteResourceCommand(decisionRequirementsKey).send().join();

    // then - wait for audit log entry and verify
    final var auditLogItems =
        awaitAuditLogEntry(
            client,
            AuditLogEntityTypeEnum.DECISION,
            AuditLogOperationTypeEnum.DELETE,
            String.valueOf(decisionKey));

    assertThat(auditLogItems).isNotEmpty();
    final var auditLog = findByEntityKey(auditLogItems, String.valueOf(decisionKey));
    assertDecisionAuditLog(auditLog, AuditLogOperationTypeEnum.DELETE, String.valueOf(decisionKey));
  }

  // ========================================================================================
  // Decision Requirements Create/Delete Tests
  // ========================================================================================

  @Test
  void shouldTrackDecisionRequirementsCreate(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // when - deploy a decision (also deploys decision requirements)
    final var deployment =
        client
            .newDeployResourceCommand()
            .addResourceFromClasspath("decisions/decision_model.dmn")
            .tenantId(TENANT_A)
            .send()
            .join();

    final var decisionRequirementsKey =
        deployment.getDecisionRequirements().getFirst().getDecisionRequirementsKey();

    // then - wait for audit log entry and verify (decision requirements map to RESOURCE entity)
    final var auditLogItems =
        awaitAuditLogEntry(
            client,
            AuditLogEntityTypeEnum.RESOURCE,
            AuditLogOperationTypeEnum.CREATE,
            String.valueOf(decisionRequirementsKey));

    assertThat(auditLogItems).isNotEmpty();
    final var auditLog = findByEntityKey(auditLogItems, String.valueOf(decisionRequirementsKey));
    assertResourceAuditLog(
        auditLog, AuditLogOperationTypeEnum.CREATE, String.valueOf(decisionRequirementsKey));
  }

  @Test
  void shouldTrackDecisionRequirementsDelete(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given - deploy a decision (also deploys decision requirements)
    final var deployment =
        client
            .newDeployResourceCommand()
            .addResourceFromClasspath("decisions/decision_model_1_v2.dmn")
            .tenantId(TENANT_A)
            .send()
            .join();

    final var decisionRequirementsKey =
        deployment.getDecisionRequirements().getFirst().getDecisionRequirementsKey();

    awaitAuditLogEntry(
        client,
        AuditLogEntityTypeEnum.RESOURCE,
        AuditLogOperationTypeEnum.CREATE,
        String.valueOf(decisionRequirementsKey));

    // when - delete the decision requirements
    client.newDeleteResourceCommand(decisionRequirementsKey).send().join();

    // then - wait for audit log entry and verify (decision requirements map to RESOURCE entity)
    final var auditLogItems =
        awaitAuditLogEntry(
            client,
            AuditLogEntityTypeEnum.RESOURCE,
            AuditLogOperationTypeEnum.DELETE,
            String.valueOf(decisionRequirementsKey));

    assertThat(auditLogItems).isNotEmpty();
    final var auditLog = findByEntityKey(auditLogItems, String.valueOf(decisionRequirementsKey));
    assertResourceAuditLog(
        auditLog, AuditLogOperationTypeEnum.DELETE, String.valueOf(decisionRequirementsKey));
  }

  // ========================================================================================
  // Form Create/Delete Tests
  // ========================================================================================

  @Test
  void shouldTrackFormCreate(@Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // when - deploy a form
    final var deployment =
        client
            .newDeployResourceCommand()
            .addResourceFromClasspath("form/form.form")
            .tenantId(TENANT_A)
            .send()
            .join();

    final var formKey = deployment.getForm().getFirst().getFormKey();

    // then - wait for audit log entry and verify (forms map to RESOURCE entity)
    final var auditLogItems =
        awaitAuditLogEntry(
            client,
            AuditLogEntityTypeEnum.RESOURCE,
            AuditLogOperationTypeEnum.CREATE,
            String.valueOf(formKey));

    assertThat(auditLogItems).isNotEmpty();
    final var auditLog = findByEntityKey(auditLogItems, String.valueOf(formKey));
    assertResourceAuditLog(auditLog, AuditLogOperationTypeEnum.CREATE, String.valueOf(formKey));
  }

  @Test
  void shouldTrackFormDelete(@Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given - deploy a form
    final var deployment =
        client
            .newDeployResourceCommand()
            .addResourceFromClasspath("form/form_v2.form")
            .tenantId(TENANT_A)
            .send()
            .join();

    final var formKey = deployment.getForm().getFirst().getFormKey();

    awaitAuditLogEntry(
        client,
        AuditLogEntityTypeEnum.RESOURCE,
        AuditLogOperationTypeEnum.CREATE,
        String.valueOf(formKey));

    // when - delete the form
    client.newDeleteResourceCommand(formKey).send().join();

    // then - wait for audit log entry and verify (forms map to RESOURCE entity)
    final var auditLogItems =
        awaitAuditLogEntry(
            client,
            AuditLogEntityTypeEnum.RESOURCE,
            AuditLogOperationTypeEnum.DELETE,
            String.valueOf(formKey));

    assertThat(auditLogItems).isNotEmpty();
    final var auditLog = findByEntityKey(auditLogItems, String.valueOf(formKey));
    assertResourceAuditLog(auditLog, AuditLogOperationTypeEnum.DELETE, String.valueOf(formKey));
  }

  // ========================================================================================
  // Helper Methods
  // ========================================================================================

  /**
   * Asserts common audit log fields that are present in all resource audit logs.
   *
   * @param auditLog the audit log to verify
   * @param entityType the expected entity type
   * @param operationType the expected operation type
   */
  private void assertCommonAuditLogFields(
      final AuditLogResult auditLog,
      final AuditLogEntityTypeEnum entityType,
      final AuditLogOperationTypeEnum operationType) {
    assertThat(auditLog.getEntityType()).isEqualTo(entityType);
    assertThat(auditLog.getOperationType()).isEqualTo(operationType);
    assertThat(auditLog.getResult()).isEqualTo(AuditLogResultEnum.SUCCESS);
    assertThat(auditLog.getTenantId()).isEqualTo(TENANT_A);
    assertThat(auditLog.getCategory()).isEqualTo(AuditLogCategoryEnum.DEPLOYED_RESOURCES);
    assertThat(auditLog.getActorId()).isEqualTo(DEFAULT_USERNAME);
    assertThat(auditLog.getActorType()).isEqualTo(AuditLogActorTypeEnum.USER);
    assertThat(auditLog.getTimestamp()).isNotNull();
    assertThat(auditLog.getAuditLogKey()).isNotNull();
  }

  /**
   * Asserts audit log fields for resource operations (process, decision requirements, forms).
   *
   * @param auditLog the audit log to verify
   * @param operationType the expected operation type (CREATE or DELETE)
   * @param resourceKey the expected resource key
   */
  private void assertResourceAuditLog(
      final AuditLogResult auditLog,
      final AuditLogOperationTypeEnum operationType,
      final String resourceKey) {
    assertCommonAuditLogFields(auditLog, AuditLogEntityTypeEnum.RESOURCE, operationType);
    assertThat(auditLog.getEntityKey()).isEqualTo(resourceKey);
  }

  /**
   * Asserts audit log fields for decision operations.
   *
   * @param auditLog the audit log to verify
   * @param operationType the expected operation type (CREATE or DELETE)
   * @param decisionKey the expected decision key
   */
  private void assertDecisionAuditLog(
      final AuditLogResult auditLog,
      final AuditLogOperationTypeEnum operationType,
      final String decisionKey) {
    assertCommonAuditLogFields(auditLog, AuditLogEntityTypeEnum.DECISION, operationType);
    assertThat(auditLog.getEntityKey()).isEqualTo(decisionKey);
  }

  private List<AuditLogResult> awaitAuditLogEntry(
      final CamundaClient client,
      final AuditLogEntityTypeEnum entityType,
      final AuditLogOperationTypeEnum operationType,
      final String entityKey) {
    return Awaitility.await("Audit log entry is created")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .atMost(Duration.ofSeconds(20))
        .until(
            () -> {
              final var auditLogItems =
                  client
                      .newAuditLogSearchRequest()
                      .filter(f -> f.entityType(entityType).operationType(operationType))
                      .send()
                      .join();
              if (entityKey != null) {
                final var filteredItems =
                    auditLogItems.items().stream()
                        .filter(al -> entityKey.equals(al.getEntityKey()))
                        .toList();
                return !filteredItems.isEmpty() ? filteredItems : null;
              } else {
                return !auditLogItems.items().isEmpty() ? auditLogItems.items() : null;
              }
            },
            Objects::nonNull);
  }

  private AuditLogResult findByEntityKey(
      final List<AuditLogResult> auditLogs, final String entityKey) {
    return auditLogs.stream()
        .filter(al -> entityKey.equals(al.getEntityKey()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No audit log found with entity key: " + entityKey));
  }
}
