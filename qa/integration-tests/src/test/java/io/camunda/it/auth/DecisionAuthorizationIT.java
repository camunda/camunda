/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.protocol.rest.PermissionTypeEnum.CREATE;
import static io.camunda.client.protocol.rest.PermissionTypeEnum.READ;
import static io.camunda.client.protocol.rest.PermissionTypeEnum.READ_DECISION_DEFINITION;
import static io.camunda.client.protocol.rest.ResourceTypeEnum.DECISION_DEFINITION;
import static io.camunda.client.protocol.rest.ResourceTypeEnum.DECISION_REQUIREMENTS_DEFINITION;
import static io.camunda.client.protocol.rest.ResourceTypeEnum.RESOURCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.DeploymentEvent;
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
class DecisionAuthorizationIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  private static final String DECISION_DEFINITION_ID_1 = "decision_1";
  private static final String DECISION_REQUIREMENTS_ID_1 = "definitions_1";
  private static final String DECISION_DEFINITION_ID_2 = "test_qa";
  private static final String DECISION_REQUIREMENTS_ID_2 = "definitions_test";
  private static final String ADMIN = "admin";
  private static final String RESTRICTED = "restrictedUser";

  @UserDefinition
  private static final User ADMIN_USER =
      new User(
          ADMIN,
          "password",
          List.of(
              new Permissions(RESOURCE, CREATE, List.of("*")),
              new Permissions(DECISION_DEFINITION, READ_DECISION_DEFINITION, List.of("*")),
              new Permissions(DECISION_REQUIREMENTS_DEFINITION, READ, List.of("*"))));

  @UserDefinition
  private static final User RESTRICTED_USER =
      new User(
          RESTRICTED,
          "password",
          List.of(
              new Permissions(
                  DECISION_DEFINITION, READ_DECISION_DEFINITION, List.of(DECISION_DEFINITION_ID_1)),
              new Permissions(
                  DECISION_REQUIREMENTS_DEFINITION, READ, List.of(DECISION_REQUIREMENTS_ID_1))));

  @BeforeAll
  static void setUp(@Authenticated(ADMIN) final CamundaClient adminClient) {
    final List<String> decisions = List.of("decision_model.dmn", "decision_model_1.dmn");
    decisions.forEach(
        decision -> deployResource(adminClient, String.format("decisions/%s", decision)));
    waitForDecisionDefinitionsToBeDeployed(adminClient, decisions.size());
    waitForDecisionRequirementsToBeDeployed(adminClient, decisions.size());
  }

  @Test
  void searchShouldReturnAuthorizedDecisionDefinitions(
      @Authenticated(RESTRICTED) final CamundaClient userClient) {
    // when
    final var decisionDefinitions = userClient.newDecisionDefinitionQuery().send().join().items();

    // then
    assertThat(decisionDefinitions).hasSize(1);
    assertThat(decisionDefinitions.stream().map(p -> p.getDmnDecisionId()).toList())
        .containsOnly(DECISION_DEFINITION_ID_1);
  }

  @Test
  void getByKeyShouldReturnForbiddenForUnauthorizedDecisionDefinition(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(RESTRICTED) final CamundaClient userClient) {
    // given
    final var decisionDefinitionKey =
        getDecisionDefinitionKey(adminClient, DECISION_DEFINITION_ID_2);

    // when
    final Executable executeGet =
        () -> userClient.newDecisionDefinitionGetRequest(decisionDefinitionKey).send().join();

    // then
    final var problemException = assertThrows(ProblemException.class, executeGet);
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .isEqualTo(
            "Unauthorized to perform operation 'READ_DECISION_DEFINITION' on resource 'DECISION_DEFINITION'");
  }

  @Test
  void getByKeyShouldReturnAuthorizedDecisionDefinition(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(RESTRICTED) final CamundaClient userClient) {
    // given
    final var decisionDefinitionKey =
        getDecisionDefinitionKey(adminClient, DECISION_DEFINITION_ID_1);

    // when
    final var decisionDefinition =
        userClient.newDecisionDefinitionGetRequest(decisionDefinitionKey).send().join();

    // then
    assertThat(decisionDefinition).isNotNull();
    assertThat(decisionDefinition.getDmnDecisionId()).isEqualTo(DECISION_DEFINITION_ID_1);
  }

  @Test
  void searchShouldReturnAuthorizedDecisionRequirements(
      @Authenticated(RESTRICTED) final CamundaClient userClient) {
    // when
    final var decisionRequirements =
        userClient.newDecisionRequirementsQuery().send().join().items();

    // then
    assertThat(decisionRequirements).hasSize(1);
    assertThat(decisionRequirements.stream().map(p -> p.getDmnDecisionRequirementsId()).toList())
        .containsOnly(DECISION_REQUIREMENTS_ID_1);
  }

  @Test
  void getByKeyShouldReturnForbiddenForUnauthorizedDecisionRequirements(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(RESTRICTED) final CamundaClient userClient) {
    // given
    final var decisionRequirementsKey =
        getDecisionRequirementsKey(adminClient, DECISION_REQUIREMENTS_ID_2);

    // when
    final Executable executeGet =
        () -> userClient.newDecisionRequirementsGetRequest(decisionRequirementsKey).send().join();

    // then
    final var problemException = assertThrows(ProblemException.class, executeGet);
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .isEqualTo(
            "Unauthorized to perform operation 'READ' on resource 'DECISION_REQUIREMENTS_DEFINITION'");
  }

  @Test
  void getByKeyShouldReturnAuthorizedDecisionRequirements(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(RESTRICTED) final CamundaClient userClient) {
    // given
    final var decisionRequirementsKey =
        getDecisionRequirementsKey(adminClient, DECISION_REQUIREMENTS_ID_1);

    // when
    final var decisionRequirements =
        userClient.newDecisionRequirementsGetRequest(decisionRequirementsKey).send().join();

    // then
    assertThat(decisionRequirements).isNotNull();
    assertThat(decisionRequirements.getDmnDecisionRequirementsId())
        .isEqualTo(DECISION_REQUIREMENTS_ID_1);
  }

  private long getDecisionDefinitionKey(
      final CamundaClient client, final String decisionDefinitionId) {
    return client
        .newDecisionDefinitionQuery()
        .filter(f -> f.decisionDefinitionId(decisionDefinitionId))
        .send()
        .join()
        .items()
        .getFirst()
        .getDecisionKey();
  }

  private long getDecisionRequirementsKey(
      final CamundaClient client, final String decisionRequirementsId) {
    return client
        .newDecisionRequirementsQuery()
        .filter(f -> f.decisionRequirementsId(decisionRequirementsId))
        .send()
        .join()
        .items()
        .getFirst()
        .getDecisionRequirementsKey();
  }

  private static DeploymentEvent deployResource(
      final CamundaClient camundaClient, final String resourceName) {
    return camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath(resourceName)
        .send()
        .join();
  }

  private static void waitForDecisionDefinitionsToBeDeployed(
      final CamundaClient camundaClient, final int expectedCount) {
    Awaitility.await("should deploy decision definitions and import in Operate")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = camundaClient.newDecisionDefinitionQuery().send().join();
              assertThat(result.items().size()).isEqualTo(expectedCount);
            });
  }

  private static void waitForDecisionRequirementsToBeDeployed(
      final CamundaClient camundaClient, final int expectedCount) {
    Awaitility.await("should deploy decision requirements and import in Operate")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = camundaClient.newDecisionRequirementsQuery().send().join();
              assertThat(result.items().size()).isEqualTo(expectedCount);
            });
  }
}
