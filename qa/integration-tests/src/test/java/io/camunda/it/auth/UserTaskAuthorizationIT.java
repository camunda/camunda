/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.zeebe.client.protocol.rest.PermissionTypeEnum.CREATE;
import static io.camunda.zeebe.client.protocol.rest.PermissionTypeEnum.READ_USER_TASK;
import static io.camunda.zeebe.client.protocol.rest.ResourceTypeEnum.DEPLOYMENT;
import static io.camunda.zeebe.client.protocol.rest.ResourceTypeEnum.PROCESS_DEFINITION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.application.Profile;
import io.camunda.it.utils.BrokerWithCamundaExporterITInvocationProvider;
import io.camunda.it.utils.ZeebeClientTestFactory.Authenticated;
import io.camunda.it.utils.ZeebeClientTestFactory.Permissions;
import io.camunda.it.utils.ZeebeClientTestFactory.User;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ProblemException;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.function.Executable;

@TestInstance(Lifecycle.PER_CLASS)
class UserTaskAuthorizationIT {
  private static final String PROCESS_ID_1 = "bpmProcessVariable";
  private static final String PROCESS_ID_2 = "processWithForm";
  private static final String ADMIN = "admin";
  private static final String USER1 = "user1";
  private static final String USER2 = "user2";
  private static final User ADMIN_USER =
      new User(
          ADMIN,
          "password",
          List.of(
              new Permissions(DEPLOYMENT, CREATE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, CREATE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_USER_TASK, List.of("*"))));
  private static final User USER1_USER =
      new User(
          USER1,
          "password",
          List.of(new Permissions(PROCESS_DEFINITION, READ_USER_TASK, List.of(PROCESS_ID_1))));
  private static final User USER2_USER =
      new User(
          USER2,
          "password",
          List.of(new Permissions(PROCESS_DEFINITION, READ_USER_TASK, List.of(PROCESS_ID_2))));

  @RegisterExtension
  static final BrokerWithCamundaExporterITInvocationProvider PROVIDER =
      new BrokerWithCamundaExporterITInvocationProvider()
          .withAdditionalProfiles(Profile.AUTH_BASIC)
          .withAuthorizationsEnabled()
          .withUsers(ADMIN_USER, USER1_USER, USER2_USER);

  private boolean initialized;

  @BeforeEach
  void setUp(@Authenticated(ADMIN) final ZeebeClient adminClient) {
    if (!initialized) {
      deployResource(adminClient, "process/bpm_variable_test.bpmn");
      deployResource(adminClient, "form/form.form");
      deployResource(adminClient, "process/process_with_form.bpmn");

      startProcessInstance(adminClient, PROCESS_ID_1);
      startProcessInstance(adminClient, PROCESS_ID_2);

      waitForTasksBeingExported(adminClient, 3);
      initialized = true;
    }
  }

  @TestTemplate
  public void searchShouldReturnAuthorizedUserTasks(
      @Authenticated(USER1) final ZeebeClient zeebeClient) {
    // when
    final var result = zeebeClient.newUserTaskQuery().send().join();

    // then return only user tasks from process with id PROCESS_ID_1
    assertThat(result.items()).hasSize(2);
    assertThat(result.items().getFirst().getBpmnProcessId()).isEqualTo(PROCESS_ID_1);
    assertThat(result.items().getLast().getBpmnProcessId()).isEqualTo(PROCESS_ID_1);
  }

  @TestTemplate
  void getByKeyShouldReturnAuthorizedUserTask(
      @Authenticated(ADMIN) final ZeebeClient adminClient,
      @Authenticated(USER1) final ZeebeClient zeebeClient) {
    // given
    final var userTaskKey = getUserTaskKey(adminClient, PROCESS_ID_1);
    // when
    final var result = zeebeClient.newUserTaskGetRequest(userTaskKey).send().join();
    // then
    assertThat(result).isNotNull();
    assertThat(result.getBpmnProcessId()).isEqualTo(PROCESS_ID_1);
  }

