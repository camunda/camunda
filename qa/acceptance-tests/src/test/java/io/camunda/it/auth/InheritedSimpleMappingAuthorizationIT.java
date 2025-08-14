/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.qa.util.auth.ClientDefinition;
import io.camunda.qa.util.auth.GroupDefinition;
import io.camunda.qa.util.auth.Membership;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.RoleDefinition;
import io.camunda.qa.util.auth.TestClient;
import io.camunda.qa.util.auth.TestGroup;
import io.camunda.qa.util.auth.TestRole;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.Strings;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@MultiDbTest(setupKeycloak = true)
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class InheritedSimpleMappingAuthorizationIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withAuthenticationMethod(AuthenticationMethod.OIDC)
          .withAuthorizationsEnabled()
          .withSecurityConfig(c -> c.getAuthorizations().setEnabled(true))
          .withSecurityConfig(c -> c.getAuthentication().getOidc().setClientIdClaim("client_id"))
          .withSecurityConfig(c -> c.getAuthentication().getOidc().setUsernameClaim("no_username"));

  // Injected by the MultiDbTest extension
  private static KeycloakContainer keycloak;

  @ClientDefinition
  private static final TestClient CLIENT_THROUGH_AUTHORIZED_GROUP = createTestClient();

  @ClientDefinition
  private static final TestClient CLIENT_THROUGH_UNAUTHORIZED_GROUP = createTestClient();

  @ClientDefinition
  private static final TestClient CLIENT_THROUGH_AUTHORIZED_ROLE = createTestClient();

  @ClientDefinition
  private static final TestClient CLIENT_THROUGH_UNAUTHORIZED_ROLE = createTestClient();

  @ClientDefinition
  private static final TestClient CLIENT_THROUGH_GROUP_THROUGH_AUTHORIZED_ROLE = createTestClient();

  @ClientDefinition
  private static final TestClient CLIENT_THROUGH_GROUP_THROUGH_UNAUTHORIZED_ROLE =
      createTestClient();

  @GroupDefinition
  private static final TestGroup UNAUTHORIZED_GROUP =
      TestGroup.withoutPermissions(
          "unauthorizedGroup",
          "unauthorized",
          List.of(new Membership(CLIENT_THROUGH_UNAUTHORIZED_GROUP.clientId(), EntityType.CLIENT)));

  @GroupDefinition
  private static final TestGroup AUTHORIZED_GROUP =
      new TestGroup(
          "authorizedGroup",
          "authorized",
          List.of(
              new Permissions(ResourceType.RESOURCE, PermissionType.CREATE, List.of("*")),
              new Permissions(
                  ResourceType.GROUP, PermissionType.READ, List.of(UNAUTHORIZED_GROUP.id()))),
          List.of(new Membership(CLIENT_THROUGH_AUTHORIZED_GROUP.clientId(), EntityType.CLIENT)));

  @GroupDefinition
  private static final TestGroup GROUP_THROUGH_AUTHORIZED_ROLE =
      TestGroup.withoutPermissions(
          "authorizedGroupThroughRole",
          "authorized",
          List.of(
              new Membership(
                  CLIENT_THROUGH_GROUP_THROUGH_AUTHORIZED_ROLE.clientId(), EntityType.CLIENT)));

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
              new Membership(CLIENT_THROUGH_AUTHORIZED_ROLE.clientId(), EntityType.CLIENT),
              new Membership(GROUP_THROUGH_AUTHORIZED_ROLE.id(), EntityType.GROUP)));

  @GroupDefinition
  private static final TestGroup GROUP_THROUGH_UNAUTHORIZED_ROLE =
      TestGroup.withoutPermissions(
          "unauthorizedGroupThroughRole",
          "unauthorized",
          List.of(
              new Membership(
                  CLIENT_THROUGH_GROUP_THROUGH_UNAUTHORIZED_ROLE.clientId(), EntityType.CLIENT)));

  @RoleDefinition
  private static final TestRole UNAUTHORIZED_ROLE =
      TestRole.withoutPermissions(
          "unauthorizedRole",
          "unauthorized",
          List.of(
              new Membership(CLIENT_THROUGH_UNAUTHORIZED_ROLE.clientId(), EntityType.CLIENT),
              new Membership(GROUP_THROUGH_UNAUTHORIZED_ROLE.id(), EntityType.GROUP)));

  @ParameterizedTest
  @MethodSource("provideAuthorizedClients")
  void shouldBeAuthorizedToDeploy(final TestClient testClient, @TempDir final Path tempDir) {
    // given
    try (final CamundaClient client = createClient(testClient.clientId(), tempDir)) {

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
  @MethodSource("provideUnauthorizedClients")
  void shouldBeUnauthorizedToDeploy(final TestClient testClient, @TempDir final Path tempDir) {
    // given
    try (final CamundaClient client = createClient(testClient.clientId(), tempDir)) {

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
  @MethodSource("provideAuthorizedClients")
  void shouldBeAuthorizedToRead(final TestClient testClient, @TempDir final Path tempDir) {
    // given
    try (final CamundaClient client = createClient(testClient.clientId(), tempDir)) {

      // then
      Assertions.assertThatNoException()
          .isThrownBy(
              () ->
                  client
                      .newGroupGetRequest(
                          UNAUTHORIZED_GROUP.id()) /* Attempt to read a random group we've */
                      .send()
                      .join());
    }
  }

  @ParameterizedTest
  @MethodSource("provideUnauthorizedClients")
  void shouldBeUnauthorizedToRead(final TestClient testClient, @TempDir final Path tempDir) {
    // given
    try (final CamundaClient client = createClient(testClient.clientId(), tempDir)) {

      // then
      Assertions.assertThatThrownBy(
              () ->
                  client
                      .newGroupGetRequest(
                          UNAUTHORIZED_GROUP.id()) // Attempt to read a random group we've
                      .send()
                      .join())
          .isInstanceOf(ProblemException.class)
          .hasMessageContaining("403: 'Forbidden'");
    }
  }

  private CamundaClient createClient(final String id, final Path tempDir) {
    return BROKER
        .newClientBuilder()
        .preferRestOverGrpc(true)
        .credentialsProvider(
            new OAuthCredentialsProviderBuilder()
                .clientId(id)
                .clientSecret(id)
                .audience("zeebe")
                .authorizationServerUrl(
                    keycloak.getAuthServerUrl() + "/realms/camunda/protocol/openid-connect/token")
                .credentialsCachePath(tempDir.resolve("default").toString())
                .build())
        .build();
  }

  private static Stream<Named<TestClient>> provideAuthorizedClients() {
    return Stream.of(
        Named.of("#clientThroughGroup", CLIENT_THROUGH_AUTHORIZED_GROUP),
        Named.of("#clientThroughRole", CLIENT_THROUGH_AUTHORIZED_ROLE),
        Named.of("#clientThroughGroupThroughRole", CLIENT_THROUGH_GROUP_THROUGH_AUTHORIZED_ROLE));
  }

  private static Stream<Named<TestClient>> provideUnauthorizedClients() {
    return Stream.of(
        Named.of("#clientThroughGroup", CLIENT_THROUGH_UNAUTHORIZED_GROUP),
        Named.of("#clientThroughRole", CLIENT_THROUGH_UNAUTHORIZED_ROLE),
        Named.of("#clientThroughGroupThroughRole", CLIENT_THROUGH_GROUP_THROUGH_UNAUTHORIZED_ROLE));
  }

  private static TestClient createTestClient() {
    return new TestClient(Strings.newRandomValidIdentityId(), List.of());
  }
}
