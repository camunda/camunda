/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.protocol.rest.PermissionTypeEnum.CREATE;
import static io.camunda.client.protocol.rest.PermissionTypeEnum.CREATE_DECISION_INSTANCE;
import static io.camunda.client.protocol.rest.PermissionTypeEnum.READ_DECISION_INSTANCE;
import static io.camunda.client.protocol.rest.ResourceTypeEnum.DECISION_DEFINITION;
import static io.camunda.client.protocol.rest.ResourceTypeEnum.RESOURCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.EvaluateDecisionResponse;
import io.camunda.client.api.search.response.DecisionInstance;
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
class DecisionInstanceAuthorizationIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  private static final String DECISION_DEFINITION_ID_1 = "decision_1";
  private static final String DECISION_DEFINITION_ID_2 = "test_qa";
  private static final String ADMIN = "admin";
  private static final String RESTRICTED = "restrictedUser";

  @UserDefinition
  private static final User ADMIN_USER =
      new User(
          ADMIN,
          "password",
          List.of(
              new Permissions(RESOURCE, CREATE, List.of("*")),
              new Permissions(DECISION_DEFINITION, CREATE_DECISION_INSTANCE, List.of("*")),
              new Permissions(DECISION_DEFINITION, READ_DECISION_INSTANCE, List.of("*"))));

  @UserDefinition
  private static final User RESTRICTED_USER =
      new User(
          RESTRICTED,
          "password",
          List.of(
              new Permissions(
                  DECISION_DEFINITION, READ_DECISION_INSTANCE, List.of(DECISION_DEFINITION_ID_1))));

  @BeforeAll
  static void setUp(@Authenticated(ADMIN) final CamundaClient adminClient) {
    final List<String> decisions = List.of("decision_model.dmn", "decision_model_1.dmn");
    decisions.stream()
        .flatMap(
            decision ->
                deployResource(adminClient, String.format("decisions/%s", decision))
                    .getDecisions()
                    .stream())
        .forEach(decision -> evaluateDecision(adminClient, decision.getDecisionKey()));
    waitForDecisionsToBeEvaluated(adminClient, decisions.size());
  }

  @Test
  void searchShouldReturnAuthorizedDecisionInstances(
      @Authenticated(RESTRICTED) final CamundaClient userClient) {
    // when
    final var decisionInstances =
        userClient.newDecisionInstanceSearchRequest().send().join().items();

    // then
    assertThat(decisionInstances).hasSize(1);
    assertThat(decisionInstances.stream().map(DecisionInstance::getDecisionDefinitionId).toList())
        .containsOnly(DECISION_DEFINITION_ID_1);
  }

  @Test
  void getByIdShouldReturnForbiddenForUnauthorizedDecisionInstance(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(RESTRICTED) final CamundaClient userClient) {
    // given
    final var decisionInstanceId = getDecisionInstanceId(adminClient, DECISION_DEFINITION_ID_2);

    // when
    final Executable executeGet =
        () -> userClient.newDecisionInstanceGetRequest(decisionInstanceId).send().join();

    // then
    final var problemException = assertThrows(ProblemException.class, executeGet);
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .isEqualTo(
            "Unauthorized to perform operation 'READ_DECISION_INSTANCE' on resource 'DECISION_DEFINITION'");
  }

  @Test
  void getByIdShouldReturnAuthorizedDecisionDefinition(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(RESTRICTED) final CamundaClient userClient) {
    // given
    final var decisionInstanceId = getDecisionInstanceId(adminClient, DECISION_DEFINITION_ID_1);

    // when
    final var decisionInstance =
        userClient.newDecisionInstanceGetRequest(decisionInstanceId).send().join();

    // then
    assertThat(decisionInstance).isNotNull();
    assertThat(decisionInstance.getDecisionDefinitionId()).isEqualTo(DECISION_DEFINITION_ID_1);
  }

  private String getDecisionInstanceId(
      final CamundaClient client, final String decisionDefinitionId) {
    return client
        .newDecisionInstanceSearchRequest()
        .filter(f -> f.decisionDefinitionId(decisionDefinitionId))
        .send()
        .join()
        .items()
        .getFirst()
        .getDecisionInstanceId();
  }

  private static DeploymentEvent deployResource(
      final CamundaClient camundaClient, final String resourceName) {
    return camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath(resourceName)
        .send()
        .join();
  }

  private static EvaluateDecisionResponse evaluateDecision(
      final CamundaClient camundaClient, final long decisionKey) {
    return camundaClient
        .newEvaluateDecisionCommand()
        .decisionKey(decisionKey)
        .variables("{\"input1\": \"A\"}")
        .send()
        .join();
  }

  private static void waitForDecisionsToBeEvaluated(
      final CamundaClient camundaClient, final int expectedCount) {
    Awaitility.await("should deploy decision definitions and import in Operate")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = camundaClient.newDecisionInstanceSearchRequest().send().join();
              assertThat(result.items().size()).isEqualTo(expectedCount);
            });
  }
}
