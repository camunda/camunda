/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.tasklist.compatibility;

import static io.camunda.client.api.search.enums.UserTaskState.COMPLETED;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.client.CamundaClient;
import io.camunda.client.protocol.rest.PermissionTypeEnum;
import io.camunda.client.protocol.rest.ResourceTypeEnum;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.User;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.cluster.TestRestTasklistClient;
import io.camunda.qa.util.cluster.TestRestTasklistClient.ProcessDefinitionResponse;
import io.camunda.qa.util.cluster.TestSimpleCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskSearchResponse;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class NoCompatibilityModeTasklistUserTaskAuthorizationIT {

  @MultiDbTestApplication
  private static final TestSimpleCamundaApplication STANDALONE_CAMUNDA =
      new TestSimpleCamundaApplication()
          .withAuthorizationsEnabled()
          .withBasicAuth()
          .withProperty("camunda.tasklist.zeebe.compatibility.enabled", false);

  private static final String PROCESS_WITH_USER_TASK = "PROCESS_WITH_USER_TASK";
  private static final String PROCESS_WITH_USER_TASK_PRE_ASSIGNED =
      "PROCESS_WITH_USER_TASK_PRE_ASSIGNED";
  private static final String PROCESS_ID_WITH_JOB_BASED_USERTASK =
      "PROCESS_WITH_JOB_BASED_USERTASK";
  private static final String PROCESS_ID_WITH_JOB_BASED_USERTASK_PRE_ASSIGNED =
      "PROCESS_WITH_JOB_BASED_USERTASK_PRE_ASSIGNED";

  private static final String ADMIN_USER_NAME = "foo";
  private static final String ADMIN_USER_PASSWORD = "foo";

  private static final String TEST_USER_NAME_NO_PERMISSION = "bar";
  private static final String TEST_USER_NAME_WITH_PERMISSION = "withPermissionUser";
  private static final String TEST_USER_PASSWORD = "bar";
  @AutoClose private static TestRestTasklistClient tasklistRestClient;

  @UserDefinition
  private static final User ADMIN_USER =
      new User(
          ADMIN_USER_NAME,
          ADMIN_USER_PASSWORD,
          List.of(
              new Permissions(ResourceTypeEnum.RESOURCE, PermissionTypeEnum.CREATE, List.of("*")),
              new Permissions(
                  ResourceTypeEnum.AUTHORIZATION, PermissionTypeEnum.CREATE, List.of("*")),
              new Permissions(
                  ResourceTypeEnum.PROCESS_DEFINITION,
                  PermissionTypeEnum.READ_PROCESS_DEFINITION,
                  List.of("*")),
              new Permissions(
                  ResourceTypeEnum.PROCESS_DEFINITION,
                  PermissionTypeEnum.READ_USER_TASK,
                  List.of("*")),
              new Permissions(
                  ResourceTypeEnum.PROCESS_DEFINITION,
                  PermissionTypeEnum.READ_PROCESS_INSTANCE,
                  List.of("*")),
              new Permissions(
                  ResourceTypeEnum.PROCESS_DEFINITION,
                  PermissionTypeEnum.CREATE_PROCESS_INSTANCE,
                  List.of("*")),
              new Permissions(ResourceTypeEnum.USER, PermissionTypeEnum.CREATE, List.of("*")),
              new Permissions(
                  ResourceTypeEnum.AUTHORIZATION, PermissionTypeEnum.UPDATE, List.of("*"))));

  @UserDefinition
  private static final User TEST_USER_NO_PERMISSIONS =
      new User(TEST_USER_NAME_NO_PERMISSION, TEST_USER_PASSWORD, List.of());

  @UserDefinition
  private static final User TEST_USER_WITH_PERMISSIONS =
      new User(
          TEST_USER_NAME_WITH_PERMISSION,
          TEST_USER_PASSWORD,
          List.of(
              new Permissions(
                  ResourceTypeEnum.PROCESS_DEFINITION,
                  PermissionTypeEnum.UPDATE_USER_TASK,
                  List.of(PROCESS_ID_WITH_JOB_BASED_USERTASK)),
              new Permissions(
                  ResourceTypeEnum.PROCESS_DEFINITION,
                  PermissionTypeEnum.UPDATE_USER_TASK,
                  List.of(
                      PROCESS_WITH_USER_TASK,
                      PROCESS_WITH_USER_TASK_PRE_ASSIGNED,
                      PROCESS_ID_WITH_JOB_BASED_USERTASK_PRE_ASSIGNED)),
              new Permissions(
                  ResourceTypeEnum.PROCESS_DEFINITION,
                  PermissionTypeEnum.READ_PROCESS_DEFINITION,
                  List.of(PROCESS_WITH_USER_TASK)),
              new Permissions(
                  ResourceTypeEnum.PROCESS_DEFINITION,
                  PermissionTypeEnum.CREATE_PROCESS_INSTANCE,
                  List.of(PROCESS_WITH_USER_TASK))));

  private static long processWithUserTaskDefinitionKey;

  @BeforeAll
  public static void beforeAll(@Authenticated(ADMIN_USER_NAME) final CamundaClient adminClient) {
    tasklistRestClient = STANDALONE_CAMUNDA.newTasklistClient();

    // deploy a process as admin user
    processWithUserTaskDefinitionKey =
        deployResource(adminClient, "process/process_public_start.bpmn");
    deployResource(adminClient, "process/process_with_assigned_user_task.bpmn");
    // deploy process with a job based user task process
    deployResource(adminClient, "process/process_job_based_user_task.bpmn");
    deployResource(adminClient, "process/process_with_assigned_job_based_user_task.bpmn");
  }

  @Test
  public void shouldNotReturnProcessDefinitionWithUnauthorizedUser(
      @Authenticated(TEST_USER_NAME_NO_PERMISSION) final CamundaClient noPermission) {
    // given (non-admin) user without any authorizations

    // when
    final var response =
        tasklistRestClient
            .withAuthentication(TEST_USER_NAME_NO_PERMISSION, TEST_USER_PASSWORD)
            .getProcessDefinition(processWithUserTaskDefinitionKey);

    // then
    assertThat(response).isNotNull();
    assertThat(response.statusCode()).isEqualTo(403);
  }

  @Test
  public void shouldBeAuthorizedToRetrieveProcessDefinition(
      @Authenticated(TEST_USER_NAME_WITH_PERMISSION) final CamundaClient withPermission) {
    // given (non-admin) user without any authorizations

    // when
    final var response =
        tasklistRestClient
            .withAuthentication(TEST_USER_NAME_WITH_PERMISSION, TEST_USER_PASSWORD)
            .getProcessDefinition(processWithUserTaskDefinitionKey);

    // then
    assertThat(response).isNotNull();
    assertThat(response.statusCode()).isEqualTo(200);
  }

  @Test
  public void shouldReturnNoDefinitionsWithUnauthorizedUser(
      @Authenticated(TEST_USER_NAME_NO_PERMISSION) final CamundaClient noPermission) {
    // given (non-admin) user without any authorizations

    // when
    final var response =
        tasklistRestClient
            .withAuthentication(TEST_USER_NAME_NO_PERMISSION, TEST_USER_PASSWORD)
            .searchProcessDefinitions();

    // then
    assertThat(response).isNotNull();
    assertThat(response).isEmpty();
  }

  @Test
  public void shouldBeAuthorizedToRetrieveDefinitionOne(
      @Authenticated(TEST_USER_NAME_WITH_PERMISSION) final CamundaClient withPermission) {
    // given (non-admin) user without any authorizations

    // when
    final var response =
        tasklistRestClient
            .withAuthentication(TEST_USER_NAME_WITH_PERMISSION, TEST_USER_PASSWORD)
            .searchProcessDefinitions();

    // then
    assertThat(response).isNotNull();
    assertThat(response.stream().map(ProcessDefinitionResponse::bpmnProcessId))
        .containsExactly(PROCESS_WITH_USER_TASK);
  }

  @Test
  public void shouldBeAuthorizedToRetrieveDefinitions(
      @Authenticated(ADMIN_USER_NAME) final CamundaClient adminClient) {
    // given (non-admin) user without any authorizations

    // when
    final var response =
        tasklistRestClient
            .withAuthentication(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
            .searchProcessDefinitions();

    // then
    assertThat(response).isNotNull();
    assertThat(response.stream().map(ProcessDefinitionResponse::bpmnProcessId))
        .containsExactlyInAnyOrder(
            PROCESS_WITH_USER_TASK,
            PROCESS_WITH_USER_TASK_PRE_ASSIGNED,
            PROCESS_ID_WITH_JOB_BASED_USERTASK_PRE_ASSIGNED,
            PROCESS_ID_WITH_JOB_BASED_USERTASK);
  }

  @Test
  public void shouldNotCreateInstanceWithUnauthorizedUser(
      @Authenticated(TEST_USER_NAME_NO_PERMISSION) final CamundaClient noPermission) {
    // given (non-admin) user without any authorizations

    // when
    final var response =
        tasklistRestClient
            .withAuthentication(TEST_USER_NAME_NO_PERMISSION, TEST_USER_PASSWORD)
            .createProcessInstance(PROCESS_WITH_USER_TASK);

    // then
    assertThat(response).isNotNull();
    assertThat(response.statusCode()).isEqualTo(403);
  }

  @Test
  public void shouldBeAuthorizedToCreateInstance(
      @Authenticated(ADMIN_USER_NAME) final CamundaClient adminClient,
      @Authenticated(TEST_USER_NAME_WITH_PERMISSION) final CamundaClient withPermission) {
    // given (non-admin) user with authorizations
    final int currentCountOfPIs = countOfProcessInstances(adminClient, PROCESS_WITH_USER_TASK);

    // when
    final var response =
        tasklistRestClient
            .withAuthentication(TEST_USER_NAME_WITH_PERMISSION, TEST_USER_PASSWORD)
            .createProcessInstance(PROCESS_WITH_USER_TASK);

    // then
    assertThat(response).isNotNull();
    assertThat(response.statusCode()).isEqualTo(200);
    ensureProcessInstanceCreated(adminClient, PROCESS_WITH_USER_TASK, currentCountOfPIs + 1);
  }

  @Test
  public void shouldCreateProcessInstanceWithoutAuthentication(
      @Authenticated(ADMIN_USER_NAME) final CamundaClient adminClient,
      @Authenticated(TEST_USER_NAME_NO_PERMISSION) final CamundaClient noPermission) {
    // given (non-admin) user without any authorizations
    final int currentCountOfPIs = countOfProcessInstances(adminClient, PROCESS_WITH_USER_TASK);

    // when
    final var response =
        tasklistRestClient
            .withAuthentication(TEST_USER_NAME_NO_PERMISSION, TEST_USER_PASSWORD)
            .createProcessInstanceViaPublicForm(PROCESS_WITH_USER_TASK);

    // then
    assertThat(response).isNotNull();
    assertThat(response.statusCode()).isEqualTo(200);
    ensureProcessInstanceCreated(adminClient, PROCESS_WITH_USER_TASK, currentCountOfPIs + 1);
  }

  @Test
  public void shouldNotAssignUserTaskWithUnauthorizedUser(
      @Authenticated(ADMIN_USER_NAME) final CamundaClient adminClient,
      @Authenticated(TEST_USER_NAME_NO_PERMISSION) final CamundaClient noPermission) {
    // given (admin) to create instance
    final var processInstanceKey = createProcessInstance(adminClient, PROCESS_WITH_USER_TASK);
    final var userTaskKey = awaitUserTaskBeingAvailable(adminClient, processInstanceKey);
    // given (non-admin) user without any authorizations

    // when
    final var response =
        tasklistRestClient
            .withAuthentication(TEST_USER_NAME_NO_PERMISSION, TEST_USER_PASSWORD)
            .assignUserTask(userTaskKey, TEST_USER_NAME_NO_PERMISSION);

    // then
    assertThat(response).isNotNull();
    assertThat(response.statusCode()).isEqualTo(403);
  }

  @Test
  public void shouldNotCompleteUserTaskWithUnauthorizedUser(
      @Authenticated(ADMIN_USER_NAME) final CamundaClient adminClient,
      @Authenticated(TEST_USER_NAME_NO_PERMISSION) final CamundaClient noPermission) {
    // given (admin) to create instance
    // create a process instance - with pre-assigned user task
    final var processInstanceKey =
        createProcessInstance(adminClient, PROCESS_WITH_USER_TASK_PRE_ASSIGNED);
    final var userTaskKeyPreAssigned = awaitUserTaskBeingAvailable(adminClient, processInstanceKey);
    // given (non-admin) user without any authorizations

    // when
    final var response =
        tasklistRestClient
            .withAuthentication(TEST_USER_NAME_NO_PERMISSION, TEST_USER_PASSWORD)
            .completeUserTask(userTaskKeyPreAssigned);

    // then
    assertThat(response).isNotNull();
    assertThat(response.statusCode()).isEqualTo(403);
  }

  @Test
  public void shouldBeAuthorizedToCompleteUserTask(
      @Authenticated(ADMIN_USER_NAME) final CamundaClient adminClient,
      @Authenticated(TEST_USER_NAME_WITH_PERMISSION) final CamundaClient withPermission) {
    // given (admin) to create instance
    // create a process instance - with pre-assigned user task
    final var processInstanceKey = createProcessInstance(adminClient, PROCESS_WITH_USER_TASK);
    final var userTaskKey = awaitUserTaskBeingAvailable(adminClient, processInstanceKey);
    // given (non-admin) user without any authorizations
    final var response =
        tasklistRestClient
            .withAuthentication(TEST_USER_NAME_WITH_PERMISSION, TEST_USER_PASSWORD)
            .assignUserTask(userTaskKey, TEST_USER_NAME_WITH_PERMISSION);
    assertThat(response.statusCode()).isEqualTo(200);
    ensureUserTaskAssigneeChanged(processInstanceKey, TEST_USER_NAME_WITH_PERMISSION);

    // when
    final var completeResponse =
        tasklistRestClient
            .withAuthentication(TEST_USER_NAME_WITH_PERMISSION, TEST_USER_PASSWORD)
            .completeUserTask(userTaskKey);

    // then
    assertThat(completeResponse).isNotNull();
    assertThat(completeResponse.statusCode()).isEqualTo(200);
    ensureUserTaskIsCompleted(adminClient, userTaskKey);
  }

  @Test
  public void shouldNotUnassignUserTaskWithUnauthorizedUser(
      @Authenticated(ADMIN_USER_NAME) final CamundaClient adminClient,
      @Authenticated(TEST_USER_NAME_NO_PERMISSION) final CamundaClient noPermission) {
    // given (admin) to create instance
    // create a process instance - with pre-assigned user task
    final var processInstanceKey =
        createProcessInstance(adminClient, PROCESS_WITH_USER_TASK_PRE_ASSIGNED);
    final var userTaskKeyPreAssigned = awaitUserTaskBeingAvailable(adminClient, processInstanceKey);
    // given (non-admin) user without any authorizations

    // when
    final var response =
        tasklistRestClient
            .withAuthentication(TEST_USER_NAME_NO_PERMISSION, TEST_USER_PASSWORD)
            .unassignUserTask(userTaskKeyPreAssigned);

    // then
    assertThat(response).isNotNull();
    assertThat(response.statusCode()).isEqualTo(403);
  }

  @Test
  public void shouldBeAuthorizedToAssignUserTask(
      @Authenticated(ADMIN_USER_NAME) final CamundaClient adminClient,
      @Authenticated(TEST_USER_NAME_WITH_PERMISSION) final CamundaClient withPermission) {
    // given (admin) to create instance
    final var processInstanceKeyWithJobBasedUserTask =
        createProcessInstance(adminClient, PROCESS_WITH_USER_TASK);
    final var userTaskKeyWithJobBasedUserTask =
        awaitJobBasedUserTaskBeingAvailable(processInstanceKeyWithJobBasedUserTask);
    // given (non-admin) user with permissions to assign task

    // when
    final var response =
        tasklistRestClient
            .withAuthentication(TEST_USER_NAME_WITH_PERMISSION, TEST_USER_PASSWORD)
            .assignUserTask(userTaskKeyWithJobBasedUserTask, TEST_USER_NAME_WITH_PERMISSION);

    // then
    assertThat(response).isNotNull();
    assertThat(response.statusCode()).isEqualTo(200);
    ensureUserTaskAssigneeChanged(
        processInstanceKeyWithJobBasedUserTask, TEST_USER_NAME_WITH_PERMISSION);
  }

  @Test
  public void shouldBeAuthorizedToUnassignUserTask(
      @Authenticated(ADMIN_USER_NAME) final CamundaClient adminClient,
      @Authenticated(TEST_USER_NAME_WITH_PERMISSION) final CamundaClient withPermission) {
    // given (admin) to create instance
    final var processInstanceKeyWithUserTask =
        createProcessInstance(adminClient, PROCESS_WITH_USER_TASK_PRE_ASSIGNED);
    final var userTaskKey = awaitJobBasedUserTaskBeingAvailable(processInstanceKeyWithUserTask);
    // given (non-admin) user with permissions to unassign task

    // when
    final var response =
        tasklistRestClient
            .withAuthentication(TEST_USER_NAME_WITH_PERMISSION, TEST_USER_PASSWORD)
            .unassignUserTask(userTaskKey);

    // then
    assertThat(response).isNotNull();
    assertThat(response.statusCode()).isEqualTo(200);
    ensureUserTaskIsUnassigned(adminClient, userTaskKey);
  }

  @Test
  public void shouldNotAssignJobBasedUserTaskWithUnauthorizedUser(
      @Authenticated(ADMIN_USER_NAME) final CamundaClient adminClient,
      @Authenticated(TEST_USER_NAME_NO_PERMISSION) final CamundaClient noPermission) {
    // given (admin) to create instance
    // create a process instance with job based user task
    final long anotherProcessInstanceKeyWithJobBasedUserTask =
        createProcessInstance(adminClient, PROCESS_ID_WITH_JOB_BASED_USERTASK);
    final var anotherUserTaskKeyWithJobBasedUserTask =
        awaitJobBasedUserTaskBeingAvailable(anotherProcessInstanceKeyWithJobBasedUserTask);
    // given (non-admin) user without any authorizations

    // when
    final var response =
        tasklistRestClient
            .withAuthentication(TEST_USER_NAME_NO_PERMISSION, TEST_USER_PASSWORD)
            .assignUserTask(anotherUserTaskKeyWithJobBasedUserTask, TEST_USER_NAME_NO_PERMISSION);

    // then
    assertThat(response).isNotNull();
    assertThat(response.statusCode()).isEqualTo(403);
  }

  @Test
  public void shouldNotCompleteJobBasedUserTaskWithUnauthorizedUser(
      @Authenticated(ADMIN_USER_NAME) final CamundaClient adminClient,
      @Authenticated(TEST_USER_NAME_NO_PERMISSION) final CamundaClient noPermission) {
    // given (admin) to create instance
    final var processInstanceKeyWithJobBasedUserTaskPreAssigned =
        createProcessInstance(adminClient, PROCESS_ID_WITH_JOB_BASED_USERTASK_PRE_ASSIGNED);
    final var userTaskKeyWithJobBasedUserTaskPreAssigned =
        awaitJobBasedUserTaskBeingAvailable(processInstanceKeyWithJobBasedUserTaskPreAssigned);
    // given (non-admin) user without any authorizations

    // when
    final var response =
        tasklistRestClient
            .withAuthentication(TEST_USER_NAME_NO_PERMISSION, TEST_USER_PASSWORD)
            .completeUserTask(userTaskKeyWithJobBasedUserTaskPreAssigned);

    // then
    assertThat(response).isNotNull();
    assertThat(response.statusCode()).isEqualTo(403);
  }

  @Test
  public void shouldBeAuthorizedToCompleteJobBasedUserTask(
      @Authenticated(ADMIN_USER_NAME) final CamundaClient adminClient,
      @Authenticated(TEST_USER_NAME_WITH_PERMISSION) final CamundaClient withPermission) {
    // given (admin) to create instance
    final var processInstanceKeyWithJobBasedUserTask =
        createProcessInstance(adminClient, PROCESS_ID_WITH_JOB_BASED_USERTASK);
    final var userTaskKeyWithJobBasedUserTask =
        awaitJobBasedUserTaskBeingAvailable(processInstanceKeyWithJobBasedUserTask);
    // given (non-admin) user with permissions to assign task
    final var response =
        tasklistRestClient
            .withAuthentication(TEST_USER_NAME_WITH_PERMISSION, TEST_USER_PASSWORD)
            .assignUserTask(userTaskKeyWithJobBasedUserTask, TEST_USER_NAME_WITH_PERMISSION);
    assertThat(response.statusCode()).isEqualTo(200);
    ensureUserTaskAssigneeChanged(
        processInstanceKeyWithJobBasedUserTask, TEST_USER_NAME_WITH_PERMISSION);

    // when
    final var completeResponse =
        tasklistRestClient
            .withAuthentication(TEST_USER_NAME_WITH_PERMISSION, TEST_USER_PASSWORD)
            .completeUserTask(userTaskKeyWithJobBasedUserTask);

    // then
    assertThat(completeResponse).isNotNull();
    assertThat(completeResponse.statusCode()).isEqualTo(200);
    ensureJobBasedUserTaskIsCompleted(processInstanceKeyWithJobBasedUserTask);
  }

  @Test
  public void shouldNotUnassignJobBasedUserTaskWithUnauthorizedUser(
      @Authenticated(ADMIN_USER_NAME) final CamundaClient adminClient,
      @Authenticated(TEST_USER_NAME_NO_PERMISSION) final CamundaClient noPermission) {
    // given (admin) to create instance
    final var processInstanceKeyWithJobBasedUserTaskPreAssigned =
        createProcessInstance(adminClient, PROCESS_ID_WITH_JOB_BASED_USERTASK_PRE_ASSIGNED);
    final var userTaskKeyWithJobBasedUserTaskPreAssigned =
        awaitJobBasedUserTaskBeingAvailable(processInstanceKeyWithJobBasedUserTaskPreAssigned);
    // given (non-admin) user without any authorizations

    // when
    final var response =
        tasklistRestClient
            .withAuthentication(TEST_USER_NAME_NO_PERMISSION, TEST_USER_PASSWORD)
            .unassignUserTask(userTaskKeyWithJobBasedUserTaskPreAssigned);

    // then
    assertThat(response).isNotNull();
    assertThat(response.statusCode()).isEqualTo(403);
  }

  @Test
  public void shouldBeAuthorizedToUnassignJobBasedUserTask(
      @Authenticated(ADMIN_USER_NAME) final CamundaClient adminClient,
      @Authenticated(TEST_USER_NAME_WITH_PERMISSION) final CamundaClient withPermission) {
    // given (admin) to create instance
    final var processInstanceKeyWithJobBasedUserTaskPreAssigned =
        createProcessInstance(adminClient, PROCESS_ID_WITH_JOB_BASED_USERTASK_PRE_ASSIGNED);
    final var userTaskKeyWithJobBasedUserTaskPreAssigned =
        awaitJobBasedUserTaskBeingAvailable(processInstanceKeyWithJobBasedUserTaskPreAssigned);
    // given (non-admin) user without any authorizations

    // when
    final var response =
        tasklistRestClient
            .withAuthentication(TEST_USER_NAME_WITH_PERMISSION, TEST_USER_PASSWORD)
            .unassignUserTask(userTaskKeyWithJobBasedUserTaskPreAssigned);

    // then
    assertThat(response).isNotNull();
    assertThat(response.statusCode()).isEqualTo(200);
    ensureJobBasedUserTaskIsUnassigned(processInstanceKeyWithJobBasedUserTaskPreAssigned);
  }

  @Test
  public void shouldBeAuthorizedToAssignJobBasedUserTask(
      @Authenticated(ADMIN_USER_NAME) final CamundaClient adminClient,
      @Authenticated(TEST_USER_NAME_WITH_PERMISSION) final CamundaClient withPermission) {
    // given (admin) to create instance
    final var processInstanceKeyWithJobBasedUserTask =
        createProcessInstance(adminClient, PROCESS_ID_WITH_JOB_BASED_USERTASK);
    final var userTaskKeyWithJobBasedUserTask =
        awaitJobBasedUserTaskBeingAvailable(processInstanceKeyWithJobBasedUserTask);
    // given (non-admin) user with permissions to assign task

    // when
    final var response =
        tasklistRestClient
            .withAuthentication(TEST_USER_NAME_WITH_PERMISSION, TEST_USER_PASSWORD)
            .assignUserTask(userTaskKeyWithJobBasedUserTask, TEST_USER_NAME_WITH_PERMISSION);

    // then
    assertThat(response).isNotNull();
    assertThat(response.statusCode()).isEqualTo(200);
    ensureUserTaskAssigneeChanged(
        processInstanceKeyWithJobBasedUserTask, TEST_USER_NAME_WITH_PERMISSION);
  }

  private static long deployResource(final CamundaClient camundaClient, final String resource) {
    return camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath(resource)
        .send()
        .join()
        .getProcesses()
        .getFirst()
        .getProcessDefinitionKey();
  }

  public static long createProcessInstance(
      final CamundaClient camundaClient, final String processDefinitionId) {
    return camundaClient
        .newCreateInstanceCommand()
        .bpmnProcessId(processDefinitionId)
        .latestVersion()
        .send()
        .join()
        .getProcessInstanceKey();
  }

  public static long awaitUserTaskBeingAvailable(
      final CamundaClient camundaClient, final long processInstanceKey) {
    final AtomicLong userTaskKey = new AtomicLong();
    Awaitility.await("should create an user task")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newUserTaskSearchRequest()
                      .filter(f -> f.processInstanceKey(processInstanceKey))
                      .send()
                      .join();
              assertThat(result.items()).hasSize(1);
              userTaskKey.set(result.items().getFirst().getUserTaskKey());
            });
    return userTaskKey.get();
  }

  public static long awaitJobBasedUserTaskBeingAvailable(final long processInstanceKey) {
    final var task =
        Awaitility.await("should create a job-based user task")
            .atMost(Duration.ofSeconds(60))
            .ignoreExceptions() // Ignore exceptions and continue retrying
            .until(
                () -> findTasksForProcessInstance(processInstanceKey),
                (result) -> result.length == 1);
    return Long.parseLong(task[0].getId());
  }

  public static void ensureUserTaskIsCompleted(
      final CamundaClient camundaClient, final long userTaskKey) {
    Awaitility.await("should create an user task")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newUserTaskSearchRequest()
                      .filter(f -> f.userTaskKey(userTaskKey).state(COMPLETED))
                      .send()
                      .join();
              assertThat(result.items()).hasSize(1);
            });
  }

  public static void ensureUserTaskAssigneeChanged(
      final long processInstanceKey, final String newAssignee) {
    Awaitility.await("should create a job-based user task")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .until(
            () -> findTasksForProcessInstance(processInstanceKey),
            (result) ->
                result.length == 1 && result[0].getAssignee().equalsIgnoreCase(newAssignee));
  }

  public static void ensureJobBasedUserTaskIsCompleted(final long processInstanceKey) {
    Awaitility.await("should complete user task")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .until(
            () -> findTasksForProcessInstance(processInstanceKey),
            (result) -> result.length == 1 && result[0].getCompletionDate() != null);
  }

  public static void ensureJobBasedUserTaskIsUnassigned(final long processInstanceKey) {
    Awaitility.await("should unassign user task")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .until(
            () -> findTasksForProcessInstance(processInstanceKey),
            (result) -> result.length == 1 && result[0].getAssignee() == null);
  }

  private static TaskSearchResponse[] findTasksForProcessInstance(final long processInstanceKey)
      throws JsonProcessingException {
    final HttpResponse<String> response =
        tasklistRestClient
            .withAuthentication(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
            .searchTasks(processInstanceKey);

    assertThat(response).isNotNull();
    assertThat(response.statusCode()).isEqualTo(200);

    return TestRestTasklistClient.OBJECT_MAPPER.readValue(
        response.body(), TaskSearchResponse[].class);
  }

  private static int countOfProcessInstances(
      final CamundaClient adminClient, final String processDefinitionId) {
    return adminClient
        .newProcessInstanceSearchRequest()
        .filter(f -> f.processDefinitionId(processDefinitionId))
        .send()
        .join()
        .items()
        .size();
  }

  private static void ensureProcessInstanceCreated(
      final CamundaClient adminClient, final String processDefinitionId, final int expectedCount) {
    Awaitility.await(
            "should have started process instance with id %s".formatted(processDefinitionId))
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .until(
            () -> countOfProcessInstances(adminClient, processDefinitionId),
            (count) -> count >= expectedCount);
  }

  public static void ensureUserTaskIsUnassigned(
      final CamundaClient adminClient, final long userTaskKey) {
    Awaitility.await("should create an user task")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  adminClient
                      .newUserTaskSearchRequest()
                      .filter(f -> f.userTaskKey(userTaskKey).assignee(c -> c.exists(false)))
                      .send()
                      .join();
              assertThat(result.items()).hasSize(1);
            });
  }
}