  @TestTemplate
  void getByKeyShouldReturnForbiddenForUnauthorizedUserTask(
      @Authenticated(ADMIN) final ZeebeClient adminClient,
      @Authenticated(USER1) final ZeebeClient zeebeClient) {
    // given
    final var userTaskKey = getUserTaskKey(adminClient, PROCESS_ID_2);
    // when
    final Executable executeGet =
        () -> zeebeClient.newUserTaskGetRequest(userTaskKey).send().join();
    // then
    final var problemException = assertThrows(ProblemException.class, executeGet);
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .isEqualTo(
            "Unauthorized to perform operation 'READ_USER_TASK' on resource 'PROCESS_DEFINITION'");
  }

  @TestTemplate
  void getUserTaskFormShouldReturnFormForAuthorizedUserTask(
      @Authenticated(ADMIN) final ZeebeClient adminClient,
      @Authenticated(USER2) final ZeebeClient zeebeClient) {
    // given
    final var userTaskKey = getUserTaskKey(adminClient, PROCESS_ID_2);
    // when
    final var result = zeebeClient.newUserTaskGetFormRequest(userTaskKey).send().join();
    // then
    assertThat(result).isNotNull();
    assertThat(result.getFormId()).isEqualTo("test");
  }

  @TestTemplate
  void getUserTaskFormShouldReturnForbiddenForUnauthorizedUserTask(
      @Authenticated(ADMIN) final ZeebeClient adminClient,
      @Authenticated(USER1) final ZeebeClient zeebeClient) {
    // given
    final var userTaskKey = getUserTaskKey(adminClient, PROCESS_ID_2);
    // when
    final Executable executeGetForm =
        () -> zeebeClient.newUserTaskGetFormRequest(userTaskKey).send().join();
    // then
    final var problemException = assertThrows(ProblemException.class, executeGetForm);
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .isEqualTo(
            "Unauthorized to perform operation 'READ_USER_TASK' on resource 'PROCESS_DEFINITION'");
  }

  @TestTemplate
  void searchUserTaskVariablesShouldReturnVariablesForAuthorizedUserTask(
      @Authenticated(ADMIN) final ZeebeClient adminClient,
      @Authenticated(USER1) final ZeebeClient zeebeClient) {
    // given
    final var userTaskKey = getUserTaskKey(adminClient, PROCESS_ID_1);
    // when
    final var result = zeebeClient.newUserTaskVariableRequest(userTaskKey).send().join();
    // then
    assertThat(result.items()).isNotEmpty();
  }

  @TestTemplate
  void searchUserTaskVariablesShouldReturnForbiddenForUnauthorizedUserTask(
      @Authenticated(ADMIN) final ZeebeClient adminClient,
      @Authenticated(USER2) final ZeebeClient zeebeClient) {
    // given
    final var userTaskKey = getUserTaskKey(adminClient, PROCESS_ID_1);
    // when
    final Executable executeSearchVariables =
        () -> zeebeClient.newUserTaskVariableRequest(userTaskKey).send().join();
    // then
    final var problemException = assertThrows(ProblemException.class, executeSearchVariables);
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .isEqualTo(
            "Unauthorized to perform operation 'READ_USER_TASK' on resource 'PROCESS_DEFINITION'");
  }

  private long getUserTaskKey(final ZeebeClient zeebeClient, final String processId) {
    return zeebeClient
        .newUserTaskQuery()
        .filter(f -> f.bpmnProcessId(processId))
        .send()
        .join()
        .items()
        .getFirst()
        .getUserTaskKey();
  }

  private void deployResource(final ZeebeClient zeebeClient, final String resourceName) {
    zeebeClient.newDeployResourceCommand().addResourceFromClasspath(resourceName).send().join();
  }

  private void startProcessInstance(final ZeebeClient zeebeClient, final String processId) {
    zeebeClient.newCreateInstanceCommand().bpmnProcessId(processId).latestVersion().send().join();
  }

  private void waitForTasksBeingExported(final ZeebeClient zeebeClient, final int expectedCount) {
    Awaitility.await("should receive data from ES")
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () ->
                assertThat(zeebeClient.newUserTaskQuery().send().join().items())
                    .hasSize(expectedCount));
  }
}
