/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.protocol.rest.PermissionTypeEnum.CREATE;
import static io.camunda.client.protocol.rest.PermissionTypeEnum.CREATE_PROCESS_INSTANCE;
import static io.camunda.client.protocol.rest.PermissionTypeEnum.READ_USER_TASK;
import static io.camunda.client.protocol.rest.ResourceTypeEnum.PROCESS_DEFINITION;
import static io.camunda.client.protocol.rest.ResourceTypeEnum.RESOURCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.User;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.function.Executable;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class UserTaskAuthorizationIT {
  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  private static final String PROCESS_ID_1 = "bpmProcessVariable";
  private static final String PROCESS_ID_2 = "processWithForm";
  private static final String ADMIN = "admin";
  private static final String USER1 = "user1";
  private static final String USER2 = "user2";

  @UserDefinition
  private static final User ADMIN_USER =
      new User(
          ADMIN,
          "password",
          List.of(
              new Permissions(RESOURCE, CREATE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, CREATE_PROCESS_INSTANCE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_USER_TASK, List.of("*"))));

  @UserDefinition
  private static final User USER1_USER =
      new User(
          USER1,
          "password",
          List.of(new Permissions(PROCESS_DEFINITION, READ_USER_TASK, List.of(PROCESS_ID_1))));

  @UserDefinition
  private static final User USER2_USER =
      new User(
          USER2,
          "password",
          List.of(new Permissions(PROCESS_DEFINITION, READ_USER_TASK, List.of(PROCESS_ID_2))));

  @BeforeAll
  static void setUp(@Authenticated(ADMIN) final CamundaClient adminClient) {
    deployResource(adminClient, "process/bpm_variable_test.bpmn");
    deployResource(adminClient, "form/form.form");
    deployResource(adminClient, "process/process_with_form.bpmn");

    startProcessInstance(adminClient, PROCESS_ID_1);
    startProcessInstance(adminClient, PROCESS_ID_2);

    waitForTasksBeingExported(adminClient, 3);
  }

  @Test
  public void searchShouldReturnAuthorizedUserTasks(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newUserTaskQuery().send().join();

    // then return only user tasks from process with id PROCESS_ID_1
    assertThat(result.items()).hasSize(2);
    assertThat(result.items().getFirst().getBpmnProcessId()).isEqualTo(PROCESS_ID_1);
    assertThat(result.items().getLast().getBpmnProcessId()).isEqualTo(PROCESS_ID_1);
  }

  @Test
  void getByKeyShouldReturnAuthorizedUserTask(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER1) final CamundaClient camundaClient) {
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
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // given
    final var userTaskKey = getUserTaskKey(adminClient, PROCESS_ID_2);
    // when
    final Executable executeGet =
        () -> camundaClient.newUserTaskGetRequest(userTaskKey).send().join();
    // then
    final var problemException = assertThrows(ProblemException.class, executeGet);
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .isEqualTo(
            "Unauthorized to perform operation 'READ_USER_TASK' on resource 'PROCESS_DEFINITION'");
  }

  @Test
  void getUserTaskFormShouldReturnFormForAuthorizedUserTask(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER2) final CamundaClient camundaClient) {
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
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // given
    final var userTaskKey = getUserTaskKey(adminClient, PROCESS_ID_2);
    // when
    final Executable executeGetForm =
        () -> camundaClient.newUserTaskGetFormRequest(userTaskKey).send().join();
    // then
    final var problemException = assertThrows(ProblemException.class, executeGetForm);
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .isEqualTo(
            "Unauthorized to perform operation 'READ_USER_TASK' on resource 'PROCESS_DEFINITION'");
  }

  @Test
  void searchUserTaskVariablesShouldReturnVariablesForAuthorizedUserTask(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // given
    final var userTaskKey = getUserTaskKey(adminClient, PROCESS_ID_1);
    // when
    final var result = camundaClient.newUserTaskVariableQuery(userTaskKey).send().join();
    // then
    assertThat(result.items()).isNotEmpty();
  }

  @Test
  void searchUserTaskVariablesShouldReturnForbiddenForUnauthorizedUserTask(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER2) final CamundaClient camundaClient) {
    // given
    final var userTaskKey = getUserTaskKey(adminClient, PROCESS_ID_1);
    // when
    final Executable executeSearchVariables =
        () -> camundaClient.newUserTaskVariableQuery(userTaskKey).send().join();
    // then
    final var problemException = assertThrows(ProblemException.class, executeSearchVariables);
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .isEqualTo(
            "Unauthorized to perform operation 'READ_USER_TASK' on resource 'PROCESS_DEFINITION'");
  }

  private long getUserTaskKey(final CamundaClient camundaClient, final String processId) {
    return camundaClient
        .newUserTaskQuery()
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

  private static void waitForTasksBeingExported(
      final CamundaClient camundaClient, final int expectedCount) {
    Awaitility.await("should receive data from ES")
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () ->
                assertThat(camundaClient.newUserTaskQuery().send().join().items())
                    .hasSize(expectedCount));
  }
}
