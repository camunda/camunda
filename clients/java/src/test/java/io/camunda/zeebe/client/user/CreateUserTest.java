/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.client.user;

import static io.camunda.zeebe.client.impl.http.HttpClientFactory.REST_API_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.client.api.command.ProblemException;
import io.camunda.zeebe.client.protocol.rest.ProblemDetail;
import io.camunda.zeebe.client.protocol.rest.UserWithPasswordRequest;
import io.camunda.zeebe.client.util.ClientRestTest;
import org.junit.jupiter.api.Test;

public class CreateUserTest extends ClientRestTest {
  @Test
  void shouldCreateUser() {
    // when
    client
        .newUserCreateCommand()
        .username("username")
        .name("name")
        .email("email@example.com")
        .password("password")
        .send()
        .join();

    // then
    final UserWithPasswordRequest request =
        gatewayService.getLastRequest(UserWithPasswordRequest.class);
    assertThat(request.getUsername()).isEqualTo("username");
  }

  @Test
  void shouldRaiseExceptionOnNullUsername() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUserCreateCommand()
                    .name("name")
                    .email("email@example.com")
                    .password("password")
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("username must not be null");
  }

  @Test
  void shouldRaiseExceptionOnRequestError() {
    // given
    gatewayService.errorOnRequest(
        REST_API_PATH + "/users/", () -> new ProblemDetail().title("Not Found").status(404));

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUserCreateCommand()
                    .username("username")
                    .name("name")
                    .email("email@example.com")
                    .password("password")
                    .send()
                    .join())
        .hasCauseInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }
}
