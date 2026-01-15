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
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.AuditLogEntityTypeEnum;
import io.camunda.client.api.search.enums.AuditLogOperationTypeEnum;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.awaitility.Awaitility;

public class AuditLogUtils {

  public static final String DEFAULT_USERNAME = "demo";

  public static final String TENANT_A = "tenantA";
  public static final String TENANT_B = "tenantB";

  public static final long USER_TASK_KEY = 123L;

  public static final String PROCESS_A_ID = "processA";
  public static final BpmnModelInstance PROCESS_A =
      Bpmn.createExecutableProcess(PROCESS_A_ID)
          .startEvent()
          .serviceTask("taskA", t -> t.zeebeJobType("taskA"))
          .endEvent()
          .done();
  public static final String PROCESS_B_ID = "USER_TASKS_B";
  public static final BpmnModelInstance PROCESS_B =
      Bpmn.createExecutableProcess(PROCESS_B_ID)
          .startEvent()
          .userTask("taskB")
          .zeebeUserTask()
          .endEvent()
          .done();

  public static final String PROCESS_C_ID = "USER_TASKS_C";
  public static final BpmnModelInstance PROCESS_C =
      Bpmn.createExecutableProcess(PROCESS_C_ID)
          .startEvent()
          .userTask("taskC")
          .zeebeUserTask()
          .endEvent()
          .done();

  private final boolean useRestApi;
  private final CamundaClient defaultClient;

  private final List<DeploymentEvent> deployments = new ArrayList<>();
  private final List<ProcessInstanceEvent> processInstances = new ArrayList<>();
  private final List<UserTenantAssignment> userTenantAssignments = new ArrayList<>();
  private final List<UserTaskRecordValue> userTasks = new ArrayList<>();

  public AuditLogUtils(final CamundaClient defaultClient) {
    this(defaultClient, true);
  }

  public AuditLogUtils(final CamundaClient defaultClient, final boolean useRestApi) {
    this.defaultClient = defaultClient;
    this.useRestApi = useRestApi;
  }

  public void createTenant(final String tenant) {
    defaultClient.newCreateTenantCommand().tenantId(tenant).name(tenant).send().join();
  }

  public void assignUserToTenant(final String username, final String tenant) {
    defaultClient.newAssignUserToTenantCommand().username(username).tenantId(tenant).send().join();

    userTenantAssignments.add(new UserTenantAssignment(username, tenant));
  }

  public DeploymentEvent deployResource(
      final String resourceName, final BpmnModelInstance modelInstance, final String tenant) {
    final var deployment =
        defaultClient
            .newDeployResourceCommand()
            .addProcessModel(modelInstance, resourceName)
            .tenantId(tenant)
            .send()
            .join();

    deployments.add(deployment);

    return deployment;
  }

  public ProcessInstanceEvent startProcessInstance(final String processId, final String tenant) {
    final var commandStep1 = defaultClient.newCreateInstanceCommand();
    if (useRestApi) {
      commandStep1.useRest();
    } else {
      commandStep1.useGrpc();
    }

    final var instanceCreated =
        commandStep1.bpmnProcessId(processId).latestVersion().tenantId(tenant).send().join();

    processInstances.add(instanceCreated);

    return instanceCreated;
  }

  public void assignUserToUserTask(final String username, final Long processInstanceKey) {
    final var task =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .limit(1L)
            .findFirst()
            .get()
            .getValue();
    userTasks.add(task);

    defaultClient.newAssignUserTaskCommand(task.getUserTaskKey()).assignee(username).send().join();

    Awaitility.await("Audit log entry is created for the assigned user task")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              // wait until user task is assigned and audit log entry is created
              // currently it's not possible to search by entityKey, thus we are using the
              // processInstance as a workaround
              final var auditLogItems =
                  defaultClient
                      .newAuditLogSearchRequest()
                      .filter(
                          fn ->
                              fn.entityType(AuditLogEntityTypeEnum.USER_TASK)
                                  .operationType(AuditLogOperationTypeEnum.ASSIGN)
                                  .processInstanceKey(String.valueOf(task.getProcessInstanceKey())))
                      .send()
                      .join();
              assertThat(auditLogItems.items()).hasSize(1);
            });
  }

  public AuditLogUtils generateDefaults() {
    init();

    // tenant A
    deployResource("A.bpmn", PROCESS_A, TENANT_A);
    startProcessInstance(PROCESS_A_ID, TENANT_A);

    deployResource("B.bpmn", PROCESS_B, TENANT_A);
    final var instanceWithUserTask = startProcessInstance(PROCESS_B_ID, TENANT_A);
    assignUserToUserTask(DEFAULT_USERNAME, instanceWithUserTask.getProcessInstanceKey());

    // tenant B
    deployResource("A.bpmn", PROCESS_A, TENANT_B);
    startProcessInstance(PROCESS_A_ID, TENANT_B);

    deployResource("C.bpmn", PROCESS_C, TENANT_B);
    final var instanceWithUserTask2 = startProcessInstance(PROCESS_C_ID, TENANT_B);
    assignUserToUserTask(DEFAULT_USERNAME, instanceWithUserTask2.getProcessInstanceKey());

    return this;
  }

  public AuditLogUtils init() {
    createTenant(TENANT_A);
    createTenant(TENANT_B);
    assignUserToTenant(DEFAULT_USERNAME, TENANT_A);
    assignUserToTenant(DEFAULT_USERNAME, TENANT_B);
    await();
    return this;
  }

  private void awaitUserTenantAssignments(final UserTenantAssignment assignment) {
    Awaitility.await("User is assigned to tenant")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var users =
                  defaultClient.newUsersByTenantSearchRequest(assignment.tenantId).send().join();
              assertThat(
                      users.items().stream()
                          .anyMatch(
                              tenantUser -> tenantUser.getUsername().equals(assignment.username)))
                  .isTrue();
            });
  }

  private void awaitProcessInstances(final ProcessInstanceEvent processInstance) {
    // wait until audit log for the process instance is exported
    final var processInstanceKey = String.valueOf(processInstance.getProcessInstanceKey());

    Awaitility.await("Audit log entry is created for the process instance")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var auditLogItems =
                  defaultClient
                      .newAuditLogSearchRequest()
                      .filter(
                          fn ->
                              fn.entityType(AuditLogEntityTypeEnum.PROCESS_INSTANCE)
                                  .operationType(AuditLogOperationTypeEnum.CREATE)
                                  .processInstanceKey(processInstanceKey))
                      .send()
                      .join();

              assertThat(auditLogItems.items()).hasSize(1);
            });
  }

  public CamundaClient getDefaultClient() {
    return defaultClient;
  }

  public List<DeploymentEvent> getDeployments() {
    return deployments;
  }

  public List<ProcessInstanceEvent> getProcessInstances() {
    return processInstances;
  }

  public List<UserTenantAssignment> getUserTenantAssignments() {
    return userTenantAssignments;
  }

  public List<UserTaskRecordValue> getUserTasks() {
    return userTasks;
  }

  public AuditLogUtils await() {
    if (!getUserTenantAssignments().isEmpty()) {
      // this waits for the creation export
      awaitUserTenantAssignments(getUserTenantAssignments().getLast());
    }

    // this waits for the actual audit log
    if (!getProcessInstances().isEmpty()) {
      awaitProcessInstances(getProcessInstances().getLast());
    }

    return this;
  }

  private record UserTenantAssignment(String username, String tenantId) {}
}
