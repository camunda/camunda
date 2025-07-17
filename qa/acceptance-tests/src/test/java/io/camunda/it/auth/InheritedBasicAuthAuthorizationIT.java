/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.impl.basicauth.BasicAuthCredentialsProviderBuilder;
import io.camunda.qa.util.auth.GroupDefinition;
import io.camunda.qa.util.auth.Membership;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.RoleDefinition;
import io.camunda.qa.util.auth.TestGroup;
import io.camunda.qa.util.auth.TestRole;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.Strings;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class InheritedBasicAuthAuthorizationIT {
  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  @UserDefinition
  private static final TestUser USER_THROUGH_AUTHORIZED_GROUP =
      new TestUser(Strings.newRandomValidUsername(), "password", List.of());

  @UserDefinition
  private static final TestUser USER_THROUGH_UNAUTHORIZED_GROUP =
      new TestUser(Strings.newRandomValidIdentityId(), "password", List.of());

  @UserDefinition
  private static final TestUser USER_THROUGH_AUTHORIZED_ROLE =
      new TestUser(Strings.newRandomValidUsername(), "password", List.of());

  @UserDefinition
  private static final TestUser USER_THROUGH_UNAUTHORIZED_ROLE =
      new TestUser(Strings.newRandomValidIdentityId(), "password", List.of());

  @UserDefinition
  private static final TestUser USER_THROUGH_GROUP_THROUGH_AUTHORIZED_ROLE =
      new TestUser(Strings.newRandomValidUsername(), "password", List.of());

  @UserDefinition
  private static final TestUser USER_THROUGH_GROUP_THROUGH_UNAUTHORIZED_ROLE =
      new TestUser(Strings.newRandomValidUsername(), "password", List.of());

  @GroupDefinition
  private static final TestGroup UNAUTHORIZED_GROUP =
      TestGroup.withoutPermissions(
          "unauthorizedGroup",
          "unauthorized",
          List.of(new Membership(USER_THROUGH_UNAUTHORIZED_GROUP.username(), EntityType.USER)));

  @GroupDefinition
  private static final TestGroup AUTHORIZED_GROUP =
      new TestGroup(
          "authorizedGroup",
          "authorized",
          List.of(
              new Permissions(ResourceType.RESOURCE, PermissionType.CREATE, List.of("*")),
              new Permissions(
                  ResourceType.GROUP, PermissionType.READ, List.of(UNAUTHORIZED_GROUP.id()))),
          List.of(new Membership(USER_THROUGH_AUTHORIZED_GROUP.username(), EntityType.USER)));

  @GroupDefinition
  private static final TestGroup GROUP_THROUGH_AUTHORIZED_ROLE =
      TestGroup.withoutPermissions(
          "authorizedGroupThroughRole",
          "authorized",
          List.of(
              new Membership(
                  USER_THROUGH_GROUP_THROUGH_AUTHORIZED_ROLE.username(), EntityType.USER)));

  @RoleDefinition
  private static final TestRole AUTHORIZED_ROLE =
      new TestRole(
          "authorizedRole",
          "authorized",
          List.of(
              new Permissions(ResourceType.RESOURCE, PermissionType.CREATE, List.of("*")),
              new Permissions(
                  ResourceType.GROUP, PermissionType.READ, List.of(UNAUTHORIZED_GROUP.id()))),
          List.of(
              new Membership(USER_THROUGH_AUTHORIZED_ROLE.username(), EntityType.USER),
              new Membership(GROUP_THROUGH_AUTHORIZED_ROLE.id(), EntityType.GROUP)));

  @GroupDefinition
  private static final TestGroup GROUP_THROUGH_UNAUTHORIZED_ROLE =
      TestGroup.withoutPermissions(
          "unauthorizedGroupThroughRole",
          "unauthorized",
          List.of(
              new Membership(
                  USER_THROUGH_GROUP_THROUGH_UNAUTHORIZED_ROLE.username(), EntityType.USER)));

  @RoleDefinition
  private static final TestRole UNAUTHORIZED_ROLE =
      TestRole.withoutPermissions(
          "unauthorizedRole",
          "unauthorized",
          List.of(
              new Membership(USER_THROUGH_UNAUTHORIZED_ROLE.username(), EntityType.USER),
              new Membership(GROUP_THROUGH_UNAUTHORIZED_ROLE.id(), EntityType.GROUP)));

  @ParameterizedTest
  @MethodSource("provideAuthorizedUsers")
  void shouldBeAuthorizedToDeploy(final TestUser user) {
    // given
    try (final CamundaClient client = createClient(user)) {

      // then
      Assertions.assertThatNoException()
          .isThrownBy(
              () ->
                  client
                      .newDeployResourceCommand()
                      .addProcessModel(
                          Bpmn.createExecutableProcess().startEvent().endEvent().done(),
                          "process.bpmn")
                      .send()
                      .join());
    }
  }

  @ParameterizedTest
  @MethodSource("provideUnauthorizedUsers")
  void shouldBeUnauthorizedToDeploy(final TestUser user) {
    // given
    try (final CamundaClient client = createClient(user)) {

      // then
      Assertions.assertThatThrownBy(
              () ->
                  client
                      .newDeployResourceCommand()
                      .addProcessModel(
                          Bpmn.createExecutableProcess().startEvent().endEvent().done(),
                          "process.bpmn")
                      .send()
                      .join())
          .isInstanceOf(ProblemException.class)
          .hasMessageContaining("403: 'Forbidden'");
    }
  }

  @ParameterizedTest
  @MethodSource("provideAuthorizedUsers")
  void shouldBeAuthorizedToRead(final TestUser user) {
    // given
    try (final CamundaClient client = createClient(user)) {

      // then
      Assertions.assertThatNoException()
          .isThrownBy(
              () ->
                  client
                      .newGroupGetRequest(
                          UNAUTHORIZED_GROUP.id()) // Attempt to read a random group we've created
                      .send()
                      .join());
    }
  }

  @ParameterizedTest
  @MethodSource("provideUnauthorizedUsers")
  void shouldBeUnauthorizedToRead(final TestUser user) {
    // given
    try (final CamundaClient client = createClient(user)) {

      // then
      Assertions.assertThatThrownBy(
              () ->
                  client
                      .newGroupGetRequest(
                          UNAUTHORIZED_GROUP.id()) // Attempt to read a random group we've created
                      .send()
                      .join())
          .isInstanceOf(ProblemException.class)
          .hasMessageContaining("403: 'Forbidden'");
    }
  }

  private static CamundaClient createClient(final TestUser user) {
    return BROKER
        .newClientBuilder()
        .preferRestOverGrpc(true)
        .credentialsProvider(
            new BasicAuthCredentialsProviderBuilder()
                .username(user.username())
                .password(user.password())
                .build())
        .build();
  }

  private static Stream<Named<TestUser>> provideAuthorizedUsers() {
    return Stream.of(
        Named.of("#userThroughGroup", USER_THROUGH_AUTHORIZED_GROUP),
        Named.of("#userThroughRole", USER_THROUGH_AUTHORIZED_ROLE),
        Named.of("#userThroughGroupThroughRole", USER_THROUGH_GROUP_THROUGH_AUTHORIZED_ROLE));
  }

  private static Stream<Named<TestUser>> provideUnauthorizedUsers() {
    return Stream.of(
        Named.of("#userThroughGroup", USER_THROUGH_UNAUTHORIZED_GROUP),
        Named.of("#userThroughRole", USER_THROUGH_UNAUTHORIZED_ROLE),
        Named.of("#userThroughGroupThroughRole", USER_THROUGH_GROUP_THROUGH_UNAUTHORIZED_ROLE));
  }
}
