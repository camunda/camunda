/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.api.search.enums.PermissionType.CREATE;
import static io.camunda.client.api.search.enums.PermissionType.CREATE_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.EVALUATE;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.ResourceType.EXPRESSION;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.RESOURCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
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

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class ExpressionAuthorizationIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  private static final String AUTHORIZED_USER = "authorizedUser";
  private static final String UNAUTHORIZED_USER = "unauthorizedUser";
  private static final String ADMIN_USER = "adminUser";
  private static final String SCOPE_AUTHORIZED_USER = "scopeAuthorizedUser";
  private static final String SCOPE_UNAUTHORIZED_USER = "scopeUnauthorizedUser";

  private static final String PROCESS_ID = "service_tasks_v1";
  private static final String OTHER_PROCESS_ID = "service_tasks_v2";
  private static final String TASK_ELEMENT_ID = "taskA";

  private static long processInstanceKey;
  private static long elementInstanceKey;

  @UserDefinition
  private static final TestUser AUTHORIZED_USER_DEF =
      new TestUser(
          AUTHORIZED_USER,
          "password",
          List.of(new Permissions(EXPRESSION, EVALUATE, List.of("*"))));

  @UserDefinition
  private static final TestUser UNAUTHORIZED_USER_DEF =
      new TestUser(UNAUTHORIZED_USER, "password", List.of());

  @UserDefinition
  private static final TestUser ADMIN_USER_DEF =
      new TestUser(
          ADMIN_USER,
          "password",
          List.of(
              new Permissions(RESOURCE, CREATE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, CREATE_PROCESS_INSTANCE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_DEFINITION, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of("*"))));

  /** Has EVALUATE + READ_PROCESS_INSTANCE on every process definition. */
  @UserDefinition
  private static final TestUser SCOPE_AUTHORIZED_USER_DEF =
      new TestUser(
          SCOPE_AUTHORIZED_USER,
          "password",
          List.of(
              new Permissions(EXPRESSION, EVALUATE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of("*"))));

  /**
   * Has EVALUATE + READ_PROCESS_INSTANCE only on {@link #OTHER_PROCESS_ID}, not on the process
   * definition the test instance belongs to. Used to verify the authorization is scoped per
   * bpmnProcessId.
   */
  @UserDefinition
  private static final TestUser SCOPE_UNAUTHORIZED_USER_DEF =
      new TestUser(
          SCOPE_UNAUTHORIZED_USER,
          "password",
          List.of(
              new Permissions(EXPRESSION, EVALUATE, List.of("*")),
              new Permissions(
                  PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of(OTHER_PROCESS_ID))));

  @BeforeAll
  static void setUp(@Authenticated(ADMIN_USER) final CamundaClient adminClient) {
    adminClient
        .newDeployResourceCommand()
        .addResourceFromClasspath("process/" + PROCESS_ID + ".bpmn")
        .send()
        .join();

    processInstanceKey =
        adminClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .send()
            .join()
            .getProcessInstanceKey();

    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        adminClient
                            .newElementInstanceSearchRequest()
                            .filter(
                                f ->
                                    f.processInstanceKey(processInstanceKey)
                                        .elementId(TASK_ELEMENT_ID)
                                        .state(ElementInstanceState.ACTIVE))
                            .send()
                            .join()
                            .items())
                    .hasSize(1));

    elementInstanceKey =
        adminClient
            .newElementInstanceSearchRequest()
            .filter(
                f ->
                    f.processInstanceKey(processInstanceKey)
                        .elementId(TASK_ELEMENT_ID)
                        .state(ElementInstanceState.ACTIVE))
            .send()
            .join()
            .items()
            .getFirst()
            .getElementInstanceKey();
  }

  @Test
  void shouldEvaluateExpressionWithEvaluatePermission(
      @Authenticated(AUTHORIZED_USER) final CamundaClient userClient) {
    // when
    final var response =
        userClient.newEvaluateExpressionCommand().expression("=1 + 2").send().join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo(3);
  }

  @Test
  void shouldRejectEvaluateExpressionWithoutEvaluatePermission(
      @Authenticated(UNAUTHORIZED_USER) final CamundaClient userClient) {
    // when / then
    assertThatExceptionOfType(ProblemException.class)
        .isThrownBy(
            () -> userClient.newEvaluateExpressionCommand().expression("=1 + 2").send().join())
        .withMessageContaining("403");
  }

  // ============ PROCESS / ELEMENT INSTANCE CONTEXT AUTHORIZATION ============

  @Test
  void shouldEvaluateExpressionWithProcessInstanceKeyWhenAuthorized(
      @Authenticated(SCOPE_AUTHORIZED_USER) final CamundaClient userClient) {
    // when
    final var response =
        userClient
            .newEvaluateExpressionCommand()
            .expression("=1 + 2")
            .processInstanceKey(processInstanceKey)
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo(3);
  }

  @Test
  void shouldEvaluateExpressionWithElementInstanceKeyWhenAuthorized(
      @Authenticated(SCOPE_AUTHORIZED_USER) final CamundaClient userClient) {
    // when
    final var response =
        userClient
            .newEvaluateExpressionCommand()
            .expression("=1 + 2")
            .elementInstanceKey(elementInstanceKey)
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo(3);
  }

  @Test
  void shouldRejectEvaluateExpressionWithProcessInstanceKeyWithoutReadProcessInstance(
      @Authenticated(AUTHORIZED_USER) final CamundaClient userClient) {
    // given - AUTHORIZED_USER has EVALUATE but no READ_PROCESS_INSTANCE
    // when / then
    assertThatExceptionOfType(ProblemException.class)
        .isThrownBy(
            () ->
                userClient
                    .newEvaluateExpressionCommand()
                    .expression("=1 + 2")
                    .processInstanceKey(processInstanceKey)
                    .send()
                    .join())
        .withMessageContaining("403");
  }

  @Test
  void shouldRejectEvaluateExpressionWithElementInstanceKeyWithoutReadProcessInstance(
      @Authenticated(AUTHORIZED_USER) final CamundaClient userClient) {
    // given - AUTHORIZED_USER has EVALUATE but no READ_PROCESS_INSTANCE
    // when / then
    assertThatExceptionOfType(ProblemException.class)
        .isThrownBy(
            () ->
                userClient
                    .newEvaluateExpressionCommand()
                    .expression("=1 + 2")
                    .elementInstanceKey(elementInstanceKey)
                    .send()
                    .join())
        .withMessageContaining("403");
  }

  @Test
  void shouldRejectEvaluateExpressionWithProcessInstanceKeyWhenScopedToDifferentProcess(
      @Authenticated(SCOPE_UNAUTHORIZED_USER) final CamundaClient userClient) {
    // given - SCOPE_UNAUTHORIZED_USER has READ_PROCESS_INSTANCE only on a different bpmnProcessId
    // when / then
    assertThatExceptionOfType(ProblemException.class)
        .isThrownBy(
            () ->
                userClient
                    .newEvaluateExpressionCommand()
                    .expression("=1 + 2")
                    .processInstanceKey(processInstanceKey)
                    .send()
                    .join())
        .withMessageContaining("403");
  }
}
