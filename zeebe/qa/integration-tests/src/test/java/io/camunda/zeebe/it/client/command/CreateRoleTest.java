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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
class CreateRoleTest {

  @AutoClose CamundaClient client;

  @TestZeebe
  private final TestStandaloneBroker zeebe =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  @BeforeEach
  void initClientAndInstances() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
  }

  @Test
  @Disabled("https://github.com/camunda/camunda/issues/29926")
  void shouldCreateRole() {
    // when
    final var response = client.newCreateRoleCommand().name("Role Name").send().join();

    // then
    assertThat(response.getRoleKey()).isGreaterThan(0);
    ZeebeAssertHelper.assertRoleCreated(
        "Role Name",
        (role) -> {
          assertThat(role.getName()).isEqualTo("Role Name");
        });
  }

  @Test
  @Disabled("https://github.com/camunda/camunda/issues/29926")
  void shouldRejectIfRoleIdAlreadyExists() {
    // given
    client.newCreateRoleCommand().name("Role Name").send().join();

    // when / then
    assertThatThrownBy(() -> client.newCreateRoleCommand().name("Role Name").send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 409: 'Conflict'")
        .hasMessageContaining("a role with this name already exists");
  }

  @Test
  void shouldRejectIfMissingRoleName() {
    // when / then
    assertThatThrownBy(() -> client.newCreateRoleCommand().send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be null");
  }
}
