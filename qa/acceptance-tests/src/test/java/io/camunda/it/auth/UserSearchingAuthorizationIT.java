/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class UserSearchingAuthorizationIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  private static final String ADMIN = "admin";
  private static final String RESTRICTED = "restrictedUser";
  private static final String RESTRICTED_WITH_READ = "restrictedUser2";
  private static final String DEFAULT_PASSWORD = "password";
  private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(15);

  @UserDefinition
  private static final TestUser ADMIN_USER =
      new TestUser(
          ADMIN,
          DEFAULT_PASSWORD,
          List.of(
              new Permissions(ResourceType.USER, PermissionType.CREATE, List.of("*")),
              new Permissions(ResourceType.USER, PermissionType.UPDATE, List.of("*")),
              new Permissions(ResourceType.USER, PermissionType.READ, List.of("*")),
              new Permissions(ResourceType.AUTHORIZATION, PermissionType.UPDATE, List.of("*"))));

  @UserDefinition
  private static final TestUser RESTRICTED_USER =
      new TestUser(RESTRICTED, DEFAULT_PASSWORD, List.of());

  @UserDefinition
  private static final TestUser RESTRICTED_USER_WITH_READ_PERMISSION =
      new TestUser(
          RESTRICTED_WITH_READ,
          DEFAULT_PASSWORD,
          List.of(new Permissions(ResourceType.USER, PermissionType.READ, List.of("*"))));

  @BeforeAll
  static void setUp(@Authenticated(ADMIN) final CamundaClient adminClient) {
    createUser(adminClient, "user1");
    createUser(adminClient, "user2");

    Awaitility.await("should create users and import in ES")
        .atMost(AWAIT_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var userSearchResponse = adminClient.newUsersSearchRequest().send().join();
              assertThat(
                      userSearchResponse.items().stream()
                          .map(io.camunda.client.api.search.response.User::getUsername)
                          .toList())
                  .containsExactly(
                      "demo", "admin", "restrictedUser", "restrictedUser2", "user1", "user2");
            });
  }

  @Test
  void searchShouldReturnAuthorizedUsers(
      @Authenticated(RESTRICTED_WITH_READ) final CamundaClient userClient) {
    // when
    final var result = userClient.newUsersSearchRequest().send().join();

    // then
    assertThat(result.items())
        .map(io.camunda.client.api.search.response.User::getUsername)
        .containsExactly("demo", "admin", "restrictedUser", "restrictedUser2", "user1", "user2");
  }

  @Test
  void searchShouldReturnOnlyRestrictedUserWhenUnauthorized(
      @Authenticated(RESTRICTED) final CamundaClient userClient) {
    // when
    final var result = userClient.newUsersSearchRequest().send().join();

    // then
    assertThat(result.items())
        .hasSize(1)
        .map(io.camunda.client.api.search.response.User::getUsername)
        .containsExactlyInAnyOrder("restrictedUser");
  }

  private static void createUser(final CamundaClient adminClient, final String username) {
    adminClient
        .newUserCreateCommand()
        .username(username)
        .email(username + "@example.com")
        .password("password")
        .name(UUID.randomUUID().toString())
        .send()
        .join();
  }
}
