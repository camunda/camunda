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
import static io.camunda.client.api.search.enums.ResourceType.GROUP;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.configuration.ConfiguredGroup;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class InitializeGroupIT {

  private static final String ADMIN = "admin";
  private static final String DEFAULT_PASSWORD = "password";
  private static final String TEST_CLIENT_ID = "test-client";

  private static final ConfiguredGroup CONFIGURED_GROUP_1 =
      new ConfiguredGroup(
          "group1",
          "Group 1",
          "something to test",
          List.of(ADMIN),
          List.of(),
          List.of(),
          List.of(TEST_CLIENT_ID));

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withAuthorizationsEnabled()
          .withSecurityConfig(
              conf -> conf.getInitialization().setGroups(List.of(CONFIGURED_GROUP_1)));

  @UserDefinition
  private static final TestUser ADMIN_USER =
      new TestUser(
          ADMIN,
          DEFAULT_PASSWORD,
          List.of(
              new Permissions(GROUP, READ, List.of("*")),
              new Permissions(GROUP, CREATE, List.of("*")),
              new Permissions(GROUP, DELETE, List.of("*"))));

  @Test
  void searchShouldReturnGroupsFromInitialization(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // when:
    final var response = adminClient.newGroupsSearchRequest().send().join();

    // then:
    assertThat(response.items()).hasSizeGreaterThanOrEqualTo(1);
    assertThat(response.items())
        .anyMatch(
            actual ->
                actual.getGroupId().equals(CONFIGURED_GROUP_1.groupId())
                    && actual.getName().equals(CONFIGURED_GROUP_1.name())
                    && actual.getDescription().equals(CONFIGURED_GROUP_1.description()));
  }

  @Test
  void searchShouldReturnGroupUsersMembersFromInitialization(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // when:
    final var membersResponse =
        adminClient.newUsersByGroupSearchRequest(CONFIGURED_GROUP_1.groupId()).send().join();

    // then:
    assertThat(membersResponse.items()).hasSize(1);
    assertThat(membersResponse.items()).anyMatch(actual -> actual.getUsername().equals(ADMIN));
  }

  @Test
  void searchShouldReturnGroupClientMembersFromInitialization(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // when:
    final var membersResponse =
        adminClient.newClientsByGroupSearchRequest(CONFIGURED_GROUP_1.groupId()).send().join();

    // then:
    assertThat(membersResponse.items()).hasSize(1);
    assertThat(membersResponse.items())
        .anyMatch(actual -> actual.getClientId().equals(TEST_CLIENT_ID));
  }
}
