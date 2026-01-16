/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.api.search.enums.PermissionType.*;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.RESOURCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
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
  private static final String USER1 = "user1";
  private static final String USER2 = "user2";

  @UserDefinition
  private static final TestUser ADMIN_USER =
      new TestUser(
          ADMIN,
          "password",
          List.of(
              new Permissions(RESOURCE, CREATE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, CREATE_PROCESS_INSTANCE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_DEFINITION, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_USER_TASK, List.of("*"))));

  @UserDefinition
  private static final TestUser USER1_USER =
      new TestUser(
          USER1,
          "password",
          List.of(new Permissions(PROCESS_DEFINITION, READ_USER_TASK, List.of(PROCESS_ID_1))));

  @UserDefinition
  private static final TestUser USER2_USER =
      new TestUser(
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

    waitForProcessAndTasksBeingExported(adminClient);
  }

  @Test
  public void searchShouldReturnAuthorizedUserTasks(
      @Authenticated(USER1) final CamundaClient camundaClient) {
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
      @Authenticated(USER1) final CamundaClient camundaClient) {
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
      @Authenticated(USER2) final CamundaClient camundaClient) {
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

  private long getUserTaskKey(final CamundaClient camundaClient, final String processId) {
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
}
