/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
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
        .newUserCreateCommand()
        .username(username)
        .password("password")
        .name(name)
        .email(email)
        .send()
        .join();
    assertUserCreated(username);

    // when
    camundaClient
        .newUpdateUserCommand(username)
        .name(updatedName)
        .email(updatedEmail)
        .send()
        .join();

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
              assertThat(response.items().getFirst().getName()).isEqualTo(updatedName);
              assertThat(response.items().getFirst().getEmail()).isEqualTo(updatedEmail);
            });
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
