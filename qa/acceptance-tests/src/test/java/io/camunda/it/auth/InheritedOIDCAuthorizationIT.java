/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker.DEFAULT_MAPPING_RULE_CLAIM_NAME;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.qa.util.auth.GroupDefinition;
import io.camunda.qa.util.auth.MappingRuleDefinition;
import io.camunda.qa.util.auth.Membership;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.RoleDefinition;
import io.camunda.qa.util.auth.TestGroup;
import io.camunda.qa.util.auth.TestMappingRule;
import io.camunda.qa.util.auth.TestRole;
import io.camunda.qa.util.multidb.CamundaClientTestFactory;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.api.model.authz.EntityType;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.Strings;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@MultiDbTest(setupKeycloak = true)
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class InheritedOIDCAuthorizationIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withAuthenticationMethod(AuthenticationMethod.OIDC)
          .withAuthorizationsEnabled()
          .withSecurityConfig(c -> c.getAuthorizations().setEnabled(true));

  // Injected by the MultiDbTest extension; physical-tenant-aware, unlike BROKER.newClientBuilder()
  private static CamundaClientTestFactory clientFactory;

  @MappingRuleDefinition
  private static final TestMappingRule MAPPING_RULE_THROUGH_AUTHORIZED_GROUP =
      createTestMappingRule();

  @MappingRuleDefinition
  private static final TestMappingRule MAPPING_RULE_THROUGH_UNAUTHORIZED_GROUP =
      createTestMappingRule();

  @MappingRuleDefinition
  private static final TestMappingRule MAPPING_RULE_THROUGH_AUTHORIZED_ROLE =
      createTestMappingRule();

  @MappingRuleDefinition
  private static final TestMappingRule MAPPING_RULE_THROUGH_UNAUTHORIZED_ROLE =
      createTestMappingRule();

  @MappingRuleDefinition
  private static final TestMappingRule MAPPING_RULE_THROUGH_GROUP_THROUGH_AUTHORIZED_ROLE =
      createTestMappingRule();

  @MappingRuleDefinition
  private static final TestMappingRule MAPPING_RULE_THROUGH_GROUP_THROUGH_UNAUTHORIZED_ROLE =
      createTestMappingRule();

  @GroupDefinition
  private static final TestGroup UNAUTHORIZED_GROUP =
      TestGroup.withoutPermissions(
          "unauthorizedGroup",
          "unauthorized",
          List.of(
              new Membership(
                  MAPPING_RULE_THROUGH_UNAUTHORIZED_GROUP.id(), EntityType.MAPPING_RULE)));

  @GroupDefinition
  private static final TestGroup AUTHORIZED_GROUP =
      new TestGroup(
          "authorizedGroup",
          "authorized",
          List.of(
              new Permissions(ResourceType.RESOURCE, PermissionType.CREATE, List.of("*")),
              new Permissions(
                  ResourceType.GROUP, PermissionType.READ, List.of(UNAUTHORIZED_GROUP.id()))),
          List.of(
              new Membership(MAPPING_RULE_THROUGH_AUTHORIZED_GROUP.id(), EntityType.MAPPING_RULE)));

  @GroupDefinition
  private static final TestGroup GROUP_THROUGH_AUTHORIZED_ROLE =
      TestGroup.withoutPermissions(
          "authorizedGroupThroughRole",
          "authorized",
          List.of(
              new Membership(
                  MAPPING_RULE_THROUGH_GROUP_THROUGH_AUTHORIZED_ROLE.id(),
                  EntityType.MAPPING_RULE)));

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
              new Membership(MAPPING_RULE_THROUGH_AUTHORIZED_ROLE.id(), EntityType.MAPPING_RULE),
              new Membership(GROUP_THROUGH_AUTHORIZED_ROLE.id(), EntityType.GROUP)));

  @GroupDefinition
  private static final TestGroup GROUP_THROUGH_UNAUTHORIZED_ROLE =
      TestGroup.withoutPermissions(
          "unauthorizedGroupThroughRole",
          "unauthorized",
          List.of(
              new Membership(
                  MAPPING_RULE_THROUGH_GROUP_THROUGH_UNAUTHORIZED_ROLE.id(),
                  EntityType.MAPPING_RULE)));

  @RoleDefinition
  private static final TestRole UNAUTHORIZED_ROLE =
      TestRole.withoutPermissions(
          "unauthorizedRole",
          "unauthorized",
          List.of(
              new Membership(MAPPING_RULE_THROUGH_UNAUTHORIZED_ROLE.id(), EntityType.MAPPING_RULE),
              new Membership(GROUP_THROUGH_UNAUTHORIZED_ROLE.id(), EntityType.GROUP)));

  @ParameterizedTest
  @MethodSource("provideAuthorizedMappingRules")
  void shouldBeAuthorizedToDeploy(final TestMappingRule mappingRule) {
    // given
    final CamundaClient client = getClient(mappingRule);

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

  @ParameterizedTest
  @MethodSource("provideUnauthorizedMappingRules")
  void shouldBeUnauthorizedToDeploy(final TestMappingRule mappingRule) {
    // given
    final CamundaClient client = getClient(mappingRule);

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

  @ParameterizedTest
  @MethodSource("provideAuthorizedMappingRules")
  void shouldBeAuthorizedToRead(final TestMappingRule mappingRule) {
    // given
    final CamundaClient client = getClient(mappingRule);

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

  @ParameterizedTest
  @MethodSource("provideUnauthorizedMappingRules")
  void shouldBeUnauthorizedToRead(final TestMappingRule mappingRule) {
    // given
    final CamundaClient client = getClient(mappingRule);

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

  /** Shared client from the {@code MultiDbTest} extension; do not close it. */
  private CamundaClient getClient(final TestMappingRule mappingRule) {
    return clientFactory.getCamundaClient(mappingRule.id());
  }

  private static Stream<Named<TestMappingRule>> provideAuthorizedMappingRules() {
    return Stream.of(
        Named.of("#userThroughGroup", MAPPING_RULE_THROUGH_AUTHORIZED_GROUP),
        Named.of("#userThroughRole", MAPPING_RULE_THROUGH_AUTHORIZED_ROLE),
        Named.of(
            "#userThroughGroupThroughRole", MAPPING_RULE_THROUGH_GROUP_THROUGH_AUTHORIZED_ROLE));
  }

  private static Stream<Named<TestMappingRule>> provideUnauthorizedMappingRules() {
    return Stream.of(
        Named.of("#userThroughGroup", MAPPING_RULE_THROUGH_UNAUTHORIZED_GROUP),
        Named.of("#userThroughRole", MAPPING_RULE_THROUGH_UNAUTHORIZED_ROLE),
        Named.of(
            "#userThroughGroupThroughRole", MAPPING_RULE_THROUGH_GROUP_THROUGH_UNAUTHORIZED_ROLE));
  }

  private static TestMappingRule createTestMappingRule() {
    return new TestMappingRule(
        Strings.newRandomValidIdentityId(),
        DEFAULT_MAPPING_RULE_CLAIM_NAME,
        Strings.newRandomValidIdentityId(),
        List.of());
  }
}
