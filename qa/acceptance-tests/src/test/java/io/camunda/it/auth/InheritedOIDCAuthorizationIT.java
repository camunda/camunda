/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker.DEFAULT_MAPPING_CLAIM_NAME;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.qa.util.auth.GroupDefinition;
import io.camunda.qa.util.auth.MappingDefinition;
import io.camunda.qa.util.auth.Membership;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.RoleDefinition;
import io.camunda.qa.util.auth.TestGroup;
import io.camunda.qa.util.auth.TestMapping;
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
public class InheritedOIDCAuthorizationIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withAuthenticationMethod(AuthenticationMethod.OIDC)
          .withAuthorizationsEnabled()
          .withSecurityConfig(c -> c.getAuthorizations().setEnabled(true));

  // Injected by the MultiDbTest extension
  private static KeycloakContainer keycloak;

  @MappingDefinition
  private static final TestMapping MAPPING_THROUGH_AUTHORIZED_GROUP = createTestMapping();

  @MappingDefinition
  private static final TestMapping MAPPING_THROUGH_UNAUTHORIZED_GROUP = createTestMapping();

  @MappingDefinition
  private static final TestMapping MAPPING_THROUGH_AUTHORIZED_ROLE = createTestMapping();

  @MappingDefinition
  private static final TestMapping MAPPING_THROUGH_UNAUTHORIZED_ROLE = createTestMapping();

  @MappingDefinition
  private static final TestMapping MAPPING_THROUGH_GROUP_THROUGH_AUTHORIZED_ROLE =
      createTestMapping();

  @MappingDefinition
  private static final TestMapping MAPPING_THROUGH_GROUP_THROUGH_UNAUTHORIZED_ROLE =
      createTestMapping();

  @GroupDefinition
  private static final TestGroup UNAUTHORIZED_GROUP =
      TestGroup.withoutPermissions(
          "unauthorizedGroup",
          "unauthorized",
          List.of(new Membership(MAPPING_THROUGH_UNAUTHORIZED_GROUP.id(), EntityType.MAPPING)));

  @GroupDefinition
  private static final TestGroup AUTHORIZED_GROUP =
      new TestGroup(
          "authorizedGroup",
          "authorized",
          List.of(
              new Permissions(ResourceType.RESOURCE, PermissionType.CREATE, List.of("*")),
              new Permissions(
                  ResourceType.GROUP, PermissionType.READ, List.of(UNAUTHORIZED_GROUP.id()))),
          List.of(new Membership(MAPPING_THROUGH_AUTHORIZED_GROUP.id(), EntityType.MAPPING)));

  @GroupDefinition
  private static final TestGroup GROUP_THROUGH_AUTHORIZED_ROLE =
      TestGroup.withoutPermissions(
          "authorizedGroupThroughRole",
          "authorized",
          List.of(
              new Membership(
                  MAPPING_THROUGH_GROUP_THROUGH_AUTHORIZED_ROLE.id(), EntityType.MAPPING)));

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
              new Membership(MAPPING_THROUGH_AUTHORIZED_ROLE.id(), EntityType.MAPPING),
              new Membership(GROUP_THROUGH_AUTHORIZED_ROLE.id(), EntityType.GROUP)));

  @GroupDefinition
  private static final TestGroup GROUP_THROUGH_UNAUTHORIZED_ROLE =
      TestGroup.withoutPermissions(
          "unauthorizedGroupThroughRole",
          "unauthorized",
          List.of(
              new Membership(
                  MAPPING_THROUGH_GROUP_THROUGH_UNAUTHORIZED_ROLE.id(), EntityType.MAPPING)));

  @RoleDefinition
  private static final TestRole UNAUTHORIZED_ROLE =
      TestRole.withoutPermissions(
          "unauthorizedRole",
          "unauthorized",
          List.of(
              new Membership(MAPPING_THROUGH_UNAUTHORIZED_ROLE.id(), EntityType.MAPPING),
              new Membership(GROUP_THROUGH_UNAUTHORIZED_ROLE.id(), EntityType.GROUP)));

  @ParameterizedTest
  @MethodSource("provideAuthorizedMappings")
  void shouldBeAuthorizedToDeploy(final TestMapping mapping, @TempDir final Path tempDir) {
    // given
    try (final CamundaClient client = createClient(mapping, tempDir)) {

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
  @MethodSource("provideUnauthorizedMappings")
  void shouldBeUnauthorizedToDeploy(final TestMapping mapping, @TempDir final Path tempDir) {
    // given
    try (final CamundaClient client = createClient(mapping, tempDir)) {

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
  @MethodSource("provideAuthorizedMappings")
  void shouldBeAuthorizedToRead(final TestMapping mapping, @TempDir final Path tempDir) {
    // given
    try (final CamundaClient client = createClient(mapping, tempDir)) {

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
  @MethodSource("provideUnauthorizedMappings")
  void shouldBeUnauthorizedToRead(final TestMapping mapping, @TempDir final Path tempDir) {
    // given
    try (final CamundaClient client = createClient(mapping, tempDir)) {

      // then
      Assertions.assertThatThrownBy(
              () ->
                  client
                      .newGroupGetRequest(
                          UNAUTHORIZED_GROUP.id()) // Attempt to read a random group we've created
                      .send()
                      .join())
          .isInstanceOf(ProblemException.class)
          .hasMessageContaining("Unauthorized to perform operation 'READ' on resource 'GROUP'");
    }
  }

  private CamundaClient createClient(final TestMapping mapping, final Path tempDir) {
    return BROKER
        .newClientBuilder()
        .preferRestOverGrpc(true)
        .credentialsProvider(
            new OAuthCredentialsProviderBuilder()
                .clientId(mapping.claimValue())
                .clientSecret(mapping.claimValue())
                .audience("zeebe")
                .authorizationServerUrl(
                    keycloak.getAuthServerUrl() + "/realms/camunda/protocol/openid-connect/token")
                .credentialsCachePath(tempDir.resolve("default").toString())
                .build())
        .build();
  }

  private static Stream<Named<TestMapping>> provideAuthorizedMappings() {
    return Stream.of(
        Named.of("#userThroughGroup", MAPPING_THROUGH_AUTHORIZED_GROUP),
        Named.of("#userThroughRole", MAPPING_THROUGH_AUTHORIZED_ROLE),
        Named.of("#userThroughGroupThroughRole", MAPPING_THROUGH_GROUP_THROUGH_AUTHORIZED_ROLE));
  }

  private static Stream<Named<TestMapping>> provideUnauthorizedMappings() {
    return Stream.of(
        Named.of("#userThroughGroup", MAPPING_THROUGH_UNAUTHORIZED_GROUP),
        Named.of("#userThroughRole", MAPPING_THROUGH_UNAUTHORIZED_ROLE),
        Named.of("#userThroughGroupThroughRole", MAPPING_THROUGH_GROUP_THROUGH_UNAUTHORIZED_ROLE));
  }

  private static TestMapping createTestMapping() {
    return new TestMapping(
        Strings.newRandomValidIdentityId(),
        DEFAULT_MAPPING_CLAIM_NAME,
        Strings.newRandomValidIdentityId(),
        List.of());
  }
}
