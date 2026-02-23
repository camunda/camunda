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
import static io.camunda.it.util.TestHelper.waitForBatchOperationCompleted;
import static io.camunda.it.util.TestHelper.waitForBatchOperationWithCorrectTotalCount;
import static io.camunda.it.util.TestHelper.waitForDecisionsToBeDeployed;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static io.camunda.it.util.TestHelper.waitUntilIncidentsAreResolved;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.MigrationPlan;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.AuditLogActorTypeEnum;
import io.camunda.client.api.search.enums.AuditLogEntityTypeEnum;
import io.camunda.client.api.search.enums.AuditLogOperationTypeEnum;
import io.camunda.client.api.search.enums.AuditLogResultEnum;
import io.camunda.client.api.search.enums.IncidentState;
import io.camunda.client.api.search.filter.AuditLogFilter;
import io.camunda.client.api.search.response.AuditLogResult;
import io.camunda.client.api.search.response.Incident;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * Acceptance tests for audit log entries related to process operations. This test class verifies
 * that audit log entities for process-related operations are correctly created and searchable
 * through the audit log search REST API endpoint using the Camunda Client.
 */
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class AuditLogProcessOperationsIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withMultiTenancyEnabled()
          .withAuthorizationsEnabled()
          .withAuthenticatedAccess();

  private static final String PROCESS_V1_ID = "migration-process_v1";
  private static final String PROCESS_V2_ID = "migration-process_v2";
  private static final String INCIDENT_PROCESS_ID = "incident_process_v1";
  private static final String SERVICE_TASKS_PROCESS_ID = "service_tasks_v1";
  private static final String DECISION_ID = "decision_1";
  private static CamundaClient adminClient;
  private static AuditLogUtils utils;

  // Pre-created process instance for tests that only read/add variables
  private static long sharedProcessInstanceKey;
  private static long sharedProcessDefinitionKey;

  @BeforeAll
  static void setup() {
    utils = new AuditLogUtils(adminClient).init();

    final var deploymentFutures =
        java.util.stream.Stream.of(
                "process/migration-process_v1.bpmn",
                "process/migration-process_v2.bpmn",
                "process/service_tasks_v1.bpmn",
                "process/incident_process_v1.bpmn",
                "decisions/decision_model.dmn")
            .map(
                resource ->
                    adminClient
                        .newDeployResourceCommand()
                        .addResourceFromClasspath(resource)
                        .tenantId(TENANT_A)
                        .send())
            .toList();

    deploymentFutures.forEach(CamundaFuture::join);

    waitForProcessesToBeDeployed(adminClient, 4);
    waitForDecisionsToBeDeployed(adminClient, 1, 1);

    final var sharedInstance =
        adminClient
            .newCreateInstanceCommand()
            .bpmnProcessId(SERVICE_TASKS_PROCESS_ID)
            .latestVersion()
            .tenantId(TENANT_A)
            .send()
            .join();
    sharedProcessInstanceKey = sharedInstance.getProcessInstanceKey();
    sharedProcessDefinitionKey = sharedInstance.getProcessDefinitionKey();

    waitForProcessInstancesToStart(
        adminClient, f -> f.processInstanceKey(sharedProcessInstanceKey).tenantId(TENANT_A), 1);
  }

  // ========================================================================================
  // Process Instance Tests
  // ========================================================================================

  @Test
  void shouldTrackProcessInstanceCreation(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // when - create a process instance
    final var processInstance = createProcessInstance(client, SERVICE_TASKS_PROCESS_ID);
    final var processInstanceKey = processInstance.getProcessInstanceKey();

    waitForProcessInstancesToStart(
        client, f -> f.processInstanceKey(processInstanceKey).tenantId(TENANT_A), 1);

    // then - wait for audit log entry and verify
    final var auditLogItems =
        awaitAuditLogEntry(
            client,
            AuditLogEntityTypeEnum.PROCESS_INSTANCE,
            AuditLogOperationTypeEnum.CREATE,
            String.valueOf(processInstanceKey));

    assertThat(auditLogItems).isNotEmpty();
    final var auditLog = auditLogItems.stream().findFirst().orElseThrow();
    assertProcessInstanceAuditLog(
        auditLog,
        AuditLogEntityTypeEnum.PROCESS_INSTANCE,
        AuditLogOperationTypeEnum.CREATE,
        processInstanceKey,
        processInstance.getProcessDefinitionKey(),
        SERVICE_TASKS_PROCESS_ID);
  }

  @Test
  void shouldTrackProcessInstanceMigration(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given - start a process instance
    final var processInstance = createProcessInstance(client, PROCESS_V1_ID);
    final var processInstanceKey = processInstance.getProcessInstanceKey();

    waitForProcessInstancesToStart(
        client, f -> f.processInstanceKey(processInstanceKey).tenantId(TENANT_A), 1);

    final var targetProcess =
        client
            .newProcessDefinitionSearchRequest()
            .filter(f -> f.processDefinitionId(PROCESS_V2_ID))
            .send()
            .join()
            .items()
            .getFirst();

    // when - migrate the process instance
    client
        .newMigrateProcessInstanceCommand(processInstanceKey)
        .migrationPlan(
            MigrationPlan.newBuilder()
                .withTargetProcessDefinitionKey(targetProcess.getProcessDefinitionKey())
                .addMappingInstruction("taskA", "taskA2")
                .addMappingInstruction("taskB", "taskB2")
                .addMappingInstruction("taskC", "taskC2")
                .build())
        .send()
        .join();

    // then - wait for audit log entry and verify
    final var auditLogItems =
        awaitAuditLogEntry(
            client,
            AuditLogEntityTypeEnum.PROCESS_INSTANCE,
            AuditLogOperationTypeEnum.MIGRATE,
            String.valueOf(processInstanceKey));

    assertThat(auditLogItems).isNotEmpty();
    final var auditLog = auditLogItems.stream().findFirst().orElseThrow();
    assertProcessInstanceAuditLog(
        auditLog,
        AuditLogEntityTypeEnum.PROCESS_INSTANCE,
        AuditLogOperationTypeEnum.MIGRATE,
        processInstanceKey,
        targetProcess.getProcessDefinitionKey(),
        PROCESS_V1_ID);
  }

  @Test
  void shouldTrackProcessInstanceModification(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given - start a process instance
    final var processInstance = createProcessInstance(client, SERVICE_TASKS_PROCESS_ID);
    final var processInstanceKey = processInstance.getProcessInstanceKey();

    waitForProcessInstancesToStart(
        client, f -> f.processInstanceKey(processInstanceKey).tenantId(TENANT_A), 1);

    // when - modify the process instance
    client
        .newModifyProcessInstanceCommand(processInstanceKey)
        .activateElement("taskB")
        .send()
        .join();

    // then - wait for audit log entry and verify
    final var auditLogItems =
        awaitAuditLogEntry(
            client,
            AuditLogEntityTypeEnum.PROCESS_INSTANCE,
            AuditLogOperationTypeEnum.MODIFY,
            String.valueOf(processInstanceKey));

    assertThat(auditLogItems).isNotEmpty();
    final var auditLog = auditLogItems.stream().findFirst().orElseThrow();
    assertProcessInstanceAuditLog(
        auditLog,
        AuditLogEntityTypeEnum.PROCESS_INSTANCE,
        AuditLogOperationTypeEnum.MODIFY,
        processInstanceKey,
        processInstance.getProcessDefinitionKey(),
        SERVICE_TASKS_PROCESS_ID);
  }

  @Test
  void shouldTrackProcessInstanceCancellation(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given - start a process instance
    final var processInstance = createProcessInstance(client, SERVICE_TASKS_PROCESS_ID);
    final var processInstanceKey = processInstance.getProcessInstanceKey();

    waitForProcessInstancesToStart(
        client, f -> f.processInstanceKey(processInstanceKey).tenantId(TENANT_A), 1);

    // when - cancel the process instance
    client.newCancelInstanceCommand(processInstanceKey).send().join();

    // then - wait for audit log entry and verify
    final var auditLogItems =
        awaitAuditLogEntry(
            client,
            AuditLogEntityTypeEnum.PROCESS_INSTANCE,
            AuditLogOperationTypeEnum.CANCEL,
            String.valueOf(processInstanceKey));

    assertThat(auditLogItems).isNotEmpty();
    final var auditLog = auditLogItems.stream().findFirst().orElseThrow();
    assertProcessInstanceAuditLog(
        auditLog,
        AuditLogEntityTypeEnum.PROCESS_INSTANCE,
        AuditLogOperationTypeEnum.CANCEL,
        processInstanceKey,
        processInstance.getProcessDefinitionKey(),
        SERVICE_TASKS_PROCESS_ID);
  }

  // ========================================================================================
  // Variable Tests
  // ========================================================================================

  @Test
  void shouldTrackVariableCreate(@Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given - use the pre-created shared process instance
    final var processInstanceKey = sharedProcessInstanceKey;

    // when - add a variable
    client
        .newSetVariablesCommand(processInstanceKey)
        .variables(Map.of("newVar", "newValue"))
        .send()
        .join();

    // then - verify audit log entry
    final var auditLogItems =
        awaitAuditLogEntryWithFilters(
            client,
            fn ->
                fn.processInstanceKey(String.valueOf(processInstanceKey))
                    .entityDescription("newVar"));

    assertThat(auditLogItems.size()).isEqualTo(1);
    final var auditLog = auditLogItems.getFirst();
    assertVariableAuditLog(
        auditLog,
        AuditLogOperationTypeEnum.CREATE,
        processInstanceKey,
        sharedProcessDefinitionKey,
        SERVICE_TASKS_PROCESS_ID,
        "newVar");
  }

  @Test
  void shouldTrackVariableUpdate(@Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given - start a process instance and add a variable

    final ProcessInstanceEvent processInstance =
        createProcessInstance(
            client, SERVICE_TASKS_PROCESS_ID, Map.of("existingVar", "initialValue"));
    final long processInstanceKey = processInstance.getProcessInstanceKey();

    waitForProcessInstancesToStart(
        client, f -> f.processInstanceKey(processInstanceKey).tenantId(TENANT_A), 1);

    // when - update the variable
    client
        .newSetVariablesCommand(processInstanceKey)
        .variables(Map.of("existingVar", "updatedValue"))
        .send()
        .join();

    // then - wait for audit log entry and verify
    final var auditLogItems =
        awaitAuditLogEntry(
            client,
            AuditLogEntityTypeEnum.VARIABLE,
            AuditLogOperationTypeEnum.UPDATE,
            String.valueOf(processInstanceKey));

    assertThat(auditLogItems).isNotEmpty();
    final var auditLog = auditLogItems.getFirst();
    assertVariableAuditLog(
        auditLog,
        AuditLogOperationTypeEnum.UPDATE,
        processInstanceKey,
        processInstance.getProcessDefinitionKey(),
        SERVICE_TASKS_PROCESS_ID,
        "existingVar");
  }

  // ========================================================================================
  // Incident Tests
  // ========================================================================================

  @Test
  void shouldTrackIncidentResolve(@Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given - start a process that creates an incident
    final var processInstance = createProcessInstance(client, INCIDENT_PROCESS_ID);
    final var processInstanceKey = processInstance.getProcessInstanceKey();

    waitForProcessInstancesToStart(
        client, f -> f.processInstanceKey(processInstanceKey).tenantId(TENANT_A), 1);

    Awaitility.await("should have active incident for this process instance")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var incidents =
                  client
                      .newIncidentSearchRequest()
                      .filter(
                          f -> f.processInstanceKey(processInstanceKey).state(IncidentState.ACTIVE))
                      .send()
                      .join();
              assertThat(incidents.items()).hasSize(1);
            });

    // Get the incident key
    final var incident =
        client
            .newIncidentSearchRequest()
            .filter(f -> f.processInstanceKey(processInstanceKey).state(IncidentState.ACTIVE))
            .send()
            .join()
            .items()
            .getFirst();

    // when - resolve the incident
    client.newResolveIncidentCommand(incident.getIncidentKey()).send().join();

    // then - wait for audit log entry and verify
    final var auditLogItems =
        awaitAuditLogEntry(
            client,
            AuditLogEntityTypeEnum.INCIDENT,
            AuditLogOperationTypeEnum.RESOLVE,
            String.valueOf(incident.getIncidentKey()));

    assertThat(auditLogItems).isNotEmpty();
    final var auditLog = auditLogItems.stream().findFirst().orElseThrow();
    assertIncidentAuditLog(
        auditLog,
        AuditLogOperationTypeEnum.RESOLVE,
        incident.getIncidentKey(),
        processInstanceKey,
        processInstance.getProcessDefinitionKey(),
        INCIDENT_PROCESS_ID);
  }

  // ========================================================================================
  // Decision Evaluation Tests
  // ========================================================================================

  @Test
  void shouldTrackStandaloneDecisionEvaluation(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // when - evaluate a decision
    client
        .newEvaluateDecisionCommand()
        .decisionId(DECISION_ID)
        .tenantId(TENANT_A)
        .variables(Map.of("age", 25, "income", 30000))
        .send()
        .join();

    // then - wait for audit log entry and verify
    final var auditLogItems =
        awaitAuditLogEntry(
            client, AuditLogEntityTypeEnum.DECISION, AuditLogOperationTypeEnum.EVALUATE, null);

    assertThat(auditLogItems).isNotEmpty();
    final var auditLog = auditLogItems.getFirst();
    assertDecisionAuditLog(auditLog, AuditLogOperationTypeEnum.EVALUATE);
  }

  // ========================================================================================
  // Batch Operation Tests
  // ========================================================================================

  @Test
  void shouldTrackBatchProcessInstanceCancel(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given - start multiple process instances in parallel
    final var instance1 =
        createProcessInstance(
            client, SERVICE_TASKS_PROCESS_ID, Map.of("batchTestCancel", "value1"));
    final var instance2 =
        createProcessInstance(
            client, SERVICE_TASKS_PROCESS_ID, Map.of("batchTestCancel", "value2"));
    final long instance1Key = instance1.getProcessInstanceKey();
    final long instance2Key = instance2.getProcessInstanceKey();

    waitForProcessInstancesToStart(
        client, f -> f.processInstanceKey(instance1Key).tenantId(TENANT_A), 1);
    waitForProcessInstancesToStart(
        client, f -> f.processInstanceKey(instance2Key).tenantId(TENANT_A), 1);

    // when - cancel using batch operation
    final var batchResult =
        client
            .newCreateBatchOperationCommand()
            .processInstanceCancel()
            .filter(f -> f.processInstanceKey(k -> k.in(List.of(instance1Key, instance2Key))))
            .send()
            .join();

    final var batchOperationKey = batchResult.getBatchOperationKey();
    waitForBatchOperationWithCorrectTotalCount(client, batchOperationKey, 2);
    waitForBatchOperationCompleted(client, batchOperationKey, 2, 0);

    // then - wait for audit log entry for batch operation
    final var auditLogItems =
        awaitAuditLogEntry(
            client,
            AuditLogEntityTypeEnum.BATCH,
            AuditLogOperationTypeEnum.CREATE,
            batchOperationKey);

    assertThat(auditLogItems).isNotEmpty();
    final var auditLog = auditLogItems.stream().findFirst().orElseThrow();
    assertBatchAuditLog(auditLog, AuditLogOperationTypeEnum.CREATE, batchOperationKey);
  }

  @Test
  void shouldTrackBatchProcessInstanceMigrate(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given - start a process instance
    final var processInstance =
        createProcessInstance(client, PROCESS_V1_ID, Map.of("batchTestMigrate", "value"));
    final var processInstanceKey = processInstance.getProcessInstanceKey();

    waitForProcessInstancesToStart(
        client, f -> f.processInstanceKey(processInstanceKey).tenantId(TENANT_A), 1);

    final var targetProcess =
        client
            .newProcessDefinitionSearchRequest()
            .filter(f -> f.processDefinitionId(PROCESS_V2_ID))
            .send()
            .join()
            .items()
            .getFirst();

    // when - migrate using batch operation
    final var batchResult =
        client
            .newCreateBatchOperationCommand()
            .migrateProcessInstance()
            .migrationPlan(
                MigrationPlan.newBuilder()
                    .withTargetProcessDefinitionKey(targetProcess.getProcessDefinitionKey())
                    .addMappingInstruction("taskA", "taskA2")
                    .addMappingInstruction("taskB", "taskB2")
                    .addMappingInstruction("taskC", "taskC2")
                    .build())
            .filter(f -> f.processInstanceKey(processInstanceKey))
            .send()
            .join();

    final var batchOperationKey = batchResult.getBatchOperationKey();
    waitForBatchOperationWithCorrectTotalCount(client, batchOperationKey, 1);
    waitForBatchOperationCompleted(client, batchOperationKey, 1, 0);

    // then - verify batch audit log
    final var batchAuditLogs =
        awaitAuditLogEntry(
            client,
            AuditLogEntityTypeEnum.BATCH,
            AuditLogOperationTypeEnum.CREATE,
            batchOperationKey);

    assertThat(batchAuditLogs).isNotEmpty();
    final var batchAuditLog = batchAuditLogs.stream().findFirst().orElseThrow();
    assertBatchAuditLog(batchAuditLog, AuditLogOperationTypeEnum.CREATE, batchOperationKey);
  }

  @Test
  void shouldTrackBatchIncidentResolve(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given - start a process instance that creates an incident
    final var instance1 = createProcessInstance(client, INCIDENT_PROCESS_ID);
    final var instance1Key = instance1.getProcessInstanceKey();

    waitForProcessInstancesToStart(
        client, f -> f.processInstanceKey(instance1Key).tenantId(TENANT_A), 1);

    Awaitility.await("should have active incident for this process instance")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var incidents =
                  client
                      .newIncidentSearchRequest()
                      .filter(f -> f.processInstanceKey(instance1Key).state(IncidentState.ACTIVE))
                      .send()
                      .join();
              assertThat(incidents.items()).hasSize(1);
            });

    final var incidentKeys =
        client
            .newIncidentSearchRequest()
            .filter(f -> f.processInstanceKey(instance1Key).state(IncidentState.ACTIVE))
            .send()
            .join()
            .items()
            .stream()
            .map(Incident::getIncidentKey)
            .toList();

    // when - resolve incidents using batch operation
    final var batchResult =
        client
            .newCreateBatchOperationCommand()
            .resolveIncident()
            .filter(f -> f.processInstanceKey(instance1Key))
            .send()
            .join();

    final var batchOperationKey = batchResult.getBatchOperationKey();
    waitForBatchOperationWithCorrectTotalCount(client, batchOperationKey, incidentKeys.size());
    waitForBatchOperationCompleted(client, batchOperationKey, incidentKeys.size(), 0);
    waitUntilIncidentsAreResolved(client, incidentKeys);

    // then - verify batch audit log
    final var batchAuditLogs =
        awaitAuditLogEntry(
            client,
            AuditLogEntityTypeEnum.BATCH,
            AuditLogOperationTypeEnum.CREATE,
            batchOperationKey);

    assertThat(batchAuditLogs).isNotEmpty();
    final var batchAuditLog = batchAuditLogs.stream().findFirst().orElseThrow();
    assertBatchAuditLog(batchAuditLog, AuditLogOperationTypeEnum.CREATE, batchOperationKey);
  }

  @Test
  void shouldTrackBatchProcessInstanceModify(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given - start a process instance that will have an active element to move
    final var processInstance =
        createProcessInstance(client, PROCESS_V1_ID, Map.of("batchTestModify", "value"));
    final var processInstanceKey = processInstance.getProcessInstanceKey();

    waitForProcessInstancesToStart(
        client, f -> f.processInstanceKey(processInstanceKey).tenantId(TENANT_A), 1);

    // when - modify using batch operation (move from taskB to taskC)
    final var batchResult =
        client
            .newCreateBatchOperationCommand()
            .modifyProcessInstance()
            .addMoveInstruction("taskB", "taskC")
            .filter(f -> f.processInstanceKey(processInstanceKey))
            .send()
            .join();

    final var batchOperationKey = batchResult.getBatchOperationKey();
    waitForBatchOperationWithCorrectTotalCount(client, batchOperationKey, 1);
    waitForBatchOperationCompleted(client, batchOperationKey, 1, 0);

    // then - verify batch audit log
    final var batchAuditLogs =
        awaitAuditLogEntry(
            client,
            AuditLogEntityTypeEnum.BATCH,
            AuditLogOperationTypeEnum.CREATE,
            batchOperationKey);

    assertThat(batchAuditLogs).isNotEmpty();
    final var batchAuditLog = batchAuditLogs.stream().findFirst().orElseThrow();
    assertBatchAuditLog(batchAuditLog, AuditLogOperationTypeEnum.CREATE, batchOperationKey);
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
   */
  private void assertCommonAuditLogFields(
      final AuditLogResult auditLog,
      final AuditLogEntityTypeEnum entityType,
      final AuditLogOperationTypeEnum operationType) {
    assertThat(auditLog.getEntityType()).isEqualTo(entityType);
    assertThat(auditLog.getOperationType()).isEqualTo(operationType);
    assertThat(auditLog.getResult()).isEqualTo(AuditLogResultEnum.SUCCESS);
    assertThat(auditLog.getActorId()).isEqualTo(DEFAULT_USERNAME);
    assertThat(auditLog.getActorType()).isEqualTo(AuditLogActorTypeEnum.USER);
    assertThat(auditLog.getTimestamp()).isNotNull();
    assertThat(auditLog.getAuditLogKey()).isNotNull();
  }

  /**
   * Asserts common audit log fields for process instance operations.
   *
   * @param auditLog the audit log to verify
   * @param entityType the expected entity type
   * @param operationType the expected operation type
   * @param processInstanceKey the expected process instance key
   * @param processDefinitionKey the expected process definition key
   * @param processDefinitionId the expected process definition ID (can be null)
   */
  private void assertProcessInstanceAuditLog(
      final AuditLogResult auditLog,
      final AuditLogEntityTypeEnum entityType,
      final AuditLogOperationTypeEnum operationType,
      final long processInstanceKey,
      final long processDefinitionKey,
      final String processDefinitionId) {
    assertCommonAuditLogFields(auditLog, entityType, operationType);
    assertThat(auditLog.getEntityKey()).isNotNull();
    assertThat(auditLog.getProcessInstanceKey()).isEqualTo(String.valueOf(processInstanceKey));
    assertThat(auditLog.getProcessDefinitionKey()).isEqualTo(String.valueOf(processDefinitionKey));
    assertThat(auditLog.getTenantId()).isEqualTo(TENANT_A);
    assertThat(auditLog.getProcessDefinitionId()).isEqualTo(processDefinitionId);
  }

  /**
   * Asserts common audit log fields for variable operations.
   *
   * @param auditLog the audit log to verify
   * @param operationType the expected operation type
   * @param processInstanceKey the expected process instance key
   * @param processDefinitionKey the expected process definition key
   * @param processDefinitionId the expected process definition ID
   */
  private void assertVariableAuditLog(
      final AuditLogResult auditLog,
      final AuditLogOperationTypeEnum operationType,
      final long processInstanceKey,
      final long processDefinitionKey,
      final String processDefinitionId,
      final String variableName) {
    assertCommonAuditLogFields(auditLog, AuditLogEntityTypeEnum.VARIABLE, operationType);
    assertThat(auditLog.getEntityKey()).isNotNull();
    assertThat(auditLog.getProcessInstanceKey()).isEqualTo(String.valueOf(processInstanceKey));
    assertThat(auditLog.getProcessDefinitionKey()).isEqualTo(String.valueOf(processDefinitionKey));
    assertThat(auditLog.getTenantId()).isEqualTo(TENANT_A);
    assertThat(auditLog.getProcessDefinitionId()).isEqualTo(processDefinitionId);
    assertThat(auditLog.getEntityDescription()).isEqualTo(variableName);
  }

  /**
   * Asserts common audit log fields for incident operations.
   *
   * @param auditLog the audit log to verify
   * @param operationType the expected operation type
   * @param incidentKey the expected incident key
   * @param processInstanceKey the expected process instance key
   * @param processDefinitionKey the expected process definition key
   * @param processDefinitionId the expected process definition ID
   */
  private void assertIncidentAuditLog(
      final AuditLogResult auditLog,
      final AuditLogOperationTypeEnum operationType,
      final long incidentKey,
      final long processInstanceKey,
      final long processDefinitionKey,
      final String processDefinitionId) {
    assertCommonAuditLogFields(auditLog, AuditLogEntityTypeEnum.INCIDENT, operationType);
    assertThat(auditLog.getEntityKey()).isEqualTo(String.valueOf(incidentKey));
    assertThat(auditLog.getProcessInstanceKey()).isEqualTo(String.valueOf(processInstanceKey));
    assertThat(auditLog.getProcessDefinitionKey()).isEqualTo(String.valueOf(processDefinitionKey));
    assertThat(auditLog.getTenantId()).isEqualTo(TENANT_A);
    assertThat(auditLog.getProcessDefinitionId()).isEqualTo(processDefinitionId);
  }

  /**
   * Asserts common audit log fields for decision evaluation operations.
   *
   * @param auditLog the audit log to verify
   * @param operationType the expected operation type
   */
  private void assertDecisionAuditLog(
      final AuditLogResult auditLog, final AuditLogOperationTypeEnum operationType) {
    assertCommonAuditLogFields(auditLog, AuditLogEntityTypeEnum.DECISION, operationType);
    assertThat(auditLog.getEntityKey()).isNotNull();
    assertThat(auditLog.getTenantId()).isEqualTo(TENANT_A);
  }

  /**
   * Asserts common audit log fields for batch operations.
   *
   * @param auditLog the audit log to verify
   * @param operationType the expected operation type
   * @param batchOperationKey the expected batch operation key
   */
  private void assertBatchAuditLog(
      final AuditLogResult auditLog,
      final AuditLogOperationTypeEnum operationType,
      final String batchOperationKey) {
    assertCommonAuditLogFields(auditLog, AuditLogEntityTypeEnum.BATCH, operationType);
    assertThat(auditLog.getEntityKey()).isEqualTo(batchOperationKey);
  }

  /**
   * Helper method to create a process instance with the given parameters.
   *
   * @param client the Camunda client to use
   * @param bpmnProcessId the BPMN process ID
   * @return the created process instance key
   */
  private ProcessInstanceEvent createProcessInstance(
      final CamundaClient client, final String bpmnProcessId) {
    return createProcessInstance(client, bpmnProcessId, Map.of());
  }

  /**
   * Helper method to create a process instance with the given parameters and variables.
   *
   * @param client the Camunda client to use
   * @param bpmnProcessId the BPMN process ID
   * @param variables the process variables
   * @return the created process instance key
   */
  private ProcessInstanceEvent createProcessInstance(
      final CamundaClient client, final String bpmnProcessId, final Map<String, Object> variables) {
    return client
        .newCreateInstanceCommand()
        .bpmnProcessId(bpmnProcessId)
        .latestVersion()
        .tenantId(AuditLogUtils.TENANT_A)
        .variables(variables)
        .send()
        .join();
  }

  private List<AuditLogResult> awaitAuditLogEntry(
      final CamundaClient client,
      final AuditLogEntityTypeEnum entityType,
      final AuditLogOperationTypeEnum operationType,
      final String entityKeyOrProcessInstanceKey) {
    return Awaitility.await("Audit log entry is created")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .atMost(Duration.ofSeconds(15))
        .until(
            () -> {
              final var auditLogItems =
                  client
                      .newAuditLogSearchRequest()
                      .filter(f -> f.entityType(entityType).operationType(operationType))
                      .send()
                      .join();
              if (entityKeyOrProcessInstanceKey != null) {
                final var filteredItems =
                    auditLogItems.items().stream()
                        .filter(
                            al ->
                                entityKeyOrProcessInstanceKey.equals(al.getEntityKey())
                                    || entityKeyOrProcessInstanceKey.equals(
                                        al.getProcessInstanceKey()))
                        .toList();
                return !filteredItems.isEmpty() ? filteredItems : null;
              } else {
                return !auditLogItems.items().isEmpty() ? auditLogItems.items() : null;
              }
            },
            Objects::nonNull);
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
