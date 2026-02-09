/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.identity;

import static io.camunda.client.api.search.enums.PermissionType.CREATE;
import static io.camunda.client.api.search.enums.PermissionType.DELETE;
import static io.camunda.client.api.search.enums.PermissionType.READ;
import static io.camunda.client.api.search.enums.ResourceType.ROLE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.configuration.ConfiguredRole;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class InitializeRoleIT {

  private static final String ADMIN = "admin";
  private static final String DEFAULT_PASSWORD = "password";
  private static final String TEST_CLIENT_ID = "test-client";

  private static final ConfiguredRole CONFIGURED_ROLE_1 =
      new ConfiguredRole(
          "role1",
          "Role 1",
          "something to test",
          List.of(ADMIN),
          List.of(TEST_CLIENT_ID),
          List.of(),
          List.of());

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withAuthorizationsEnabled()
          .withSecurityConfig(
              conf -> conf.getInitialization().setRoles(List.of(CONFIGURED_ROLE_1)));

  @UserDefinition
  private static final TestUser ADMIN_USER =
      new TestUser(
          ADMIN,
          DEFAULT_PASSWORD,
          List.of(
              new Permissions(ROLE, READ, List.of("*")),
              new Permissions(ROLE, CREATE, List.of("*")),
              new Permissions(ROLE, DELETE, List.of("*"))));

  @Test
  void searchShouldReturnRolesFromInitialization(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // given:

    final int defaultRoleCount = 6;

    // when:
    final var response = adminClient.newRolesSearchRequest().send().join();

    // then:
    assertThat(response.items()).hasSize(defaultRoleCount + 1);
    assertThat(response.items())
        .anyMatch(
            actual ->
                actual.getRoleId().equals(CONFIGURED_ROLE_1.roleId())
                    && actual.getName().equals(CONFIGURED_ROLE_1.name())
                    && actual.getDescription().equals(CONFIGURED_ROLE_1.description()));
  }

  @Test
  void searchShouldReturnRoleUsersMembersFromInitialization(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // when:
    final var membersResponse =
        adminClient.newUsersByRoleSearchRequest(CONFIGURED_ROLE_1.roleId()).send().join();

    // then:
    assertThat(membersResponse.items()).hasSize(1);
    assertThat(membersResponse.items()).anyMatch(actual -> actual.getUsername().equals(ADMIN));
  }

  @Test
  void searchShouldReturnRoleClientMembersFromInitialization(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // when:
    final var membersResponse =
        adminClient.newClientsByRoleSearchRequest(CONFIGURED_ROLE_1.roleId()).send().join();

    // then:
    assertThat(membersResponse.items()).hasSize(1);
    assertThat(membersResponse.items())
        .anyMatch(actual -> actual.getClientId().equals(TEST_CLIENT_ID));
  }
}
