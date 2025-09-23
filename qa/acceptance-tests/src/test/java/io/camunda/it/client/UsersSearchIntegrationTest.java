/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.search.exception.ErrorMessages.ERROR_ENTITY_BY_ID_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.User;
import io.camunda.qa.util.multidb.MultiDbTest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class UsersSearchIntegrationTest {

  private static CamundaClient camundaClient;

  private static final String USERNAME_1 = "AUserName";
  private static final String USERNAME_2 = "BUserName";
  private static final String EMAIL_1 = "email_a@email.com";
  private static final String EMAIL_2 = "email_b@email.com";
  private static final String NAME_1 = "A Name";
  private static final String NAME_2 = "B Name";

  @BeforeAll
  static void setup() {
    camundaClient
        .newCreateUserCommand()
        .username(USERNAME_1)
        .password("password")
        .name(NAME_1)
        .email(EMAIL_1)
        .send()
        .join();
    assertUserCreated(USERNAME_1);

    camundaClient
        .newCreateUserCommand()
        .username(USERNAME_2)
        .password("password2")
        .name(NAME_2)
        .email(EMAIL_2)
        .send()
        .join();
    assertUserCreated(USERNAME_2);
  }

  @Test
  void shouldGetUserByUsername() {
    final User user = camundaClient.newUserGetRequest(USERNAME_1).send().join();

    assertThat(user.getUsername()).isEqualTo(USERNAME_1);
    assertThat(user.getEmail()).isEqualTo(EMAIL_1);
    assertThat(user.getName()).isEqualTo(NAME_1);
  }

  @Test
  void shouldReturnNotFoundWhenGettingUserThatDoesNotExist() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newUserGetRequest("someUserName").send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining(
            ERROR_ENTITY_BY_ID_NOT_FOUND.formatted("User", "username", "someUserName"));
  }

  @Test
  void searchShouldReturnUsersFilteredByUsername() {
    final SearchResponse<User> usersSearchResponse =
        camundaClient.newUsersSearchRequest().filter(fn -> fn.username(USERNAME_1)).send().join();

    assertThat(usersSearchResponse.items())
        .hasSize(1)
        .map(User::getUsername)
        .containsOnly(USERNAME_1);
  }

  @Test
  void searchShouldReturnUsersFilteredByEmail() {
    final SearchResponse<User> usersSearchResponse =
        camundaClient.newUsersSearchRequest().filter(fn -> fn.email(EMAIL_1)).send().join();

    assertThat(usersSearchResponse.items()).map(User::getEmail).containsOnly(EMAIL_1);
  }

  @Test
  void searchShouldReturnUsersFilteredByName() {
    final SearchResponse<User> usersSearchResponse =
        camundaClient.newUsersSearchRequest().filter(fn -> fn.name(NAME_1)).send().join();

    assertThat(usersSearchResponse.items()).map(User::getName).containsOnly(NAME_1);
  }

  @Test
  void searchShouldReturnEmptyListWhenSearchingForNonExistingUsername() {
    final var userSearchResponse =
        camundaClient
            .newUsersSearchRequest()
            .filter(fn -> fn.username("someUserName"))
            .send()
            .join();
    assertThat(userSearchResponse.items()).isEmpty();
  }

  @Test
  void searchShouldReturnEmptyListWhenSearchingForNonExistingEmail() {
    final var userSearchResponse =
        camundaClient
            .newUsersSearchRequest()
            .filter(fn -> fn.email("some.email@email.com"))
            .send()
            .join();
    assertThat(userSearchResponse.items()).isEmpty();
  }

  @Test
  void searchShouldReturnEmptyListWhenSearchingForNonExistingName() {
    final var userSearchResponse =
        camundaClient.newUsersSearchRequest().filter(fn -> fn.name("some name")).send().join();
    assertThat(userSearchResponse.items()).isEmpty();
  }

  @Test
  void searchShouldReturnUsersSortedByUsername() {
    final var roleSearchResponse =
        camundaClient.newUsersSearchRequest().sort(s -> s.username().desc()).send().join();

    assertThat(roleSearchResponse.items())
        .hasSize(3)
        .map(User::getUsername)
        .containsExactly("demo", USERNAME_2, USERNAME_1);
  }

  @Test
  void searchShouldReturnUsersSortedByEmail() {
    final var roleSearchResponse =
        camundaClient.newUsersSearchRequest().sort(s -> s.email().desc()).send().join();

    assertThat(roleSearchResponse.items())
        .hasSize(3)
        .map(User::getEmail)
        .containsExactly(EMAIL_2, EMAIL_1, "demo@example.com");
  }

  @Test
  void searchShouldReturnUsersSortedByName() {
    final var roleSearchResponse =
        camundaClient.newUsersSearchRequest().sort(s -> s.name().desc()).send().join();

    assertThat(roleSearchResponse.items())
        .hasSize(3)
        .map(User::getName)
        .containsExactly("Demo", NAME_2, NAME_1);
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
