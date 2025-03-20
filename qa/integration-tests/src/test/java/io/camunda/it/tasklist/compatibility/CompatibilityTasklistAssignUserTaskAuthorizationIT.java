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
import io.camunda.client.protocol.rest.PermissionTypeEnum;
import io.camunda.client.protocol.rest.ResourceTypeEnum;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.User;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.cluster.TestRestTasklistClient;
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
public class CompatibilityTasklistAssignUserTaskAuthorizationIT {

  @MultiDbTestApplication
  private static final TestSimpleCamundaApplication STANDALONE_CAMUNDA =
      new TestSimpleCamundaApplication()
          .withAuthorizationsEnabled()
          .withBasicAuth()
          .withProperty("camunda.tasklist.zeebe.compatibility.enabled", true);

  private static final String PROCESS_ID = "foo";
  private static final String PROCESS_ID_WITH_JOB_BASED_USERTASK =
      "PROCESS_WITH_JOB_BASED_USERTASK";

  private static final String ADMIN_USER_NAME = "foo";
  private static final String ADMIN_USER_PASSWORD = "foo";

  private static final String TEST_USER_NAME_NO_PERMISSION = "noPermissionUser";
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
                  List.of(PROCESS_ID_WITH_JOB_BASED_USERTASK))));

  private static long userTaskKey;
  private static long userTaskKeyWithJobBasedUserTask;
  private static long processInstanceKeyWithJobBasedUserTask;
  private static long anotherUserTaskKeyWithJobBasedUserTask;

  @BeforeAll
  public static void beforeAll(@Authenticated(ADMIN_USER_NAME) final CamundaClient adminClient) {
    tasklistRestClient = STANDALONE_CAMUNDA.newTasklistClient();

    // deploy a process as admin user
    deployResource(adminClient, "process/process_public_start.bpmn");
    // deploy process with a job based user task process
    deployResource(adminClient, "process/process_job_based_user_task.bpmn");

    // create a process instance
    final var processInstanceKey = createProcessInstance(adminClient, PROCESS_ID);
    userTaskKey = awaitUserTaskBeingAvailable(adminClient, processInstanceKey);

    // create a process instance with job based user task
    processInstanceKeyWithJobBasedUserTask =
        createProcessInstance(adminClient, PROCESS_ID_WITH_JOB_BASED_USERTASK);
    userTaskKeyWithJobBasedUserTask =
        awaitJobBasedUserTaskBeingAvailable(processInstanceKeyWithJobBasedUserTask);

    // create a process instance with job based user task
    final long anotherProcessInstanceKeyWithJobBasedUserTask =
        createProcessInstance(adminClient, PROCESS_ID_WITH_JOB_BASED_USERTASK);
    anotherUserTaskKeyWithJobBasedUserTask =
        awaitJobBasedUserTaskBeingAvailable(anotherProcessInstanceKeyWithJobBasedUserTask);
  }

  @Test
  public void shouldNotCreateInstanceWithUnauthorizedUser(
      @Authenticated(TEST_USER_NAME_NO_PERMISSION) final CamundaClient noPermission) {
    // given (non-admin) user without any authorizations

    // when
    final var response =
        tasklistRestClient
            .withAuthentication(TEST_USER_NAME_NO_PERMISSION, TEST_USER_PASSWORD)
            .createProcessInstance(PROCESS_ID);

    // then
    assertThat(response).isNotNull();
    assertThat(response.statusCode()).isEqualTo(403);
  }

  @Test
  public void shouldNotAssignUserTaskWithUnauthorizedUser(
      @Authenticated(TEST_USER_NAME_NO_PERMISSION) final CamundaClient noPermission) {
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
  public void shouldNotAssignJobBasedUserTaskWithUnauthorizedUser(
      @Authenticated(TEST_USER_NAME_NO_PERMISSION) final CamundaClient noPermission) {
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
  public void shouldBeAuthorizedToAssignJobBasedUserTask(
      @Authenticated(ADMIN_USER_NAME) final CamundaClient adminClient,
      @Authenticated(TEST_USER_NAME_WITH_PERMISSION) final CamundaClient withPermission) {
    // given

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
                      .newUserTaskQuery()
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
