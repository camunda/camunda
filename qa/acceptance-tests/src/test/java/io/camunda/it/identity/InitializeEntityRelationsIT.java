/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.identity;

import static io.camunda.client.api.search.enums.PermissionType.READ;
import static io.camunda.client.api.search.enums.ResourceType.GROUP;
import static io.camunda.client.api.search.enums.ResourceType.ROLE;
import static io.camunda.client.api.search.enums.ResourceType.TENANT;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.configuration.ConfiguredGroup;
import io.camunda.security.configuration.ConfiguredRole;
import io.camunda.security.configuration.ConfiguredTenant;
import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class InitializeEntityRelationsIT {

  private static final String DEFAULT_ROLE_CONNECTORS = "connectors";
  private static final String TEST_ROLE_ID = "role1";
  private static final String TEST_TENANT_ID = "tenant1";
  private static final String USERNAME_ADMIN = "admin";
  private static final String DEFAULT_ROLE_ADMIN = "admin";
  private static final String DEFAULT_PASSWORD = "password";
  private static final String TEST_USERNAME = "test-user";
  private static final String TEST_GROUP_ID = "group1";
  private static final String TEST_CLIENT_ID = "test-client";
  private static final ConfiguredGroup CONFIGURED_GROUP_1 =
      new ConfiguredGroup(
          TEST_GROUP_ID,
          "Group 1",
          "something to test",
          List.of(),
          List.of(DEFAULT_ROLE_ADMIN, DEFAULT_ROLE_CONNECTORS),
          List.of(),
          List.of());
  private static final ConfiguredRole CONFIGURED_ROLE_1 =
      new ConfiguredRole(
          TEST_ROLE_ID,
          "Role 1",
          "something to test",
          List.of(),
          List.of(),
          List.of(),
          List.of(TEST_GROUP_ID));
  private static final ConfiguredTenant CONFIGURED_TENANT_1 =
      new ConfiguredTenant(
          TEST_TENANT_ID,
          "Tenant 1",
          "something to test",
          List.of(),
          List.of(),
          List.of(TEST_ROLE_ID),
          List.of(TEST_GROUP_ID),
          List.of());

  private static final ConfiguredUser CONFIGURED_USER =
      new ConfiguredUser("test-user", DEFAULT_PASSWORD, "test-user", "");

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withAuthorizationsEnabled()
          .withSecurityConfig(
              conf -> {
                conf.getInitialization().getGroups().add(CONFIGURED_GROUP_1);
                conf.getInitialization().getUsers().add(CONFIGURED_USER);
                conf.getInitialization().getRoles().add(CONFIGURED_ROLE_1);
                conf.getInitialization().getTenants().add(CONFIGURED_TENANT_1);
              });

  @UserDefinition
  private static final TestUser ADMIN_USER =
      new TestUser(
          USERNAME_ADMIN,
          DEFAULT_PASSWORD,
          List.of(
              new Permissions(GROUP, READ, List.of("*")),
              new Permissions(ROLE, READ, List.of("*")),
              new Permissions(TENANT, READ, List.of("*"))));

  @Test
  void shouldMakeRoleGroupRelations(
      @Authenticated(USERNAME_ADMIN) final CamundaClient adminClient) {
    // when:
    final var response = adminClient.newRolesByGroupSearchRequest(TEST_GROUP_ID).send().join();

    // then:
    assertThat(response.items()).hasSize(3);
    assertThat(response.items()).anyMatch(actual -> actual.getRoleId().equals(DEFAULT_ROLE_ADMIN));
    assertThat(response.items())
        .anyMatch(actual -> actual.getRoleId().equals(DEFAULT_ROLE_CONNECTORS));
    assertThat(response.items()).anyMatch(actual -> actual.getRoleId().equals(TEST_ROLE_ID));
  }

  @Test
  void shouldMakeTenantRoleRelations(
      @Authenticated(USERNAME_ADMIN) final CamundaClient adminClient) {
    // when:
    final var response = adminClient.newRolesByTenantSearchRequest(TEST_TENANT_ID).send().join();

    // then:
    assertThat(response.items()).hasSize(1);
    assertThat(response.items()).anyMatch(actual -> actual.getRoleId().equals(TEST_ROLE_ID));
  }

  @Test
  void shouldMakeTenantGroupRelations(
      @Authenticated(USERNAME_ADMIN) final CamundaClient adminClient) {
    // when:
    final var response = adminClient.newGroupsByTenantSearchRequest(TEST_TENANT_ID).send().join();

    // then:
    assertThat(response.items()).hasSize(1);
    assertThat(response.items()).anyMatch(actual -> actual.getGroupId().equals(TEST_GROUP_ID));
  }
}
