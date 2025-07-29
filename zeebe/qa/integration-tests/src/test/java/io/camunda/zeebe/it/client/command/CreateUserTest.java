/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.client.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.zeebe.it.util.ZeebeAssertHelper;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
class CreateUserTest {

  @AutoClose CamundaClient client;

  @TestZeebe
  private final TestStandaloneBroker zeebe =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  @BeforeEach
  void initClientAndInstances() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
  }

  @Test
  void shouldCreateUser() {
    // when
    final var response =
        client
            .newCreateUserCommand()
            .username("username")
            .name("name")
            .email("email@example.com")
            .password("password")
            .send()
            .join();

    // then
    ZeebeAssertHelper.assertUserCreated(
        "username",
        (user) -> {
          assertThat(user.getEmail()).isEqualTo("email@example.com");
          assertThat(user.getName()).isEqualTo("name");
          assertThat(user.getPassword()).isNotNull();
        });
  }

  @Test
  void shouldRejectIfUsernameAlreadyExists() {
    // given
    client
        .newCreateUserCommand()
        .username("username")
        .name("name")
        .email("email@example.com")
        .password("password")
        .send()
        .join();

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newCreateUserCommand()
                    .username("username")
                    .name("name")
                    .email("email@example.com")
                    .password("password")
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 409: 'Conflict'")
        .hasMessageContaining("a user with this username already exists");
  }

  @Test
  void shouldRejectIfMissingUsername() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newCreateUserCommand()
                    .name("name")
                    .email("email@example.com")
                    .password("password")
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("username must not be null");
  }
}
