/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auditlog;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.AuditLogCategoryEnum;
import io.camunda.client.api.search.enums.AuditLogOperationTypeEnum;
import io.camunda.client.protocol.rest.AuditLogResultEnum;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.HashSet;
import java.util.Set;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
  private static CamundaClient adminClient;

  private static final String PROCESS_ID = "testProcess";
  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("taskA", t -> t.zeebeJobType("taskA"))
          .serviceTask("taskB", t -> t.zeebeJobType("taskB"))
          .endEvent()
          .done();

  private static final String TENANT_A = "tenantA";
  private static final Set<Long> STARTED_PROCESS_INSTANCES = new HashSet<>();

  @BeforeAll
  static void setUp() {
    createTenant(adminClient, TENANT_A);
    adminClient
        .newAssignUserToTenantCommand()
        .username(DEFAULT_USERNAME)
        .tenantId(TENANT_A)
        .send()
        .join();
    deployResource(adminClient, "testProcess.bpmn", PROCESS, TENANT_A);
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

  private static void createTenant(final CamundaClient camundaClient, final String tenant) {
    camundaClient.newCreateTenantCommand().tenantId(tenant).name(tenant).send().join();
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
