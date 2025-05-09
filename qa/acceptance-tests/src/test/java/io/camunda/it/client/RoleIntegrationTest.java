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
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.test.util.Strings;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class RoleIntegrationTest {

  private static CamundaClient camundaClient;

  private static final String ROLE_ID_1 = Strings.newRandomValidIdentityId();
  private static final String ROLE_ID_2 = Strings.newRandomValidIdentityId();
  private static final String ROLE_NAME_1 = "ARoleName";
  private static final String ROLE_NAME_2 = "BRoleName";
  private static final String DESCRIPTION = "description";

  @Test
  void shouldCreateAndGetRoleById() {
    // when
    camundaClient
        .newCreateRoleCommand()
        .roleId(ROLE_ID_1)
        .name(ROLE_NAME_1)
        .description(DESCRIPTION)
        .send()
        .join();

    // then
    Awaitility.await("Role is created and exported")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var role = camundaClient.newRoleGetRequest(ROLE_ID_1).send().join();
              assertThat(role).isNotNull();
              assertThat(role.getRoleId()).isEqualTo(ROLE_ID_1);
              assertThat(role.getName()).isEqualTo(ROLE_NAME_1);
              assertThat(role.getDescription()).isEqualTo(DESCRIPTION);
              assertThat(role.getRoleKey()).isPositive();
            });
  }

  @Test
  void shouldRejectCreationIfRoleIdAlreadyExists() {
    // given
    camundaClient
        .newCreateRoleCommand()
        .roleId(ROLE_ID_2)
        .name(ROLE_NAME_2)
        .description(DESCRIPTION)
        .send()
        .join();

    // when/then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newCreateRoleCommand()
                    .roleId(ROLE_ID_2)
                    .name(ROLE_NAME_2)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 409: 'Conflict'")
        .hasMessageContaining("a role with this ID already exists");
  }

  @Test
  void shouldRejectCreationIfMissingRoleId() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newRoleGetRequest(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleId must not be null");
  }

  @Test
  void shouldRejectCreationIfEmptyRoleId() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newRoleGetRequest("").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleId must not be empty");
  }

  @Test
  void shouldRejectCreationIfMissingRoleName() {
    // when / then
    assertThatThrownBy(
            () -> camundaClient.newCreateRoleCommand().roleId("someRoleId").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be null");
  }

  @Test
  void shouldReturnNotFoundIfRoleDoesNotExist() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newRoleGetRequest("someRoleId").send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining("Role with role ID someRoleId not found");
  }
}
