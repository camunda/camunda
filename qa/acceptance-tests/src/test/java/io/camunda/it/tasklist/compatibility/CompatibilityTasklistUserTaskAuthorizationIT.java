/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.tasklist.compatibility;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.cluster.TestRestTasklistClient;
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
@DisabledIfSystemProperty(
    named = "test.integration.camunda.data.secondary-storage.type",
    matches = "rdbms")
@DisabledIfSystemProperty(
    named = "test.integration.camunda.data.secondary-storage.type",
    matches = "AWS_OS")
public class CompatibilityTasklistUserTaskAuthorizationIT {

  @MultiDbTestApplication
  private static final TestCamundaApplication STANDALONE_CAMUNDA =
      new TestCamundaApplication()
          .withAuthorizationsEnabled()
          .withBasicAuth()
          .withProperty("camunda.tasklist.zeebe.compatibility.enabled", true);

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
  private static final TestUser ADMIN_USER =
      new TestUser(
          ADMIN_USER_NAME,
          ADMIN_USER_PASSWORD,
          List.of(
              new Permissions(ResourceType.RESOURCE, PermissionType.CREATE, List.of("*")),
              new Permissions(ResourceType.AUTHORIZATION, PermissionType.CREATE, List.of("*")),
              new Permissions(
                  ResourceType.PROCESS_DEFINITION,
                  PermissionType.READ_PROCESS_DEFINITION,
                  List.of("*")),
              new Permissions(
                  ResourceType.PROCESS_DEFINITION, PermissionType.READ_USER_TASK, List.of("*")),
              new Permissions(
                  ResourceType.PROCESS_DEFINITION,
                  PermissionType.READ_PROCESS_INSTANCE,
                  List.of("*")),
              new Permissions(
                  ResourceType.PROCESS_DEFINITION,
                  PermissionType.CREATE_PROCESS_INSTANCE,
                  List.of("*")),
              new Permissions(ResourceType.USER, PermissionType.CREATE, List.of("*")),
              new Permissions(ResourceType.AUTHORIZATION, PermissionType.UPDATE, List.of("*"))));

  @UserDefinition
  private static final TestUser TEST_USER_NO_PERMISSIONS =
      new TestUser(TEST_USER_NAME_NO_PERMISSION, TEST_USER_PASSWORD, List.of());

  @UserDefinition
  private static final TestUser TEST_USER_WITH_PERMISSIONS =
      new TestUser(
          TEST_USER_NAME_WITH_PERMISSION,
          TEST_USER_PASSWORD,
          List.of(
              new Permissions(
                  ResourceType.PROCESS_DEFINITION,
                  PermissionType.UPDATE_USER_TASK,
                  List.of(PROCESS_ID_WITH_JOB_BASED_USERTASK))));

  @BeforeAll
  public static void beforeAll(@Authenticated(ADMIN_USER_NAME) final CamundaClient adminClient) {
    tasklistRestClient = STANDALONE_CAMUNDA.newTasklistClient();

    // deploy a process as admin user
    deployResource(adminClient, "process/process_public_start.bpmn");
    deployResource(adminClient, "process/process_with_assigned_user_task.bpmn");
    // deploy process with a job based user task process
    deployResource(adminClient, "process/process_job_based_user_task.bpmn");
    deployResource(adminClient, "process/process_with_assigned_job_based_user_task.bpmn");
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
    ensureJobBasedUserTaskAssigneeChanged(
        processInstanceKeyWithJobBasedUserTask, TEST_USER_NAME_WITH_PERMISSION);
  }

  private static void deployResource(final CamundaClient camundaClient, final String resource) {
    camundaClient.newDeployResourceCommand().addResourceFromClasspath(resource).send().join();
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
                () -> {
                  final HttpResponse<String> response =
                      tasklistRestClient
                          .withAuthentication(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                          .searchTasks(processInstanceKey);

                  assertThat(response).isNotNull();
                  assertThat(response.statusCode()).isEqualTo(200);

                  return TestRestTasklistClient.OBJECT_MAPPER.readValue(
                      response.body(), TaskSearchResponse[].class);
                },
                (result) -> result.length == 1);
    return Long.parseLong(task[0].getId());
  }

  public static void ensureJobBasedUserTaskAssigneeChanged(
      final long processInstanceKey, final String newAssignee) {
    Awaitility.await("should create a job-based user task")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .until(
            () -> {
              final HttpResponse<String> response =
                  tasklistRestClient
                      .withAuthentication(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                      .searchTasks(processInstanceKey);

              assertThat(response).isNotNull();
              assertThat(response.statusCode()).isEqualTo(200);

              return TestRestTasklistClient.OBJECT_MAPPER.readValue(
                  response.body(), TaskSearchResponse[].class);
            },
            (result) ->
                result.length == 1 && result[0].getAssignee().equalsIgnoreCase(newAssignee));
  }
}
