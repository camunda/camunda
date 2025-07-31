/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.operate;

import static io.camunda.client.api.search.enums.PermissionType.READ;
import static io.camunda.client.api.search.enums.PermissionType.READ_DECISION_DEFINITION;
import static io.camunda.client.api.search.enums.PermissionType.READ_DECISION_INSTANCE;
import static io.camunda.client.api.search.enums.ResourceType.DECISION_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.DECISION_REQUIREMENTS_DEFINITION;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.Profile;
import io.camunda.client.CamundaClient;
import io.camunda.qa.util.auth.GroupDefinition;
import io.camunda.qa.util.auth.Membership;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestGroup;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.cluster.TestRestOperateClient;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.test.util.Strings;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.http.HttpStatus;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class OperateV1ApiGroupPermissionsIT {

  @MultiDbTestApplication
  static final TestCamundaApplication STANDALONE_CAMUNDA =
      new TestCamundaApplication()
          .withAuthorizationsEnabled()
          .withBasicAuth()
          .withAdditionalProfile(Profile.OPERATE);

  private static final String GROUP_AUTHORIZED_USERNAME = "operateV1GroupAuthorizedUser";
  private static final String UNAUTHORIZED_USERNAME = "operateV1GroupUnauthorizedUser";
  private static final String DMN_MODEL_RESOURCE =
      String.format("decisions/%s", "decision_model.dmn");
  private static String decisionInstanceId;

  @AutoClose
  private static final TestRestOperateClient GROUP_AUTHORIZED_OPERATE_CLIENT =
      STANDALONE_CAMUNDA.newOperateClient(GROUP_AUTHORIZED_USERNAME, GROUP_AUTHORIZED_USERNAME);

  @AutoClose
  private static final TestRestOperateClient UNAUTHORIZED_OPERATE_CLIENT =
      STANDALONE_CAMUNDA.newOperateClient(UNAUTHORIZED_USERNAME, UNAUTHORIZED_USERNAME);

  @UserDefinition
  private static final TestUser GROUP_AUTHORIZED_USER =
      new TestUser(GROUP_AUTHORIZED_USERNAME, GROUP_AUTHORIZED_USERNAME, List.of());

  @UserDefinition
  private static final TestUser UNAUTHORIZED_USER =
      new TestUser(UNAUTHORIZED_USERNAME, UNAUTHORIZED_USERNAME, List.of());

  private static final String GROUP_ID = Strings.newRandomValidIdentityId();

  @GroupDefinition
  private static final TestGroup AUTHORIZED_GROUP =
      new TestGroup(
          GROUP_ID,
          "operate_v1_auth_group",
          List.of(
              new Permissions(DECISION_DEFINITION, READ_DECISION_DEFINITION, List.of("*")),
              new Permissions(DECISION_DEFINITION, READ_DECISION_INSTANCE, List.of("*")),
              new Permissions(DECISION_REQUIREMENTS_DEFINITION, READ, List.of("*"))),
          List.of(new Membership(GROUP_AUTHORIZED_USERNAME, EntityType.USER)));

  @BeforeAll
  public static void beforeAll(final CamundaClient adminClient) throws Exception {

    final long decisionKey =
        adminClient
            .newDeployResourceCommand()
            .addResourceFromClasspath(DMN_MODEL_RESOURCE)
            .send()
            .join()
            .getDecisions()
            .getFirst()
            .getDecisionKey();
    adminClient
        .newEvaluateDecisionCommand()
        .decisionKey(decisionKey)
        .variables("{\"input1\": \"A\"}")
        .send()
        .join();
    waitForDecisionToBeEvaluated(adminClient);
  }

  @Test
  void shouldBePermittedToSearchDecisionDefinitionUsingV1Api(final CamundaClient client)
      throws Exception {
    final int statusCode =
        GROUP_AUTHORIZED_OPERATE_CLIENT.searchRequest("v1/decision-definitions", "{}").statusCode();
    assertThat(statusCode)
        .describedAs("Is authorized to search decision definitions")
        .isEqualTo(HttpStatus.OK.value());
  }

  @Test
  void shouldBeUnauthorizedToSearchDecisionInstanceUsingV1Api(final CamundaClient client)
      throws Exception {
    final int statusCode =
        UNAUTHORIZED_OPERATE_CLIENT.searchRequest("v1/decision-instances", "{}").statusCode();
    assertThat(statusCode)
        .describedAs("Is unauthorized to search decision instances")
        .isEqualTo(HttpStatus.FORBIDDEN.value());
  }

  @Test
  void shouldBePermittedToSearchDecisionRequirementsUsingV1Api(final CamundaClient client)
      throws Exception {
    final int statusCode =
        GROUP_AUTHORIZED_OPERATE_CLIENT.searchRequest("v1/drd", "{}").statusCode();
    assertThat(statusCode)
        .describedAs("Is authorized to search decision definitions")
        .isEqualTo(HttpStatus.OK.value());
  }

  @Test
  void shouldBeUnauthorizedToGetDecisionRequirementsUsingV1Api() throws Exception {
    final int statusCode = UNAUTHORIZED_OPERATE_CLIENT.getRequest("v1/drd/%s", 1L).statusCode();
    assertThat(statusCode)
        .describedAs("Is unauthorized to get the decision definition")
        .isEqualTo(HttpStatus.FORBIDDEN.value());
  }

  @Test
  void shouldBeUnauthorizedToGetDecisionRequirementsXMLUsingV1Api() throws Exception {
    final int statusCode = UNAUTHORIZED_OPERATE_CLIENT.getRequest("v1/drd/%s/xml", 1L).statusCode();
    assertThat(statusCode)
        .describedAs("Is unauthorized to get the decision definition")
        .isEqualTo(HttpStatus.FORBIDDEN.value());
  }

  private static void waitForDecisionToBeEvaluated(final CamundaClient camundaClient) {
    Awaitility.await("should deploy decision definitions and import in Operate")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = camundaClient.newDecisionInstanceSearchRequest().send().join();
              assertThat(result.items().size()).isEqualTo(1);
              decisionInstanceId = result.items().getFirst().getDecisionInstanceId();
            });
  }
}
