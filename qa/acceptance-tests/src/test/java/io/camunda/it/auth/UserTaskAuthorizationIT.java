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

    // Add USER_TASK[READ] permission for specific task key at runtime
    final var processId1FirstUserTaskKey = getUserTaskKey(adminClient, PROCESS_ID_1);
    createAuthorizationAndWait(
        adminClient,
        USER_WITH_USER_TASK_READ_PROCESS_1_FIRST_TASK,
        USER_TASK,
        READ,
        String.valueOf(processId1FirstUserTaskKey));
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
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER_WITH_PROCESS_DEF_READ_USER_TASK_PROCESS_1)
          final CamundaClient camundaClient) {
    // given
    final var userTaskKey = getUserTaskKey(adminClient, PROCESS_ID_1);
    // when
    final var result = camundaClient.newUserTaskGetRequest(userTaskKey).send().join();
    // then
    assertThat(result).isNotNull();
    assertThat(result.getBpmnProcessId()).isEqualTo(PROCESS_ID_1);
  }

  @Test
  void getByKeyShouldReturnForbiddenForUnauthorizedUserTask(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER_WITH_PROCESS_DEF_READ_USER_TASK_PROCESS_1)
          final CamundaClient camundaClient) {
    // given
    final var userTaskKey = getUserTaskKey(adminClient, PROCESS_ID_2);
    // when
    final ThrowingCallable executeGet =
        () -> camundaClient.newUserTaskGetRequest(userTaskKey).send().join();
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
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER_WITH_PROCESS_DEF_READ_USER_TASK_PROCESS_2)
          final CamundaClient camundaClient) {
    // given
    final var userTaskKey = getUserTaskKey(adminClient, PROCESS_ID_2);
    // when
    final var result = camundaClient.newUserTaskGetFormRequest(userTaskKey).send().join();
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
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER_WITH_PROCESS_DEF_READ_USER_TASK_PROCESS_1)
          final CamundaClient camundaClient) {
    // given
    final var userTaskKey = getUserTaskKey(adminClient, PROCESS_ID_1);
    // when
    final var result = camundaClient.newUserTaskVariableSearchRequest(userTaskKey).send().join();
    // then
    assertThat(result.items()).isNotEmpty();
  }

  @Test
  void searchUserTaskVariablesShouldReturnForbiddenForUnauthorizedUserTask(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER_WITH_PROCESS_DEF_READ_USER_TASK_PROCESS_2)
          final CamundaClient camundaClient) {
    // given
    final var userTaskKey = getUserTaskKey(adminClient, PROCESS_ID_1);
    // when
    final ThrowingCallable executeSearchVariables =
        () -> camundaClient.newUserTaskVariableSearchRequest(userTaskKey).send().join();
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

    // then - return all user tasks created in @BeforeAll:
    //        2 tasks from PROCESS_ID_1 (bpmProcessVariable)
    //        1 task from PROCESS_ID_2 (processWithForm)
    assertThat(result.items()).hasSize(3);
    assertThat(result.items())
        .extracting(UserTask::getBpmnProcessId)
        .containsExactlyInAnyOrder(PROCESS_ID_1, PROCESS_ID_1, PROCESS_ID_2);
  }

  @Test
  void getByKeyShouldReturnUserTaskForWildcardUserTaskReadPermission(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER_WITH_USER_TASK_READ_WILDCARD) final CamundaClient camundaClient) {
    // given - get a task from process 2
    final var userTaskKeyProcess = getUserTaskKey(adminClient, PROCESS_ID_2);
    // when
    final var result = camundaClient.newUserTaskGetRequest(userTaskKeyProcess).send().join();
    // then
    assertThat(result).isNotNull();
    assertThat(result.getBpmnProcessId()).isEqualTo(PROCESS_ID_2);
  }

  @Test
  void searchShouldReturnOnlyAuthorizedUserTaskForSpecificKeyPermission(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER_WITH_USER_TASK_READ_PROCESS_1_FIRST_TASK)
          final CamundaClient camundaClient) {
    // given - user has permission only for the first user task from PROCESS_ID_1

    // when
    final var result = camundaClient.newUserTaskSearchRequest().send().join();

    // then - return only the authorized user task: 1 from PROCESS_ID_1
    assertThat(result.items())
        .hasSize(1)
        .extracting(UserTask::getBpmnProcessId, UserTask::getUserTaskKey)
        .containsExactly(tuple(PROCESS_ID_1, getUserTaskKey(adminClient, PROCESS_ID_1)));
  }

  @Test
  void getByKeyShouldReturnUserTaskForSpecificKeyPermission(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER_WITH_USER_TASK_READ_PROCESS_1_FIRST_TASK)
          final CamundaClient camundaClient) {
    // given - the authorized task key from @BeforeAll
    final var authorizedTaskKey = getUserTaskKey(adminClient, PROCESS_ID_1);

    // when
    final var result = camundaClient.newUserTaskGetRequest(authorizedTaskKey).send().join();

    // then
    assertThat(result).isNotNull();
    assertThat(result.getBpmnProcessId()).isEqualTo(PROCESS_ID_1);
    assertThat(result.getUserTaskKey()).isEqualTo(authorizedTaskKey);
  }

  @Test
  void getByKeyShouldReturnForbiddenForUnauthorizedUserTaskWithSpecificKeyPermission(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER_WITH_USER_TASK_READ_PROCESS_1_FIRST_TASK)
          final CamundaClient camundaClient) {
    // given - an unauthorized task key from PROCESS_ID_2
    final var unauthorizedTaskKey = getUserTaskKey(adminClient, PROCESS_ID_2);

    // when
    final ThrowingCallable executeGet =
        () -> camundaClient.newUserTaskGetRequest(unauthorizedTaskKey).send().join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeGet).actual();
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .isEqualTo(
            "Unauthorized to perform any of the operations: 'READ_USER_TASK' on 'PROCESS_DEFINITION' or 'READ' on 'USER_TASK'");
  }

  private static long getUserTaskKey(final CamundaClient camundaClient, final String processId) {
    return camundaClient
        .newUserTaskSearchRequest()
        .filter(f -> f.bpmnProcessId(processId))
        .send()
        .join()
        .items()
        .getFirst()
        .getUserTaskKey();
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
            () -> {
              final var authorization =
                  adminClient.newAuthorizationGetRequest(authorizationKey).send().join();
              assertThat(authorization).isNotNull();
            });
  }
}
