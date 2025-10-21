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
import io.camunda.client.api.search.response.RoleUser;
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
  void shouldAssignRoleToUser() {
    // given
    final var username = Strings.newRandomValidUsername();
    final var roleId = Strings.newRandomValidIdentityId();
    final var createdUser = createUser(username);
    createRole(roleId, "roleName", "roleDesc");

    // when
    camundaClient.newAssignRoleToUserCommand().roleId(roleId).username(username).send().join();

    // then
    Awaitility.await("User should be visible in role user search")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final List<RoleUser> users =
                  camundaClient.newUsersByRoleSearchRequest(roleId).send().join().items();
              assertThat(users)
                  .singleElement()
                  .satisfies(
                      roleUser -> {
                        assertThat(roleUser.getUsername()).isEqualTo(createdUser.getUsername());
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
                    .newAssignRoleToUserCommand()
                    .roleId(nonExistingRoleId)
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
    camundaClient.newAssignRoleToUserCommand().roleId(roleId).username(username).send().join();

    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignRoleToUserCommand()
                    .roleId(roleId)
                    .username(username)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("409: 'Conflict'")
        .hasMessageContaining("the entity is already assigned to this role");
  }

  @Test
  void shouldUnassignRoleFromUserOnRoleDeletion() {
    // given
    final var roleId = Strings.newRandomValidIdentityId();
    final var username1 = Strings.newRandomValidUsername();
    final var username2 = Strings.newRandomValidUsername();
    createUser(username1);
    createUser(username2);
    createRole(roleId, "roleName", "roleDesc");

    camundaClient.newAssignRoleToUserCommand().roleId(roleId).username(username1).send().join();
    camundaClient.newAssignRoleToUserCommand().roleId(roleId).username(username2).send().join();

    Awaitility.await("Users should appear in role user search")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var users =
                  camundaClient.newUsersByRoleSearchRequest(roleId).send().join().items();
              assertThat(users)
                  .extracting(RoleUser::getUsername)
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
    camundaClient.newAssignRoleToUserCommand().roleId(roleId).username(username).send().join();

    Awaitility.await("Wait for user to appear in search before unassign")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var users =
                  camundaClient.newUsersByRoleSearchRequest(roleId).send().join().items();
              assertThat(users).anyMatch(u -> u.getUsername().equals(username));
            });
    // when

    camundaClient.newUnassignRoleFromUserCommand().roleId(roleId).username(username).send().join();

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
                    .newUnassignRoleFromUserCommand()
                    .roleId(roleId)
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
                    .newUnassignRoleFromUserCommand()
                    .roleId(roleId)
                    .username(username)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("404: 'Not Found'")
        .hasMessageContaining("role with this ID does not exist");
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

    camundaClient.newAssignRoleToUserCommand().roleId(roleId).username(user1).send().join();
    camundaClient.newAssignRoleToUserCommand().roleId(roleId).username(user2).send().join();

    Awaitility.await("Users should appear in role user search")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var users =
                  camundaClient.newUsersByRoleSearchRequest(roleId).send().join().items();
              assertThat(users).extracting(RoleUser::getUsername).contains(user1, user2);
            });
  }

  @Test
  void shouldReturnUsersByRoleSorted() {
    final var roleId = Strings.newRandomValidIdentityId();
    final var user1 = "Alice" + Strings.newRandomValidUsername();
    final var user2 = "Bob" + Strings.newRandomValidUsername();
    final var user3 = "Simon" + Strings.newRandomValidUsername();
    createUser(user1, "Alice", user1 + "@example.com");
    createUser(user2, "Bob", user2 + "@example.com");
    createUser(user3, "Simon", user2 + "@example.com");
    createRole(roleId, "roleName", "desc");

    camundaClient.newAssignRoleToUserCommand().roleId(roleId).username(user1).send().join();
    camundaClient.newAssignRoleToUserCommand().roleId(roleId).username(user2).send().join();
    camundaClient.newAssignRoleToUserCommand().roleId(roleId).username(user3).send().join();

    Awaitility.await("Users should appear in sorted search")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var roleUsers =
                  camundaClient
                      .newUsersByRoleSearchRequest(roleId)
                      .sort(s -> s.username().desc())
                      .page((p) -> p.limit(100))
                      .send()
                      .join()
                      .items();
              assertThat(roleUsers)
                  .extracting(RoleUser::getUsername)
                  .containsExactly(user3, user2, user1);
            });
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
        .newCreateUserCommand()
        .username(username)
        .email(email)
        .name(name)
        .password("pass")
        .send()
        .join();
  }
}
