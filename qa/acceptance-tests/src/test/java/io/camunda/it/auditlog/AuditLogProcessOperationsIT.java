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
    assertThat(auditLog.getEntityType()).isEqualTo(AuditLogEntityTypeEnum.PROCESS_INSTANCE);
    assertThat(auditLog.getOperationType()).isEqualTo(AuditLogOperationTypeEnum.CREATE);
    assertThat(auditLog.getEntityKey()).isNotNull();
    assertThat(auditLog.getProcessInstanceKey()).isEqualTo(String.valueOf(processInstanceKey));
    assertThat(auditLog.getResult()).isEqualTo(AuditLogResultEnum.SUCCESS);
    assertThat(auditLog.getTenantId()).isEqualTo(TENANT_A);
    assertThat(auditLog.getProcessDefinitionId()).isEqualTo(SERVICE_TASKS_PROCESS_ID);
    assertThat(auditLog.getActorId()).isEqualTo(DEFAULT_USERNAME);
    assertThat(auditLog.getActorType()).isEqualTo(AuditLogActorTypeEnum.USER);
    assertThat(auditLog.getProcessDefinitionKey())
        .isEqualTo(String.valueOf(processInstance.getProcessDefinitionKey()));
    assertThat(auditLog.getTimestamp()).isNotNull();
    assertThat(auditLog.getAuditLogKey()).isNotNull();
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
    assertThat(auditLog.getEntityType()).isEqualTo(AuditLogEntityTypeEnum.PROCESS_INSTANCE);
    assertThat(auditLog.getOperationType()).isEqualTo(AuditLogOperationTypeEnum.MIGRATE);
    assertThat(auditLog.getEntityKey()).isNotNull();
    assertThat(auditLog.getProcessInstanceKey()).isEqualTo(String.valueOf(processInstanceKey));
    assertThat(auditLog.getProcessDefinitionKey())
        .isEqualTo(String.valueOf(targetProcess.getProcessDefinitionKey()));
    assertThat(auditLog.getResult()).isEqualTo(AuditLogResultEnum.SUCCESS);
    assertThat(auditLog.getTenantId()).isEqualTo(TENANT_A);
    assertThat(auditLog.getActorId()).isEqualTo(DEFAULT_USERNAME);
    assertThat(auditLog.getActorType()).isEqualTo(AuditLogActorTypeEnum.USER);
    assertThat(auditLog.getTimestamp()).isNotNull();
    assertThat(auditLog.getAuditLogKey()).isNotNull();
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
    assertThat(auditLog.getEntityType()).isEqualTo(AuditLogEntityTypeEnum.PROCESS_INSTANCE);
    assertThat(auditLog.getOperationType()).isEqualTo(AuditLogOperationTypeEnum.MODIFY);
    assertThat(auditLog.getEntityKey()).isNotNull();
    assertThat(auditLog.getProcessInstanceKey()).isEqualTo(String.valueOf(processInstanceKey));
    assertThat(auditLog.getProcessDefinitionKey())
        .isEqualTo(String.valueOf(processInstance.getProcessDefinitionKey()));
    assertThat(auditLog.getResult()).isEqualTo(AuditLogResultEnum.SUCCESS);
    assertThat(auditLog.getTenantId()).isEqualTo(TENANT_A);
    assertThat(auditLog.getActorId()).isEqualTo(DEFAULT_USERNAME);
    assertThat(auditLog.getActorType()).isEqualTo(AuditLogActorTypeEnum.USER);
    assertThat(auditLog.getTimestamp()).isNotNull();
    assertThat(auditLog.getAuditLogKey()).isNotNull();
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
    assertThat(auditLog.getEntityType()).isEqualTo(AuditLogEntityTypeEnum.PROCESS_INSTANCE);
    assertThat(auditLog.getOperationType()).isEqualTo(AuditLogOperationTypeEnum.CANCEL);
    assertThat(auditLog.getEntityKey()).isNotNull();
    assertThat(auditLog.getProcessInstanceKey()).isEqualTo(String.valueOf(processInstanceKey));
    assertThat(auditLog.getResult()).isEqualTo(AuditLogResultEnum.SUCCESS);
    assertThat(auditLog.getTenantId()).isEqualTo(TENANT_A);
    assertThat(auditLog.getProcessDefinitionId()).isEqualTo(SERVICE_TASKS_PROCESS_ID);
    assertThat(auditLog.getActorId()).isEqualTo(DEFAULT_USERNAME);
    assertThat(auditLog.getActorType()).isEqualTo(AuditLogActorTypeEnum.USER);
    assertThat(auditLog.getProcessDefinitionKey())
        .isEqualTo(String.valueOf(processInstance.getProcessDefinitionKey()));
    assertThat(auditLog.getTimestamp()).isNotNull();
    assertThat(auditLog.getAuditLogKey()).isNotNull();
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

    // then - wait for audit log entry and verify
    final var auditLogItems =
        awaitAuditLogEntry(
            client,
            AuditLogEntityTypeEnum.VARIABLE,
            AuditLogOperationTypeEnum.CREATE,
            String.valueOf(processInstanceKey));

    assertThat(auditLogItems).isNotEmpty();
    final var auditLog = auditLogItems.getFirst();
    assertThat(auditLog.getEntityType()).isEqualTo(AuditLogEntityTypeEnum.VARIABLE);
    assertThat(auditLog.getOperationType()).isEqualTo(AuditLogOperationTypeEnum.CREATE);
    assertThat(auditLog.getEntityKey()).isNotNull();
    assertThat(auditLog.getProcessInstanceKey()).isEqualTo(String.valueOf(processInstanceKey));
    assertThat(auditLog.getResult()).isEqualTo(AuditLogResultEnum.SUCCESS);
    assertThat(auditLog.getTenantId()).isEqualTo(TENANT_A);
    assertThat(auditLog.getProcessDefinitionKey())
        .isEqualTo(String.valueOf(sharedProcessDefinitionKey));
    assertThat(auditLog.getProcessDefinitionId()).isEqualTo(SERVICE_TASKS_PROCESS_ID);
    assertThat(auditLog.getActorId()).isEqualTo(DEFAULT_USERNAME);
    assertThat(auditLog.getActorType()).isEqualTo(AuditLogActorTypeEnum.USER);
    assertThat(auditLog.getTimestamp()).isNotNull();
    assertThat(auditLog.getAuditLogKey()).isNotNull();
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
    assertThat(auditLog.getEntityType()).isEqualTo(AuditLogEntityTypeEnum.VARIABLE);
    assertThat(auditLog.getOperationType()).isEqualTo(AuditLogOperationTypeEnum.UPDATE);
    assertThat(auditLog.getEntityKey()).isNotNull();
    assertThat(auditLog.getProcessInstanceKey()).isEqualTo(String.valueOf(processInstanceKey));
    assertThat(auditLog.getResult()).isEqualTo(AuditLogResultEnum.SUCCESS);
    assertThat(auditLog.getTenantId()).isEqualTo(TENANT_A);
    assertThat(auditLog.getProcessDefinitionId()).isEqualTo(SERVICE_TASKS_PROCESS_ID);
    assertThat(auditLog.getProcessDefinitionKey())
        .isEqualTo(String.valueOf(processInstance.getProcessDefinitionKey()));
    assertThat(auditLog.getActorId()).isEqualTo(DEFAULT_USERNAME);
    assertThat(auditLog.getActorType()).isEqualTo(AuditLogActorTypeEnum.USER);
    assertThat(auditLog.getTimestamp()).isNotNull();
    assertThat(auditLog.getAuditLogKey()).isNotNull();
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
    assertThat(auditLog.getEntityType()).isEqualTo(AuditLogEntityTypeEnum.INCIDENT);
    assertThat(auditLog.getOperationType()).isEqualTo(AuditLogOperationTypeEnum.RESOLVE);
    assertThat(auditLog.getEntityKey()).isEqualTo(String.valueOf(incident.getIncidentKey()));
    assertThat(auditLog.getProcessInstanceKey()).isEqualTo(String.valueOf(processInstanceKey));
    assertThat(auditLog.getResult()).isEqualTo(AuditLogResultEnum.SUCCESS);
    assertThat(auditLog.getTenantId()).isEqualTo(TENANT_A);
    assertThat(auditLog.getProcessDefinitionId()).isEqualTo(INCIDENT_PROCESS_ID);
    assertThat(auditLog.getProcessDefinitionKey())
        .isEqualTo(String.valueOf(processInstance.getProcessDefinitionKey()));
    assertThat(auditLog.getActorId()).isEqualTo(DEFAULT_USERNAME);
    assertThat(auditLog.getActorType()).isEqualTo(AuditLogActorTypeEnum.USER);
    assertThat(auditLog.getTimestamp()).isNotNull();
    assertThat(auditLog.getAuditLogKey()).isNotNull();
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
    assertThat(auditLog.getEntityType()).isEqualTo(AuditLogEntityTypeEnum.DECISION);
    assertThat(auditLog.getOperationType()).isEqualTo(AuditLogOperationTypeEnum.EVALUATE);
    assertThat(auditLog.getEntityKey()).isNotNull();
    assertThat(auditLog.getResult()).isEqualTo(AuditLogResultEnum.SUCCESS);
    assertThat(auditLog.getTenantId()).isEqualTo(TENANT_A);
    assertThat(auditLog.getActorId()).isEqualTo(DEFAULT_USERNAME);
    assertThat(auditLog.getActorType()).isEqualTo(AuditLogActorTypeEnum.USER);
    assertThat(auditLog.getTimestamp()).isNotNull();
    assertThat(auditLog.getAuditLogKey()).isNotNull();
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
    assertThat(auditLog.getEntityType()).isEqualTo(AuditLogEntityTypeEnum.BATCH);
    assertThat(auditLog.getResult()).isEqualTo(AuditLogResultEnum.SUCCESS);
    assertThat(auditLog.getOperationType()).isEqualTo(AuditLogOperationTypeEnum.CREATE);
    assertThat(auditLog.getEntityKey()).isEqualTo(batchOperationKey);
    assertThat(auditLog.getActorId()).isEqualTo(DEFAULT_USERNAME);
    assertThat(auditLog.getActorType()).isEqualTo(AuditLogActorTypeEnum.USER);
    assertThat(auditLog.getTimestamp()).isNotNull();
    assertThat(auditLog.getAuditLogKey()).isNotNull();
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
    assertThat(batchAuditLog.getEntityType()).isEqualTo(AuditLogEntityTypeEnum.BATCH);
    assertThat(batchAuditLog.getResult()).isEqualTo(AuditLogResultEnum.SUCCESS);
    assertThat(batchAuditLog.getOperationType()).isEqualTo(AuditLogOperationTypeEnum.CREATE);
    assertThat(batchAuditLog.getEntityKey()).isEqualTo(batchOperationKey);
    assertThat(batchAuditLog.getActorId()).isEqualTo(DEFAULT_USERNAME);
    assertThat(batchAuditLog.getActorType()).isEqualTo(AuditLogActorTypeEnum.USER);
    assertThat(batchAuditLog.getTimestamp()).isNotNull();
    assertThat(batchAuditLog.getAuditLogKey()).isNotNull();
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
    assertThat(batchAuditLog.getEntityType()).isEqualTo(AuditLogEntityTypeEnum.BATCH);
    assertThat(batchAuditLog.getResult()).isEqualTo(AuditLogResultEnum.SUCCESS);
    assertThat(batchAuditLog.getOperationType()).isEqualTo(AuditLogOperationTypeEnum.CREATE);
    assertThat(batchAuditLog.getEntityKey()).isEqualTo(batchOperationKey);
    assertThat(batchAuditLog.getActorId()).isEqualTo(DEFAULT_USERNAME);
    assertThat(batchAuditLog.getActorType()).isEqualTo(AuditLogActorTypeEnum.USER);
    assertThat(batchAuditLog.getTimestamp()).isNotNull();
    assertThat(batchAuditLog.getAuditLogKey()).isNotNull();
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
    assertThat(batchAuditLog.getEntityType()).isEqualTo(AuditLogEntityTypeEnum.BATCH);
    assertThat(batchAuditLog.getResult()).isEqualTo(AuditLogResultEnum.SUCCESS);
    assertThat(batchAuditLog.getOperationType()).isEqualTo(AuditLogOperationTypeEnum.CREATE);
    assertThat(batchAuditLog.getEntityKey()).isEqualTo(batchOperationKey);
    assertThat(batchAuditLog.getActorId()).isEqualTo(DEFAULT_USERNAME);
    assertThat(batchAuditLog.getActorType()).isEqualTo(AuditLogActorTypeEnum.USER);
    assertThat(batchAuditLog.getTimestamp()).isNotNull();
    assertThat(batchAuditLog.getAuditLogKey()).isNotNull();
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
}
