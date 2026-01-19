/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.api.search.enums.PermissionType.*;
import static io.camunda.client.api.search.enums.ResourceType.AUTHORIZATION;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.RESOURCE;
import static io.camunda.client.api.search.enums.ResourceType.USER_TASK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.api.search.response.Authorization;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class UserTaskAuthorizationIT {
  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  private static final String PROCESS_ID_1 = "bpmProcessVariable";
  private static final String PROCESS_ID_2 = "processWithForm";
  private static final String ADMIN = "admin";
  private static final String USER_WITH_PROCESS_DEF_READ_USER_TASK_PROCESS_1 =
      "userWithProcessDefReadUserTaskProcess1";
  private static final String USER_WITH_PROCESS_DEF_READ_USER_TASK_PROCESS_2 =
      "userWithProcessDefReadUserTaskProcess2";
  private static final String USER_WITH_USER_TASK_READ_WILDCARD = "userWithUserTaskReadWildcard";
  private static final String USER_WITH_USER_TASK_READ_PROCESS_1_FIRST_TASK =
      "userWithUserTaskReadProcessId1FirstTask";

  // Task keys captured during setup for reuse in tests
  private static long processId1FirstTaskKey;
  private static long processId1SecondTaskKey;
  private static long processId2TaskKey;

  @UserDefinition
  private static final TestUser ADMIN_USER =
      new TestUser(
          ADMIN,
          "password",
          List.of(
              new Permissions(AUTHORIZATION, CREATE, List.of("*")),
              new Permissions(AUTHORIZATION, READ, List.of("*")),
              new Permissions(RESOURCE, CREATE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, CREATE_PROCESS_INSTANCE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_DEFINITION, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_USER_TASK, List.of("*"))));

  @UserDefinition
  private static final TestUser USER_WITH_PROCESS_DEF_READ_USER_TASK_PROCESS_1_USER =
      new TestUser(
          USER_WITH_PROCESS_DEF_READ_USER_TASK_PROCESS_1,
          "password",
          List.of(new Permissions(PROCESS_DEFINITION, READ_USER_TASK, List.of(PROCESS_ID_1))));

  @UserDefinition
  private static final TestUser USER_WITH_PROCESS_DEF_READ_USER_TASK_PROCESS_2_USER =
      new TestUser(
          USER_WITH_PROCESS_DEF_READ_USER_TASK_PROCESS_2,
          "password",
          List.of(new Permissions(PROCESS_DEFINITION, READ_USER_TASK, List.of(PROCESS_ID_2))));

  @UserDefinition
  private static final TestUser USER_WITH_USER_TASK_READ_WILDCARD_USER =
      new TestUser(
          USER_WITH_USER_TASK_READ_WILDCARD,
          "password",
          List.of(new Permissions(USER_TASK, READ, List.of("*"))));

  @UserDefinition
  private static final TestUser USER_WITH_USER_TASK_READ_PROCESS_1_FIRST_TASK_USER =
      new TestUser(
          USER_WITH_USER_TASK_READ_PROCESS_1_FIRST_TASK,
          "password",
          List.of()); // Empty permissions - will be added dynamically in @BeforeAll

  @BeforeAll
  static void setUp(@Authenticated(ADMIN) final CamundaClient adminClient) {
    deployResource(adminClient, "process/bpm_variable_test.bpmn");
    deployResource(adminClient, "form/form.form");
    deployResource(adminClient, "process/process_with_form.bpmn");

    startProcessInstance(adminClient, PROCESS_ID_1);
    startProcessInstance(adminClient, PROCESS_ID_2);

    waitForProcessAndTasksBeingExported(adminClient);

    // Capture task keys for reuse in tests
    final var process1Tasks = getUserTaskKeys(adminClient, PROCESS_ID_1).toList();
    assertThat(process1Tasks).hasSize(2);
    processId1FirstTaskKey = process1Tasks.getFirst();
    processId1SecondTaskKey = process1Tasks.getLast();
    processId2TaskKey = getUserTaskKey(adminClient, PROCESS_ID_2);

    // Add USER_TASK[READ] permission for specific task key at runtime
    createAuthorizationAndWait(
        adminClient,
        USER_WITH_USER_TASK_READ_PROCESS_1_FIRST_TASK,
        USER_TASK,
        READ,
        String.valueOf(processId1FirstTaskKey));
  }

  @Test
  public void searchShouldReturnAuthorizedUserTasks(
      @Authenticated(USER_WITH_PROCESS_DEF_READ_USER_TASK_PROCESS_1)
          final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newUserTaskSearchRequest().send().join();

    // then return only user tasks from process with id PROCESS_ID_1
    assertThat(result.items()).hasSize(2);
    assertThat(result.items().getFirst().getBpmnProcessId()).isEqualTo(PROCESS_ID_1);
    assertThat(result.items().getLast().getBpmnProcessId()).isEqualTo(PROCESS_ID_1);
  }

  @Test
  void getByKeyShouldReturnAuthorizedUserTask(
      @Authenticated(USER_WITH_PROCESS_DEF_READ_USER_TASK_PROCESS_1)
          final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newUserTaskGetRequest(processId1FirstTaskKey).send().join();
    // then
    assertThat(result).isNotNull();
    assertThat(result.getBpmnProcessId()).isEqualTo(PROCESS_ID_1);
  }

  @Test
  void getByKeyShouldReturnForbiddenForUnauthorizedUserTask(
      @Authenticated(USER_WITH_PROCESS_DEF_READ_USER_TASK_PROCESS_1)
          final CamundaClient camundaClient) {
    // when
    final ThrowingCallable executeGet =
        () -> camundaClient.newUserTaskGetRequest(processId2TaskKey).send().join();
    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeGet).actual();
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .isEqualTo(
            "Unauthorized to perform any of the operations: 'READ_USER_TASK' on 'PROCESS_DEFINITION' or 'READ' on 'USER_TASK'");
  }

  @Test
  void getUserTaskFormShouldReturnFormForAuthorizedUserTask(
      @Authenticated(USER_WITH_PROCESS_DEF_READ_USER_TASK_PROCESS_2)
          final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newUserTaskGetFormRequest(processId2TaskKey).send().join();
    // then
    assertThat(result).isNotNull();
    assertThat(result.getFormId()).isEqualTo("test");
  }

  @Test
  void getUserTaskFormShouldReturnForbiddenForUnauthorizedUserTask(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER_WITH_PROCESS_DEF_READ_USER_TASK_PROCESS_1)
          final CamundaClient camundaClient) {
    // given
    final var userTaskKey = getUserTaskKey(adminClient, PROCESS_ID_2);
    // when
    final ThrowingCallable executeGetForm =
        () -> camundaClient.newUserTaskGetFormRequest(userTaskKey).send().join();
    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeGetForm).actual();
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .isEqualTo(
            "Unauthorized to perform any of the operations: 'READ_USER_TASK' on 'PROCESS_DEFINITION' or 'READ' on 'USER_TASK'");
  }

  @Test
  void searchUserTaskVariablesShouldReturnVariablesForAuthorizedUserTask(
      @Authenticated(USER_WITH_PROCESS_DEF_READ_USER_TASK_PROCESS_1)
          final CamundaClient camundaClient) {
    // when
    final var result =
        camundaClient.newUserTaskVariableSearchRequest(processId1FirstTaskKey).send().join();
    // then
    assertThat(result.items()).isNotEmpty();
  }

  @Test
  void searchUserTaskVariablesShouldReturnForbiddenForUnauthorizedUserTask(
      @Authenticated(USER_WITH_PROCESS_DEF_READ_USER_TASK_PROCESS_2)
          final CamundaClient camundaClient) {
    // when
    final ThrowingCallable executeSearchVariables =
        () -> camundaClient.newUserTaskVariableSearchRequest(processId1FirstTaskKey).send().join();
    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class)
            .isThrownBy(executeSearchVariables)
            .actual();
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .isEqualTo(
            "Unauthorized to perform any of the operations: 'READ_USER_TASK' on 'PROCESS_DEFINITION' or 'READ' on 'USER_TASK'");
  }

  @Test
  void searchShouldReturnAllUserTasksForWildcardUserTaskReadPermission(
      @Authenticated(USER_WITH_USER_TASK_READ_WILDCARD) final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newUserTaskSearchRequest().send().join();

    // then - return all created user tasks
    assertThat(result.items())
        .extracting(UserTask::getBpmnProcessId, UserTask::getUserTaskKey)
        .containsExactlyInAnyOrder(
            tuple(PROCESS_ID_1, processId1FirstTaskKey),
            tuple(PROCESS_ID_1, processId1SecondTaskKey),
            tuple(PROCESS_ID_2, processId2TaskKey));
  }

  @Test
  void getByKeyShouldReturnUserTaskForWildcardUserTaskReadPermission(
      @Authenticated(USER_WITH_USER_TASK_READ_WILDCARD) final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newUserTaskGetRequest(processId1SecondTaskKey).send().join();
    // then
    assertThat(result).isNotNull();
    assertThat(result.getBpmnProcessId()).isEqualTo(PROCESS_ID_1);
  }

  @Test
  void searchShouldReturnOnlyAuthorizedUserTaskForSpecificKeyPermission(
      @Authenticated(USER_WITH_USER_TASK_READ_PROCESS_1_FIRST_TASK)
          final CamundaClient camundaClient) {
    // given - user has permission only for the first user task from PROCESS_ID_1

    // when
    final var result = camundaClient.newUserTaskSearchRequest().send().join();

    // then - return only the authorized user task: 1 from PROCESS_ID_1
    assertThat(result.items())
        .extracting(UserTask::getBpmnProcessId, UserTask::getUserTaskKey)
        .containsExactly(tuple(PROCESS_ID_1, processId1FirstTaskKey));
  }

  @Test
  void getByKeyShouldReturnUserTaskForSpecificKeyPermission(
      @Authenticated(USER_WITH_USER_TASK_READ_PROCESS_1_FIRST_TASK)
          final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newUserTaskGetRequest(processId1FirstTaskKey).send().join();

    // then
    assertThat(result).isNotNull();
    assertThat(result.getBpmnProcessId()).isEqualTo(PROCESS_ID_1);
    assertThat(result.getUserTaskKey()).isEqualTo(processId1FirstTaskKey);
  }

  @Test
  void getByKeyShouldReturnForbiddenForUnauthorizedUserTaskWithSpecificKeyPermission(
      @Authenticated(USER_WITH_USER_TASK_READ_PROCESS_1_FIRST_TASK)
          final CamundaClient camundaClient) {
    // when - try to access unauthorized task from PROCESS_ID_2
    final ThrowingCallable executeGet =
        () -> camundaClient.newUserTaskGetRequest(processId2TaskKey).send().join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeGet).actual();
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .isEqualTo(
            "Unauthorized to perform any of the operations: 'READ_USER_TASK' on 'PROCESS_DEFINITION' or 'READ' on 'USER_TASK'");
  }

  private static long getUserTaskKey(final CamundaClient camundaClient, final String processId) {
    return getUserTaskKeys(camundaClient, processId).findFirst().orElseThrow();
  }

  private static Stream<Long> getUserTaskKeys(
      final CamundaClient camundaClient, final String processId) {
    return camundaClient
        .newUserTaskSearchRequest()
        .filter(f -> f.bpmnProcessId(processId))
        .send()
        .join()
        .items()
        .stream()
        .map(UserTask::getUserTaskKey);
  }

  private static void deployResource(final CamundaClient camundaClient, final String resourceName) {
    camundaClient.newDeployResourceCommand().addResourceFromClasspath(resourceName).send().join();
  }

  private static void startProcessInstance(
      final CamundaClient camundaClient, final String processId) {
    camundaClient.newCreateInstanceCommand().bpmnProcessId(processId).latestVersion().send().join();
  }

  private static void waitForProcessAndTasksBeingExported(final CamundaClient camundaClient) {
    Awaitility.await("should receive data from ES")
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              assertThat(
                      camundaClient
                          .newProcessDefinitionSearchRequest()
                          .filter(filter -> filter.processDefinitionId(PROCESS_ID_1))
                          .send()
                          .join()
                          .items())
                  .hasSize(1);
              assertThat(
                      camundaClient
                          .newProcessDefinitionSearchRequest()
                          .filter(filter -> filter.processDefinitionId(PROCESS_ID_2))
                          .send()
                          .join()
                          .items())
                  .hasSize(1);
              assertThat(
                      camundaClient
                          .newProcessInstanceSearchRequest()
                          .filter(
                              filter ->
                                  filter.processDefinitionId(
                                      fn -> fn.in(PROCESS_ID_1, PROCESS_ID_2)))
                          .send()
                          .join()
                          .items())
                  .hasSize(2);
              assertThat(camundaClient.newUserTaskSearchRequest().send().join().items()).hasSize(3);
            });
  }

  /**
   * Creates an authorization for a user and waits until it's visible in the system.
   *
   * @param adminClient the admin client to use for creating the authorization
   * @param username the username (owner ID) to grant the permission to
   * @param resourceType the resource type (e.g., USER_TASK, PROCESS_DEFINITION)
   * @param permissionType the permission type (e.g., READ, UPDATE)
   * @param resourceId the resource ID to grant permission for
   */
  private static void createAuthorizationAndWait(
      final CamundaClient adminClient,
      final String username,
      final ResourceType resourceType,
      final PermissionType permissionType,
      final String resourceId) {
    final long authorizationKey =
        adminClient
            .newCreateAuthorizationCommand()
            .ownerId(username)
            .ownerType(OwnerType.USER)
            .resourceId(resourceId)
            .resourceType(resourceType)
            .permissionTypes(permissionType)
            .send()
            .join()
            .getAuthorizationKey();

    Awaitility.await("authorization with key '" + authorizationKey + "' to be created")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                // Use `newAuthorizationSearchRequest` instead of `newAuthorizationGetRequest`
                // to ensure authorization is indexed and searchable.
                // Get request may return the authorization before it's available in search index,
                // causing test flakiness when tests subsequently search for user tasks.
                assertThat(
                        adminClient
                            .newAuthorizationSearchRequest()
                            .filter(
                                f ->
                                    f.resourceIds(resourceId)
                                        .resourceType(resourceType)
                                        .ownerId(username))
                            .execute()
                            .items())
                    .extracting(Authorization::getAuthorizationKey)
                    .contains(String.valueOf(authorizationKey)));
  }
}
