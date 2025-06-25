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
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.User;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.test.util.Strings;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class UserIntegrationTest {

  private static CamundaClient camundaClient;

  @Test
  void shouldCreateUser() {
    // given
    final var username = "someUsername";
    final var name = "someName";
    final var email = "some_email@email.com";

    // when
    camundaClient
        .newCreateUserCommand()
        .username(username)
        .name(name)
        .password("some password")
        .email(email)
        .send()
        .join();

    // then
    Awaitility.await("User is created and exported")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final SearchResponse<User> response =
                  camundaClient
                      .newUsersSearchRequest()
                      .filter(fn -> fn.username(username))
                      .send()
                      .join();
              assertThat(response.items().size()).isEqualTo(1);
              assertThat(response.items().getFirst().getUsername()).isEqualTo(username);
              assertThat(response.items().getFirst().getName()).isEqualTo(name);
              assertThat(response.items().getFirst().getEmail()).isEqualTo(email);
            });
  }

  @Test
  void shouldDeleteUser() {
    // given
    final var username = "username";

    camundaClient
        .newCreateUserCommand()
        .username(username)
        .name("name")
        .password("some password")
        .email("email@email.com")
        .send()
        .join();

    // when
    camundaClient.newDeleteUserCommand(username).send().join();

    // then
    Awaitility.await("User is created and exported")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final SearchResponse<User> response =
                  camundaClient
                      .newUsersSearchRequest()
                      .filter(fn -> fn.username(username))
                      .send()
                      .join();
              assertThat(response.items()).isEmpty();
            });
  }

  @Test
  void shouldReturnNotFoundOnDeleteWhenUsernameDoesNotExist() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newDeleteUserCommand(Strings.newRandomValidIdentityId())
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining("a user with this username does not exist");
  }
}
