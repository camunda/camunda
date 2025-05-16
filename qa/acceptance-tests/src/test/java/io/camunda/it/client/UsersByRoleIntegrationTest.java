/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.CreateUserResponse;
import io.camunda.client.api.search.response.User;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.test.util.Strings;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class UsersByRoleIntegrationTest {

  private static CamundaClient camundaClient;

  // assign tests

  @Test
  void shouldAssignUserToRole() {
    // given
    final var username = Strings.newRandomValidUsername();
    final var roleId = Strings.newRandomValidIdentityId();
    final var createdUser = createUser(username);
    createRole(roleId, "roleName", "roleDesc");

    // when
    camundaClient.newAssignUserToRoleCommand(roleId).username(username).send().join();

    // then
    Awaitility.await("User should be visible in role user search")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final List<User> users =
                  camundaClient.newUsersByRoleSearchRequest(roleId).send().join().items();
              assertThat(users)
                  .singleElement()
                  .satisfies(
                      user -> {
                        assertThat(user.getEmail()).isEqualTo(createdUser.getEmail());
                        assertThat(user.getName()).isEqualTo(createdUser.getName());
                        assertThat(user.getUsername()).isEqualTo(createdUser.getUsername());
                      });
            });
  }

  @Test
  void shouldRejectAssignIfRoleDoesNotExist() {
    final var username = Strings.newRandomValidUsername();
    createUser(username);
    final var nonExistingRoleId = Strings.newRandomValidIdentityId();

    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignUserToRoleCommand(nonExistingRoleId)
                    .username(username)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("404: 'Not Found'")
        .hasMessageContaining("role with this ID does not exist");
  }

  @Test
  void shouldRejectAssignIfUserAlreadyAssigned() {
    final var username = Strings.newRandomValidUsername();
    final var roleId = Strings.newRandomValidIdentityId();
    createUser(username);
    createRole(roleId, "roleName", "roleDesc");
    camundaClient.newAssignUserToRoleCommand(roleId).username(username).send().join();

    assertThatThrownBy(
            () -> camundaClient.newAssignUserToRoleCommand(roleId).username(username).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("409: 'Conflict'")
        .hasMessageContaining("the entity is already assigned to this role");
  }

  @Test
  void shouldRejectAssignIfUsernameIsNull() {
    assertThatThrownBy(
            () -> camundaClient.newAssignUserToRoleCommand("roleId").username(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("username must not be null");
  }

  @Test
  void shouldRejectAssignIfUsernameIsEmpty() {
    assertThatThrownBy(
            () -> camundaClient.newAssignUserToRoleCommand("roleId").username("").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("username must not be empty");
  }

  @Test
  void shouldRejectAssignIfRoleIdIsNull() {
    assertThatThrownBy(
            () -> camundaClient.newAssignUserToRoleCommand(null).username("username").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleId must not be null");
  }

  @Test
  void shouldRejectAssignIfRoleIdIsEmpty() {
    assertThatThrownBy(
            () -> camundaClient.newAssignUserToRoleCommand("").username("username").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleId must not be empty");
  }

  @Test
  void shouldUnassignUsersFromRoleOnRoleDeletion() {
    // given
    final var roleId = Strings.newRandomValidIdentityId();
    final var username1 = Strings.newRandomValidUsername();
    final var username2 = Strings.newRandomValidUsername();
    createUser(username1);
    createUser(username2);
    createRole(roleId, "roleName", "roleDesc");

    camundaClient.newAssignUserToRoleCommand(roleId).username(username1).send().join();
    camundaClient.newAssignUserToRoleCommand(roleId).username(username2).send().join();

    Awaitility.await("Users should appear in role user search")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var users =
                  camundaClient.newUsersByRoleSearchRequest(roleId).send().join().items();
              assertThat(users)
                  .extracting(User::getUsername)
                  .containsExactlyInAnyOrder(username1, username2);
            });

    // when
    camundaClient.newDeleteRoleCommand(roleId).send().join();

    // then
    Awaitility.await("Users should be unassigned after role deletion")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var users =
                  camundaClient.newUsersByRoleSearchRequest(roleId).send().join().items();
              assertThat(users).isEmpty();
            });
  }

  // unassign tests

  @Test
  void shouldUnassignUserFromRole() {
    // given
    final var username = Strings.newRandomValidUsername();
    final var roleId = Strings.newRandomValidIdentityId();
    createUser(username);
    createRole(roleId, "roleName", "roleDesc");
    camundaClient.newAssignUserToRoleCommand(roleId).username(username).send().join();

    // when
    camundaClient.newUnassignUserFromRoleCommand(roleId).username(username).send().join();

    // then
    Awaitility.await("User should be removed from role")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var users =
                  camundaClient.newUsersByRoleSearchRequest(roleId).send().join().items();
              assertThat(users).noneMatch(u -> u.getUsername().equals(username));
            });
  }

  @Test
  void shouldRejectUnassignIfUserNotAssigned() {
    final var username = Strings.newRandomValidUsername();
    final var roleId = Strings.newRandomValidIdentityId();
    createUser(username);
    createRole(roleId, "roleName", "roleDesc");

    assertThatThrownBy(
            () ->
                camundaClient
                    .newUnassignUserFromRoleCommand(roleId)
                    .username(username)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("404: 'Not Found'")
        .hasMessageContaining("the entity is not assigned to this role");
  }

  @Test
  void shouldRejectUnassignIfRoleDoesNotExist() {
    final var roleId = Strings.newRandomValidIdentityId();
    final var username = Strings.newRandomValidUsername();
    createUser(username);

    assertThatThrownBy(
            () ->
                camundaClient
                    .newUnassignUserFromRoleCommand(roleId)
                    .username(username)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("404: 'Not Found'")
        .hasMessageContaining("role with this ID does not exist");
  }

  @Test
  void shouldRejectUnassignIfRoleIsNull() {
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUnassignUserFromRoleCommand(null)
                    .username("username")
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleId must not be null");
  }

  @Test
  void shouldRejectUnassignIfRoleIsEmpty() {
    assertThatThrownBy(
            () ->
                camundaClient.newUnassignUserFromRoleCommand("").username("username").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleId must not be empty");
  }

  @Test
  void shouldRejectUnassignIfUsernameIsNull() {
    assertThatThrownBy(
            () ->
                camundaClient.newUnassignUserFromRoleCommand("roleId").username(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("username must not be null");
  }

  @Test
  void shouldRejectUnassignIfUsernameIsEmpty() {
    assertThatThrownBy(
            () -> camundaClient.newUnassignUserFromRoleCommand("roleId").username("").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("username must not be empty");
  }

  // search tests

  @Test
  void shouldReturnUsersByRole() {
    final var user1 = Strings.newRandomValidUsername();
    final var user2 = Strings.newRandomValidUsername();
    final var roleId = Strings.newRandomValidIdentityId();

    createUser(user1);
    createUser(user2);
    createRole(roleId, "roleName", "roleDesc");

    camundaClient.newAssignUserToRoleCommand(roleId).username(user1).send().join();
    camundaClient.newAssignUserToRoleCommand(roleId).username(user2).send().join();

    Awaitility.await("Users should appear in role user search")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var users =
                  camundaClient.newUsersByRoleSearchRequest(roleId).send().join().items();
              assertThat(users).extracting(User::getUsername).contains(user1, user2);
            });
  }

  @Test
  void shouldReturnUsersByRoleFilteredByUsername() {
    final var username = Strings.newRandomValidUsername();
    final var username2 = Strings.newRandomValidUsername();
    final var roleId = Strings.newRandomValidIdentityId();
    createUser(username, "John Doe", username + "@example.com");
    createUser(username2, "Bob", username2 + "@example.com");
    createRole(roleId, "roleName", "roleDesc");
    camundaClient.newAssignUserToRoleCommand(roleId).username(username).send().join();

    Awaitility.await("User should appear in role user search filtered by username")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var users =
                  camundaClient
                      .newUsersByRoleSearchRequest(roleId)
                      .filter(f -> f.username(username))
                      .send()
                      .join()
                      .items();
              assertThat(users).singleElement().extracting(User::getUsername).isEqualTo(username);
            });
  }

  @Test
  void shouldReturnUsersByRoleFilteredByName() {
    final var username = Strings.newRandomValidUsername();
    final var username2 = Strings.newRandomValidUsername();
    final var roleId = Strings.newRandomValidIdentityId();
    final var name = "Alice Johnson";
    final var name2 = "John Doe";
    createUser(username, name, username + "@example.com");
    createUser(username2, name2, username + "@example.com");
    createRole(roleId, "roleName", "roleDesc");
    camundaClient.newAssignUserToRoleCommand(roleId).username(username).send().join();

    Awaitility.await("User should appear in role user search filtered by name")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var users =
                  camundaClient
                      .newUsersByRoleSearchRequest(roleId)
                      .filter(f -> f.name(name))
                      .send()
                      .join()
                      .items();
              assertThat(users).singleElement().extracting(User::getName).isEqualTo(name);
            });
  }

  @Test
  void shouldReturnUsersByRoleFilteredByEmail() {
    final var username = Strings.newRandomValidUsername();
    final var username2 = Strings.newRandomValidUsername();
    final var roleId = Strings.newRandomValidIdentityId();
    final var email = username + "@example.com";
    createUser(username, "Bob Test", email);
    createUser(username2, "Alice Johnson", username2 + "@example.com");
    createRole(roleId, "roleName", "desc");
    camundaClient.newAssignUserToRoleCommand(roleId).username(username).send().join();

    Awaitility.await("User should appear in role user search filtered by email")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var users =
                  camundaClient
                      .newUsersByRoleSearchRequest(roleId)
                      .filter(f -> f.email(email))
                      .send()
                      .join()
                      .items();
              assertThat(users).singleElement().extracting(User::getEmail).isEqualTo(email);
            });
  }

  @Test
  void shouldReturnUsersByRoleSorted() {
    final var roleId = Strings.newRandomValidIdentityId();
    final var user1 = Strings.newRandomValidUsername();
    final var user2 = Strings.newRandomValidUsername();
    final var user3 = Strings.newRandomValidUsername();
    createUser(user1, "Alice", user1 + "@example.com");
    createUser(user2, "Bob", user2 + "@example.com");
    createUser(user3, "Simon", user2 + "@example.com");
    createRole(roleId, "roleName", "desc");

    camundaClient.newAssignUserToRoleCommand(roleId).username(user1).send().join();
    camundaClient.newAssignUserToRoleCommand(roleId).username(user2).send().join();
    camundaClient.newAssignUserToRoleCommand(roleId).username(user3).send().join();

    Awaitility.await("User should appear in role user search filtered by email")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var users =
                  camundaClient
                      .newUsersByRoleSearchRequest(roleId)
                      .sort(s -> s.name().desc())
                      .send()
                      .join()
                      .items();
              assertThat(users).extracting(User::getName).containsExactly("Simon", "Bob", "Alice");
            });
  }

  @Test
  void shouldRejectSearchIfRoleIdIsNull() {
    assertThatThrownBy(() -> camundaClient.newUsersByRoleSearchRequest(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleId must not be null");
  }

  @Test
  void shouldRejectSearchIfRoleIdIsEmpty() {
    assertThatThrownBy(() -> camundaClient.newUsersByRoleSearchRequest("").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleId must not be empty");
  }

  private static void createRole(final String roleId, final String name, final String desc) {
    camundaClient.newCreateRoleCommand().roleId(roleId).name(name).description(desc).send().join();
  }

  private static CreateUserResponse createUser(final String username) {
    return createUser(username, "name", username + "@mail.com");
  }

  private static CreateUserResponse createUser(
      final String username, final String name, final String email) {
    return camundaClient
        .newUserCreateCommand()
        .username(username)
        .email(email)
        .name(name)
        .password("pass")
        .send()
        .join();
  }
}
