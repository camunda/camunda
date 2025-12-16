/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auditlog;

import static io.camunda.client.api.search.enums.PermissionType.CREATE_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.READ;
import static io.camunda.client.api.search.enums.ResourceType.AUDIT_LOG;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.AuditLogCategoryEnum;
import io.camunda.client.api.search.enums.AuditLogOperationTypeEnum;
import io.camunda.client.protocol.rest.AuditLogResultEnum;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class AuditLogIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withMultiTenancyEnabled()
          .withAuthorizationsEnabled()
          .withAuthenticatedAccess();

  private static final String DEFAULT_USERNAME = "demo";
  private static final String USER_A_USERNAME = "userA";
  private static final String USER_B_USERNAME = "userB";
  private static final String PASSWORD = "password";
  private static CamundaClient adminClient;

  private static final String PROCESS_ID = "modifiableProcess";
  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("taskA", t -> t.zeebeJobType("taskA"))
          .serviceTask("taskB", t -> t.zeebeJobType("taskB"))
          .endEvent()
          .done();

  private static final String PROCESS_ID_A = "processA";
  private static final BpmnModelInstance PROCESS_A =
      Bpmn.createExecutableProcess(PROCESS_ID_A)
          .startEvent()
          .serviceTask("taskA", t -> t.zeebeJobType("taskA"))
          .endEvent()
          .done();

  private static final String PROCESS_ID_B = "processB";
  private static final BpmnModelInstance PROCESS_B =
      Bpmn.createExecutableProcess(PROCESS_ID_B)
          .startEvent()
          .serviceTask("taskB", t -> t.zeebeJobType("taskB"))
          .endEvent()
          .done();

  @UserDefinition
  private static final TestUser USER_A =
      new TestUser(
          USER_A_USERNAME,
          PASSWORD,
          List.of(
              new Permissions(PROCESS_DEFINITION, CREATE_PROCESS_INSTANCE, List.of(PROCESS_ID_A)),
              new Permissions(AUDIT_LOG, READ, List.of("*"))));

  @UserDefinition
  private static final TestUser USER_B =
      new TestUser(
          USER_B_USERNAME,
          PASSWORD,
          List.of(
              new Permissions(PROCESS_DEFINITION, CREATE_PROCESS_INSTANCE, List.of(PROCESS_ID_B)),
              new Permissions(AUDIT_LOG, READ, List.of("*"))));

  private static final String TENANT_A = "tenantA";
  private static final String TENANT_B = "tenantB";
  private static final Set<Long> STARTED_PROCESS_INSTANCES = new HashSet<>();

  @BeforeAll
  static void setUp() {
    createTenant(adminClient, TENANT_A);
    createTenant(adminClient, TENANT_B);

    assignUserToTenant(adminClient, DEFAULT_USERNAME, TENANT_A);
    assignUserToTenant(adminClient, DEFAULT_USERNAME, TENANT_B);
    assignUserToTenant(adminClient, USER_A_USERNAME, TENANT_A);
    assignUserToTenant(adminClient, USER_B_USERNAME, TENANT_B);

    deployResource(adminClient, "testProcess.bpmn", PROCESS, TENANT_A);
    deployResource(adminClient, "processA.bpmn", PROCESS_A, TENANT_A);
    deployResource(adminClient, "processB.bpmn", PROCESS_B, TENANT_B);
  }

  @AfterEach
  void cleanUp() {
    // cancel all process instances to ensure no jobs are left in the system
    STARTED_PROCESS_INSTANCES.forEach(
        processInstanceKey -> cancelProcessInstance(adminClient, processInstanceKey));
    STARTED_PROCESS_INSTANCES.clear();
  }

  @Test
  void shouldCreateAuditLogEntryFromPICreation() {
    // when
    // a process instance is created
    final var processInstanceEvent = startProcessInstance(adminClient, PROCESS_ID, TENANT_A);
    final long processInstanceKey = processInstanceEvent.getProcessInstanceKey();

    // then
    // wait for the audit log entry to be created and available
    final var piCreated =
        RecordingExporter.processInstanceCreationRecords()
            .withIntent(ProcessInstanceCreationIntent.CREATED)
            .withKey(processInstanceKey)
            .exists();
    if (piCreated) {
      Awaitility.await("Audit log entry is created for process instance modification")
          .ignoreExceptionsInstanceOf(ProblemException.class)
          .untilAsserted(
              () -> {
                final var auditLogItems =
                    adminClient
                        .newAuditLogSearchRequest()
                        .filter(
                            fn ->
                                fn.processInstanceKey(String.valueOf(processInstanceKey))
                                    .operationType(AuditLogOperationTypeEnum.CREATE))
                        .send()
                        .join();

                assertThat(auditLogItems.items().size()).isOne();
                final var auditLog = auditLogItems.items().get(0);

                assertThat(auditLog).isNotNull();
                assertThat(auditLog.getOperationType()).isEqualTo(AuditLogOperationTypeEnum.CREATE);
                assertThat(auditLog.getCategory()).isEqualTo(AuditLogCategoryEnum.OPERATOR);
                assertThat(auditLog.getProcessDefinitionId())
                    .isEqualTo(processInstanceEvent.getBpmnProcessId());
                assertThat(auditLog.getProcessDefinitionKey())
                    .isEqualTo(processInstanceEvent.getProcessDefinitionKey());
                assertThat(auditLog.getProcessInstanceKey()).isEqualTo(processInstanceKey);
                assertThat(auditLog.getTenantId()).isEqualTo(TENANT_A);
                assertThat(auditLog.getResult()).isEqualTo(AuditLogResultEnum.SUCCESS);
              });
    }
  }

  @Test
  void shouldFetchAuditLogEntryById() {
    // when
    // a process instance is created
    final var processInstanceEvent = startProcessInstance(adminClient, PROCESS_ID, TENANT_A);
    final long processInstanceKey = processInstanceEvent.getProcessInstanceKey();

    // then
    // wait for the audit log entry to be created and available
    final var piCreated =
        RecordingExporter.processInstanceCreationRecords()
            .withIntent(ProcessInstanceCreationIntent.CREATED)
            .withKey(processInstanceKey)
            .exists();
    if (piCreated) {
      Awaitility.await("Audit log entry is created and can be fetched by ID")
          .ignoreExceptionsInstanceOf(ProblemException.class)
          .untilAsserted(
              () -> {
                // First, search for the audit log entry to get its ID
                final var auditLogItems = adminClient.newAuditLogSearchRequest().send().join();

                assertThat(auditLogItems.items().size()).isGreaterThan(0);
                final var searchedAuditLog = auditLogItems.items().get(0);
                final String auditLogKey = searchedAuditLog.getAuditLogKey();

                // Fetch the audit log entry by ID
                final var fetchedAuditLog =
                    adminClient.newAuditLogGetRequest(auditLogKey).send().join();

                // Verify the fetched audit log entry matches the searched one
                assertThat(fetchedAuditLog).isNotNull();
                assertThat(fetchedAuditLog.getAuditLogKey()).isEqualTo(auditLogKey);
                assertThat(fetchedAuditLog.getOperationType())
                    .isEqualTo(AuditLogOperationTypeEnum.CREATE);
                assertThat(fetchedAuditLog.getCategory()).isEqualTo(AuditLogCategoryEnum.OPERATOR);
                assertThat(fetchedAuditLog.getProcessDefinitionId())
                    .isEqualTo(processInstanceEvent.getBpmnProcessId());
                assertThat(fetchedAuditLog.getProcessDefinitionKey())
                    .isEqualTo(processInstanceEvent.getProcessDefinitionKey());
                assertThat(fetchedAuditLog.getProcessInstanceKey()).isEqualTo(processInstanceKey);
                assertThat(fetchedAuditLog.getTenantId()).isEqualTo(TENANT_A);
                assertThat(fetchedAuditLog.getResult()).isEqualTo(AuditLogResultEnum.SUCCESS);
              });
    }
  }

  @Disabled("WIP")
  @Test
  void shouldIsolateAuditLogsByTenant(
      @Authenticated(USER_A_USERNAME) final CamundaClient userAClient,
      @Authenticated(USER_B_USERNAME) final CamundaClient userBClient) {
    // when
    // User A starts a process instance of Process A
    final var processInstanceA =
        userAClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID_A)
            .latestVersion()
            .tenantId(TENANT_A)
            .send()
            .join();
    STARTED_PROCESS_INSTANCES.add(processInstanceA.getProcessInstanceKey());

    // User B starts a process instance of Process B
    final var processInstanceB =
        userBClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID_B)
            .latestVersion()
            .tenantId(TENANT_B)
            .send()
            .join();
    STARTED_PROCESS_INSTANCES.add(processInstanceB.getProcessInstanceKey());

    // then
    // Wait for audit log entries to be created
    RecordingExporter.processInstanceCreationRecords()
        .withIntent(ProcessInstanceCreationIntent.CREATED)
        .limit(2L);
    Awaitility.await("Audit logs are created for both process instances")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              // User A should only see audit logs from Process A
              final var userAAuditLogs = userAClient.newAuditLogSearchRequest().send().join();

              assertThat(userAAuditLogs.items()).isNotEmpty();
              assertThat(
                      userAAuditLogs.items().stream()
                          .allMatch(log -> TENANT_A.equals(log.getTenantId())))
                  .isTrue();
              assertThat(
                      userAAuditLogs.items().stream()
                          .anyMatch(log -> PROCESS_ID_A.equals(log.getProcessDefinitionId())))
                  .isTrue();
              assertThat(
                      userAAuditLogs.items().stream()
                          .noneMatch(log -> PROCESS_ID_B.equals(log.getProcessDefinitionId())))
                  .isTrue();

              // User B should only see audit logs from Process B
              final var userBAuditLogs = userBClient.newAuditLogSearchRequest().send().join();

              assertThat(userBAuditLogs.items()).isNotEmpty();
              assertThat(
                      userBAuditLogs.items().stream()
                          .allMatch(log -> TENANT_B.equals(log.getTenantId())))
                  .isTrue();
              assertThat(
                      userBAuditLogs.items().stream()
                          .anyMatch(log -> PROCESS_ID_B.equals(log.getProcessDefinitionId())))
                  .isTrue();
              assertThat(
                      userBAuditLogs.items().stream()
                          .noneMatch(log -> PROCESS_ID_A.equals(log.getProcessDefinitionId())))
                  .isTrue();
            });
  }

  private static void createTenant(final CamundaClient camundaClient, final String tenant) {
    camundaClient.newCreateTenantCommand().tenantId(tenant).name(tenant).send().join();
  }

  private static void assignUserToTenant(
      final CamundaClient camundaClient, final String username, final String tenant) {
    camundaClient.newAssignUserToTenantCommand().username(username).tenantId(tenant).send().join();
  }

  private static void deployResource(
      final CamundaClient camundaClient,
      final String resourceName,
      final BpmnModelInstance modelInstance,
      final String tenant) {
    camundaClient
        .newDeployResourceCommand()
        .addProcessModel(modelInstance, resourceName)
        .tenantId(tenant)
        .send()
        .join();
  }

  private static ProcessInstanceEvent startProcessInstance(
      final CamundaClient camundaClient, final String processId, final String tenant) {
    final var instanceCreated =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .tenantId(tenant)
            .send()
            .join();
    STARTED_PROCESS_INSTANCES.add(instanceCreated.getProcessInstanceKey());

    return instanceCreated;
  }

  private static void cancelProcessInstance(
      final CamundaClient camundaClient, final long processInstanceKey) {
    camundaClient.newCancelInstanceCommand(processInstanceKey).send().join();
  }
}
