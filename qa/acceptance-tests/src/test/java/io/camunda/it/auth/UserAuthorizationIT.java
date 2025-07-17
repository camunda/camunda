/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.CreateUserResponse;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.it.util.TestHelper;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class UserAuthorizationIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  private static final String ADMIN = "admin";
  private static final String RESTRICTED = "restrictedUser";
  private static final String RESTRICTED_WITH_READ = "restrictedUser2";
  private static final String FIRST_USER = "user1";
  private static final String DEFAULT_PASSWORD = "password";

  @UserDefinition
  private static final TestUser ADMIN_USER =
      new TestUser(
          ADMIN,
          DEFAULT_PASSWORD,
          List.of(
              new Permissions(ResourceType.USER, PermissionType.CREATE, List.of("*")),
              new Permissions(ResourceType.USER, PermissionType.UPDATE, List.of("*")),
              new Permissions(ResourceType.USER, PermissionType.READ, List.of("*")),
              new Permissions(ResourceType.USER, PermissionType.DELETE, List.of("*")),
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

  @UserDefinition
  private static final TestUser USER_1 = new TestUser(FIRST_USER, DEFAULT_PASSWORD, List.of());

  private static CamundaClient camundaClient;

  private final List<String> createdUsers = new ArrayList<>();

  @AfterEach
  void awaitExpectedUsers() {
    if (!createdUsers.isEmpty()) {
      Objects.requireNonNull(camundaClient);

      // some tests create/delete users, so we ensure that after each test
      // run, only the expected users exist; this includes waiting for propagation
      // to the secondary storage, i.e. if a test deletes a user, this must
      // be reflected in Elasticsearch before the next test is executed.
      TestHelper.waitUntilExactUsersExist(
          camundaClient, "demo", ADMIN, RESTRICTED, RESTRICTED_WITH_READ, FIRST_USER);

      createdUsers.clear();
    }
  }

  @Test
  void searchShouldReturnAuthorizedUsers(
      @Authenticated(RESTRICTED_WITH_READ) final CamundaClient userClient) {
    // when
    final var result = userClient.newUsersSearchRequest().send().join();

    // then
    assertThat(result.items())
        .map(io.camunda.client.api.search.response.User::getUsername)
        .containsExactly("demo", "admin", "restrictedUser", "restrictedUser2", "user1");
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

  @Test
  void shouldDeleteUserIfAuthorized(@Authenticated(ADMIN) final CamundaClient adminClient) {
    final String username = "username";

    final CreateUserResponse createUserResponse =
        adminClient
            .newCreateUserCommand()
            .username(username)
            .name("name")
            .password("password")
            .email("email@email.com")
            .send()
            .join();

    createdUsers.add(createUserResponse.getUsername());

    assertThatNoException()
        .isThrownBy(() -> adminClient.newDeleteUserCommand(username).send().join());
  }

  @Test
  void deleteUserShouldReturnForbiddenIfUnauthorized(
      @Authenticated(RESTRICTED_WITH_READ) final CamundaClient camundaClient) {
    assertThatThrownBy(() -> camundaClient.newDeleteUserCommand(RESTRICTED).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("403: 'Forbidden'");
  }

  @Test
  void shouldUpdateUserByUsernameIfAuthorized(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    final String updatedName = "updated_name";
    final String updatedEmail = "updated_email@email.com";

    adminClient
        .newUpdateUserCommand(USER_1.username())
        .name(updatedName)
        .email(updatedEmail)
        .send()
        .join();

    Awaitility.await("User is updated")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () ->
                assertThat(
                        adminClient
                            .newUsersSearchRequest()
                            .filter(fn -> fn.username(USER_1.username()))
                            .send()
                            .join()
                            .items()
                            .getFirst())
                    .matches(u -> u.getUsername().equals(USER_1.username()))
                    .matches(u -> u.getEmail().equals(updatedEmail))
                    .matches(u -> u.getName().equals(updatedName)));
  }

  @Test
  void updateUserShouldReturnForbiddenIfUnauthorized(
      @Authenticated(RESTRICTED_WITH_READ) final CamundaClient camundaClient) {
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUpdateUserCommand(USER_1.username())
                    .name("updated_name")
                    .email("updated_email@email.com")
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("403: 'Forbidden'");
  }

  @Test
  void getUserByUsernameShouldReturnNotFoundIfUnauthorized(
      @Authenticated(RESTRICTED) final CamundaClient camundaClient) {
    assertThatThrownBy(() -> camundaClient.newUserGetRequest(USER_1.username()).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("403: 'Forbidden'");
  }

  @Test
  void getUserByUsernameShouldReturnNotFoundForNonExistentUsername(
      @Authenticated(RESTRICTED_WITH_READ) final CamundaClient camundaClient) {
    assertThatThrownBy(
            () -> camundaClient.newUserGetRequest(Strings.newRandomValidIdentityId()).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("404: 'Not Found'");
  }

  @Test
  void getUserByUsernameShouldReturnUserIfAuthorized(
      @Authenticated(RESTRICTED_WITH_READ) final CamundaClient camundaClient) {
    final var user = camundaClient.newUserGetRequest(USER_1.username()).send().join();

    assertThat(user.getUsername()).isEqualTo(USER_1.username());
  }
}
