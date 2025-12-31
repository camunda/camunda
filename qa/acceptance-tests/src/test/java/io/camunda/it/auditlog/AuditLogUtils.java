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
import io.camunda.client.api.search.enums.AuditLogEntityTypeEnum;
import io.camunda.client.api.search.enums.AuditLogOperationTypeEnum;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.collection.Tuple;
import java.time.Duration;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;

public class AuditLogUtils {

  public static final String DEFAULT_USERNAME = "demo";
  public static final String PROCESS_ID_DEPLOYED_RESOURCES = "DEPLOYED_RESOURCES";
  public static final BpmnModelInstance TWO_SERVICE_TASKS_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID_DEPLOYED_RESOURCES)
          .startEvent()
          .serviceTask("taskA", t -> t.zeebeJobType("taskA"))
          .serviceTask("taskB", t -> t.zeebeJobType("taskB"))
          .endEvent()
          .done();
  public static final String PROCESS_ID_A = "processA";
  public static final BpmnModelInstance PROCESS_TENANT_A =
      Bpmn.createExecutableProcess(PROCESS_ID_A)
          .startEvent()
          .serviceTask("taskA", t -> t.zeebeJobType("taskA"))
          .endEvent()
          .done();
  public static final String PROCESS_ID_B = "processB";
  public static final BpmnModelInstance PROCESS_TENANT_B =
      Bpmn.createExecutableProcess(PROCESS_ID_B)
          .startEvent()
          .serviceTask("taskB", t -> t.zeebeJobType("taskB"))
          .endEvent()
          .done();
  public static final String USER_TASKS_PROCESS_ID = "USER_TASKS";
  public static final BpmnModelInstance PROCESS_C =
      Bpmn.createExecutableProcess(USER_TASKS_PROCESS_ID)
          .startEvent()
          .userTask("taskC")
          .zeebeUserTask()
          .endEvent()
          .done();
  public static final String TENANT_A = "tenantA";
  public static final String TENANT_B = "tenantB";

  public static void createTenant(final CamundaClient camundaClient, final String tenant) {
    camundaClient.newCreateTenantCommand().tenantId(tenant).name(tenant).send().join();
  }

  public static void assignUserToTenant(
      final CamundaClient camundaClient, final String username, final String tenant) {
    camundaClient.newAssignUserToTenantCommand().username(username).tenantId(tenant).send().join();

    Awaitility.await("User is assigned to tenant")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var users = camundaClient.newUsersByTenantSearchRequest(tenant).send().join();
              assertThat(
                      users.items().stream()
                          .anyMatch(tenantUser -> tenantUser.getUsername().equals(username)))
                  .isTrue();
            });
  }

  public static void deployResource(
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

  public static ProcessInstanceEvent startProcessInstance(
      final CamundaClient camundaClient,
      final String processId,
      final String tenant,
      final boolean useRest) {
    final var commandStep1 = camundaClient.newCreateInstanceCommand();
    if (useRest) {
      commandStep1.useRest();
    } else {
      commandStep1.useGrpc();
    }

    final var instanceCreated =
        commandStep1.bpmnProcessId(processId).latestVersion().tenantId(tenant).send().join();

    return instanceCreated;
  }

  public static Tuple<String, ProcessInstanceEvent> generateData(
      final CamundaClient adminClient, final boolean useRest) {
    createTenant(adminClient, TENANT_A);
    createTenant(adminClient, TENANT_B);
    assignUserToTenant(adminClient, DEFAULT_USERNAME, TENANT_A);
    assignUserToTenant(adminClient, DEFAULT_USERNAME, TENANT_B);

    deployResource(adminClient, "testProcess.bpmn", TWO_SERVICE_TASKS_PROCESS, TENANT_A);
    deployResource(adminClient, "processA.bpmn", PROCESS_TENANT_A, TENANT_A);
    deployResource(adminClient, "processB.bpmn", PROCESS_TENANT_B, TENANT_B);
    deployResource(adminClient, "processC.bpmn", PROCESS_C, TENANT_B);

    final var processInstanceEvent =
        startProcessInstance(adminClient, PROCESS_ID_DEPLOYED_RESOURCES, TENANT_A, useRest);
    startProcessInstance(adminClient, PROCESS_ID_A, TENANT_A, useRest);
    startProcessInstance(adminClient, PROCESS_ID_B, TENANT_B, useRest);
    startProcessInstance(adminClient, USER_TASKS_PROCESS_ID, TENANT_B, useRest);

    // TODO: investigate flakiness with UserTask#ASSIGNED events:
    // https://github.com/camunda/camunda/issues/43319
    //    assignUserToUserTask(adminClient, USER_TASKS_PROCESS_ID, DEFAULT_USERNAME);

    // wait until all process instance creation records are exported and audit log entries are
    // created
    RecordingExporter.processInstanceCreationRecords()
        .withIntent(ProcessInstanceCreationIntent.CREATED)
        .limit(3L);
    final String[] auditLogKey = new String[1];
    Awaitility.await("Audit log entries are created for the created process instances")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var auditLogItems =
                  adminClient
                      .newAuditLogSearchRequest()
                      .filter(
                          fn ->
                              fn.entityType(AuditLogEntityTypeEnum.PROCESS_INSTANCE)
                                  .operationType(AuditLogOperationTypeEnum.CREATE))
                      .send()
                      .join();
              assertThat(auditLogItems.items()).hasSize(4);
              assertThat(
                      auditLogItems.items().stream()
                          .map(item -> item.getProcessDefinitionId())
                          .collect(Collectors.toSet()))
                  .containsExactlyInAnyOrder(
                      PROCESS_ID_DEPLOYED_RESOURCES,
                      PROCESS_ID_A,
                      PROCESS_ID_B,
                      USER_TASKS_PROCESS_ID);
              auditLogKey[0] = auditLogItems.items().get(0).getAuditLogKey();
            });

    return Tuple.of(auditLogKey[0], processInstanceEvent);
  }

  public static void assignUserToUserTask(
      final CamundaClient adminClient, final String processId, final String username) {
    RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED).limit(1L);
    Awaitility.await("Audit log entry is created for the assigned user task")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              final var userTasks =
                  adminClient
                      .newUserTaskSearchRequest()
                      .filter(f -> f.bpmnProcessId(processId))
                      .send()
                      .join();
              assertThat(userTasks.items()).hasSize(1);
              final var userTask = userTasks.items().get(0);
              adminClient
                  .newAssignUserTaskCommand(userTask.getUserTaskKey())
                  .assignee(username)
                  .send()
                  .join();

              // wait until user task is assigned and audit log entry is created
              final var auditLogItems =
                  adminClient
                      .newAuditLogSearchRequest()
                      .filter(
                          fn ->
                              fn.entityType(AuditLogEntityTypeEnum.USER_TASK)
                                  .operationType(AuditLogOperationTypeEnum.ASSIGN)
                                  .processInstanceKey(
                                      String.valueOf(userTask.getProcessInstanceKey())))
                      .send()
                      .join();
              assertThat(auditLogItems.items()).hasSize(1);
            });
  }

  public static long createAuthorization(
      final CamundaClient camundaClient,
      final String username,
      final ResourceType authorizationResourceType,
      final PermissionType permission) {
    final var authorizationResponse =
        camundaClient
            .newCreateAuthorizationCommand()
            .ownerId(username)
            .ownerType(io.camunda.client.api.search.enums.OwnerType.USER)
            .resourceId("*")
            .resourceType(authorizationResourceType)
            .permissionTypes(permission)
            .send()
            .join();
    final var authorizationKey = authorizationResponse.getAuthorizationKey();

    RecordingExporter.authorizationRecords(AuthorizationIntent.CREATED).limit(1L);
    Awaitility.await("Audit log entry is created for the authorization")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              final var authorization =
                  camundaClient.newAuthorizationGetRequest(authorizationKey).send().join();
              assertThat(authorization).isNotNull();
            });

    return authorizationKey;
  }
}
