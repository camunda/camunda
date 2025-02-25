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
import io.camunda.it.utils.BrokerITInvocationProvider;
import io.camunda.it.utils.CamundaClientTestFactory.Authenticated;
import io.camunda.it.utils.CamundaClientTestFactory.Permissions;
import io.camunda.it.utils.CamundaClientTestFactory.User;
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
class DecisionInstanceAuthorizationIT {

  private static final String DECISION_DEFINITION_ID_1 = "decision_1";
  private static final String DECISION_DEFINITION_ID_2 = "test_qa";
  private static final String ADMIN = "admin";
  private static final String RESTRICTED = "restricted-user";
  private static final User ADMIN_USER =
      new User(
          ADMIN,
          "password",
          List.of(
              new Permissions(RESOURCE, CREATE, List.of("*")),
              new Permissions(DECISION_DEFINITION, CREATE_DECISION_INSTANCE, List.of("*")),
              new Permissions(DECISION_DEFINITION, READ_DECISION_INSTANCE, List.of("*"))));
  private static final User RESTRICTED_USER =
      new User(
          RESTRICTED,
          "password",
          List.of(
              new Permissions(
                  DECISION_DEFINITION, READ_DECISION_INSTANCE, List.of(DECISION_DEFINITION_ID_1))));

  @RegisterExtension
  static final BrokerITInvocationProvider PROVIDER =
      new BrokerITInvocationProvider()
          .withoutRdbmsExporter()
          .withBasicAuth()
          .withAuthorizationsEnabled()
          .withUsers(ADMIN_USER, RESTRICTED_USER);

  private boolean initialized;

  @BeforeEach
  void setUp(@Authenticated(ADMIN) final CamundaClient adminClient) {
    if (!initialized) {
      final List<String> decisions = List.of("decision_model.dmn", "decision_model_1.dmn");
      decisions.stream()
          .flatMap(
              decision ->
                  deployResource(adminClient, String.format("decisions/%s", decision))
                      .getDecisions()
                      .stream())
          .forEach(decision -> evaluateDecision(adminClient, decision.getDecisionKey()));
      waitForDecisionsToBeEvaluated(adminClient, decisions.size());
      initialized = true;
    }
  }

  @TestTemplate
  void searchShouldReturnAuthorizedDecisionInstances(
      @Authenticated(RESTRICTED) final CamundaClient userClient) {
    // when
    final var decisionInstances = userClient.newDecisionInstanceQuery().send().join().items();

    // then
    assertThat(decisionInstances).hasSize(1);
    assertThat(decisionInstances.stream().map(DecisionInstance::getDecisionDefinitionId).toList())
        .containsOnly(DECISION_DEFINITION_ID_1);
  }

  @TestTemplate
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

  @TestTemplate
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
        .newDecisionInstanceQuery()
        .filter(f -> f.decisionDefinitionId(decisionDefinitionId))
        .send()
        .join()
        .items()
        .getFirst()
        .getDecisionInstanceId();
  }

  private DeploymentEvent deployResource(
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

  private void waitForDecisionsToBeEvaluated(
      final CamundaClient camundaClient, final int expectedCount) {
    Awaitility.await("should deploy decision definitions and import in Operate")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = camundaClient.newDecisionInstanceQuery().send().join();
              assertThat(result.items().size()).isEqualTo(expectedCount);
            });
  }
}
