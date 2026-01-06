/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.api.search.enums.PermissionType.EVALUATE;
import static io.camunda.client.api.search.enums.ResourceType.EXPRESSION;
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
import java.util.List;
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

  @UserDefinition
  private static final TestUser AUTHORIZED_USER_DEF =
      new TestUser(
          AUTHORIZED_USER,
          "password",
          List.of(new Permissions(EXPRESSION, EVALUATE, List.of("*"))));

  @UserDefinition
  private static final TestUser UNAUTHORIZED_USER_DEF =
      new TestUser(UNAUTHORIZED_USER, "password", List.of());

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
}
