/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ProblemException;
import io.camunda.zeebe.it.util.ZeebeAssertHelper;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
@AutoCloseResources
class CreateUserTest {

  @AutoCloseResource ZeebeClient client;

  @TestZeebe
  private final TestStandaloneBroker zeebe = new TestStandaloneBroker().withRecordingExporter(true);

  @BeforeEach
  void initClientAndInstances() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
  }

  @Test
  @Disabled("Feature is disabled for 8.6 as the feature releases in 8.7")
  void shouldCreateUser() {
    // when
    final var response =
        client
            .newUserCreateCommand()
            .username("username")
            .name("name")
            .email("email@example.com")
            .password("password")
            .send()
            .join();

    // then
    assertThat(response.getUserKey()).isGreaterThan(0);
    ZeebeAssertHelper.assertUserCreated(
        "username",
        (user) -> {
          assertThat(user.getEmail()).isEqualTo("email@example.com");
          assertThat(user.getName()).isEqualTo("name");
          assertThat(user.getPassword()).isNotNull();
        });
  }

  @Test
  @Disabled("Feature is disabled for 8.6 as the feature releases in 8.7")
  void shouldRejectIfUsernameAlreadyExists() {
    // given
    client
        .newUserCreateCommand()
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
                    .newUserCreateCommand()
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
  @Disabled("Feature is disabled for 8.6 as the feature releases in 8.7")
  void shouldRejectIfMissingUsername() {
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
}
