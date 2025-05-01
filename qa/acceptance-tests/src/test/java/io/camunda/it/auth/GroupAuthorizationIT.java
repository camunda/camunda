/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.api.search.enums.PermissionType.*;
import static io.camunda.client.api.search.enums.ResourceType.AUTHORIZATION;
import static io.camunda.client.api.search.enums.ResourceType.GROUP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.response.Group;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.User;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.Strings;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class GroupAuthorizationIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  private static final String GROUP_ID_1 = Strings.newRandomValidIdentityId();
  private static final String GROUP_ID_2 = Strings.newRandomValidIdentityId();
  private static final String GROUP_NAME_1 = "AGroupName";
  private static final String GROUP_NAME_2 = "BGroupName";
  private static final String ADMIN = "admin";
  private static final String RESTRICTED = "restrictedUser";
  private static final String RESTRICTED_WITH_READ = "restricteUser2";
  private static final String DEFAULT_PASSWORD = "password";
  private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(15);

  @UserDefinition
  private static final User ADMIN_USER =
      new User(
          ADMIN,
          DEFAULT_PASSWORD,
          List.of(
              new Permissions(GROUP, CREATE, List.of("*")),
              new Permissions(GROUP, UPDATE, List.of("*")),
              new Permissions(GROUP, READ, List.of("*")),
              new Permissions(AUTHORIZATION, UPDATE, List.of("*"))));

  @UserDefinition
  private static final User RESTRICTED_USER = new User(RESTRICTED, DEFAULT_PASSWORD, List.of());

  @UserDefinition
  private static final User RESTRICTED_USER_WITH_READ_PERMISSION =
      new User(
          RESTRICTED_WITH_READ,
          DEFAULT_PASSWORD,
          List.of(new Permissions(GROUP, READ, List.of("*"))));

  @AutoClose private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  @BeforeAll
  static void setUp(@Authenticated(ADMIN) final CamundaClient adminClient) {
    createGroup(adminClient, GROUP_ID_1, GROUP_NAME_1);
    createGroup(adminClient, GROUP_ID_2, GROUP_NAME_2);
    waitForGroupsToBeCreated(adminClient, 2);
  }

  @Test
  void searchShouldReturnAuthorizedGroups(
      @Authenticated(RESTRICTED_WITH_READ) final CamundaClient camundaClient) {
    final var groupSearchResponse = camundaClient.newGroupsSearchRequest().send().join();

    assertThat(groupSearchResponse.items())
        .hasSize(2)
        .map(Group::getGroupId)
        .containsExactlyInAnyOrder(GROUP_ID_1, GROUP_ID_2);
  }

  @Test
  void searchShouldReturnGroupsSortedById(
      @Authenticated(RESTRICTED_WITH_READ) final CamundaClient camundaClient) {
    final var groupSearchResponse =
        camundaClient.newGroupsSearchRequest().sort(s -> s.name().desc()).send().join();

    assertThat(groupSearchResponse.items())
        .hasSize(2)
        .map(Group::getName)
        .containsExactly(GROUP_NAME_2, GROUP_NAME_1);
  }

  @Test
  void searchShouldReturnGroupsFilteredByName(
      @Authenticated(RESTRICTED_WITH_READ) final CamundaClient camundaClient) {
    final var groupSearchResponse =
        camundaClient.newGroupsSearchRequest().filter(fn -> fn.name(GROUP_NAME_2)).send().join();

    assertThat(groupSearchResponse.items())
        .hasSize(1)
        .map(Group::getName)
        .containsExactly(GROUP_NAME_2);
  }

  @Test
  void searchShouldReturnEmptyListWhenUnauthorized(
      @Authenticated(RESTRICTED) final CamundaClient camundaClient) {
    final var groupSearchResponse = camundaClient.newGroupsSearchRequest().send().join();

    assertThat(groupSearchResponse.items()).hasSize(0).map(Group::getName).isEmpty();
  }

  @Test
  void getGroupByIdShouldReturnGroupIfAuthorized(
      @Authenticated(RESTRICTED_WITH_READ) final CamundaClient camundaClient) {
    final var group = camundaClient.newGroupGetRequest(GROUP_ID_1).send().join();

    assertThat(group.getGroupId()).isEqualTo(GROUP_ID_1);
  }

  @Test
  void getGroupByIdShouldReturnNotFoundIfUnauthorized(
      @Authenticated(RESTRICTED) final CamundaClient camundaClient) {
    assertThatThrownBy(() -> camundaClient.newGroupGetRequest(GROUP_ID_1).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("404: 'Not Found'");
  }

  private static void createGroup(
      final CamundaClient adminClient, final String groupId, final String groupName) {
    adminClient.newCreateGroupCommand().groupId(groupId).name(groupName).send().join();
  }

  private static void waitForGroupsToBeCreated(
      final CamundaClient client, final int expectedCount) {
    Awaitility.await("should create groups and import in ES")
        .atMost(AWAIT_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var groupSearchResponse = client.newGroupsSearchRequest().send().join();
              assertThat(groupSearchResponse.items()).hasSize(expectedCount);
            });
  }
}
