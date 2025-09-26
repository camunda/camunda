/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.command;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
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
class CreateRoleTest {

  private static final String ROLE_ID = "roleId";
  private static final String ROLE_NAME = "roleName";
  private static final String ROLE_DESCRIPTION = "roleDescription";

  @AutoClose CamundaClient client;

  @TestZeebe
  private final TestStandaloneBroker zeebe =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  @BeforeEach
  void initClientAndInstances() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
  }

  @Test
  void shouldCreateRole() {
    // when
    final var response =
        client
            .newCreateRoleCommand()
            .roleId(ROLE_ID)
            .name(ROLE_NAME)
            .description(ROLE_DESCRIPTION)
            .send()
            .join();

    // then
    assertThat(response.getRoleId()).isEqualTo(ROLE_ID);
    assertThat(response.getName()).isEqualTo(ROLE_NAME);
    assertThat(response.getDescription()).isEqualTo(ROLE_DESCRIPTION);

    ZeebeAssertHelper.assertRoleCreated(
        ROLE_NAME,
        (role) -> {
          assertThat(role).hasRoleId(ROLE_ID);
          assertThat(role).hasName(ROLE_NAME);
          assertThat(role).hasDescription(ROLE_DESCRIPTION);
        });
  }

  @Test
  void shouldRejectIfRoleIdAlreadyExists() {
    // given
    client.newCreateRoleCommand().roleId(ROLE_ID).name(ROLE_NAME).send().join();

    // when / then
    assertThatThrownBy(
            () -> client.newCreateRoleCommand().roleId(ROLE_ID).name(ROLE_NAME).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 409: 'Conflict'")
        .hasMessageContaining("a role with this ID already exists");
  }

  @Test
  void shouldRejectIfMissingRoleId() {
    // when / then
    assertThatThrownBy(() -> client.newCreateRoleCommand().roleId(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleId must not be null");
  }

  @Test
  void shouldRejectIfMissingRoleName() {
    // when / then
    assertThatThrownBy(() -> client.newCreateRoleCommand().roleId(ROLE_ID).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be null");
  }
}
