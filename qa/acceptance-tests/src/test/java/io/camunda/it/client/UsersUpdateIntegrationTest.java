/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.UpdateUserResponse;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.User;
import io.camunda.qa.util.multidb.MultiDbTest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class UsersUpdateIntegrationTest {

  private static CamundaClient camundaClient;

  @Test
  void shouldUpdateUser() {
    // given
    final var username = "AUserName";
    final var name = "User Name";
    final var email = "email@email.com";

    final var updatedName = "Updated User Name";
    final var updatedEmail = "updated_email@email.com";

    camundaClient
        .newCreateUserCommand()
        .username(username)
        .password("password")
        .name(name)
        .email(email)
        .send()
        .join();
    assertUserCreated(username);

    // when
    final UpdateUserResponse updateUserResponse =
        camundaClient
            .newUpdateUserCommand(username)
            .name(updatedName)
            .email(updatedEmail)
            .send()
            .join();

    // then
    assertThat(updateUserResponse.getUsername()).isEqualTo(username);
    assertThat(updateUserResponse.getName()).isEqualTo(updatedName);
    assertThat(updateUserResponse.getEmail()).isEqualTo(updatedEmail);

    Awaitility.await("User is updated")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final SearchResponse<User> response =
                  camundaClient
                      .newUsersSearchRequest()
                      .filter(fn -> fn.username(username))
                      .send()
                      .join();
              assertThat(response.items().getFirst().getUsername()).isEqualTo(username);
              assertThat(response.items().getFirst().getName()).isEqualTo(updatedName);
              assertThat(response.items().getFirst().getEmail()).isEqualTo(updatedEmail);
            });
  }

  @Test
  void shouldUpdateUserWhenOptionalParametersAreNotPresent() {
    // given
    final var username = "BUserName";
    final var name = "B User Name";
    final var email = "some_email@email.com";

    camundaClient
        .newCreateUserCommand()
        .username(username)
        .password("password")
        .name(name)
        .email(email)
        .send()
        .join();
    assertUserCreated(username);

    // when
    camundaClient.newUpdateUserCommand(username).send().join();

    // then
    Awaitility.await("User is updated")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final SearchResponse<User> response =
                  camundaClient
                      .newUsersSearchRequest()
                      .filter(fn -> fn.username(username))
                      .send()
                      .join();
              assertThat(response.items().getFirst().getUsername()).isEqualTo(username);
              assertThat(response.items().getFirst().getName()).isEmpty();
              assertThat(response.items().getFirst().getEmail()).isEmpty();
            });
  }

  @Test
  void shouldReturnNotFoundWhenUpdatingIfUserDoesNotExist() {
    // when / then
    final var nonExistingUserName = "nonExistingUserName";
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUpdateUserCommand(nonExistingUserName)
                    .name("User Name")
                    .email("new_email@email.com")
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining(
            "Expected to update user with username %s, but a user with this username does not exist"
                .formatted(nonExistingUserName));
  }

  private static void assertUserCreated(final String userName) {
    Awaitility.await("User is created and exported")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final SearchResponse<User> usersSearchResponse =
                  camundaClient
                      .newUsersSearchRequest()
                      .filter(fn -> fn.username(userName))
                      .send()
                      .join();
              assertThat(usersSearchResponse.items()).hasSize(1);
              final User user = usersSearchResponse.items().getFirst();
              assertThat(user.getUsername()).isEqualTo(userName);
            });
  }
}
