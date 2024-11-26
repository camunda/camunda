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
import org.junit.jupiter.api.Test;

@ZeebeIntegration
@AutoCloseResources
class CreateRoleTest {

  @AutoCloseResource ZeebeClient client;

  @TestZeebe
  private final TestStandaloneBroker zeebe = new TestStandaloneBroker().withRecordingExporter(true);

  @BeforeEach
  void initClientAndInstances() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
  }

  @Test
  void shouldCreateRole() {
    // when
    final var response =
        client.newRoleCreateCommand().roleId("role-id").name("Role Name").send().join();

    // then
    assertThat(response.getRoleKey()).isGreaterThan(0);
    ZeebeAssertHelper.assertRoleCreated(
        "role-id",
        (role) -> {
          assertThat(role.getName()).isEqualTo("Role Name");
        });
  }

  @Test
  void shouldRejectIfRoleIdAlreadyExists() {
    // given
    client.newRoleCreateCommand().roleId("role-id").name("Role Name").send().join();

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newRoleCreateCommand()
                    .roleId("role-id")
                    .name("Another Role Name")
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 409: 'Conflict'")
        .hasMessageContaining("a role with this ID already exists");
  }

  @Test
  void shouldRejectIfMissingRoleId() {
    // when / then
    assertThatThrownBy(() -> client.newRoleCreateCommand().name("Role Name").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleId must not be null");
  }
}
