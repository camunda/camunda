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
import io.camunda.client.api.search.response.Role;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.test.util.Strings;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
@CompatibilityTest
public class RolesSearchTest {

  private static CamundaClient camundaClient;

  private static final String ROLE_ID_1 = Strings.newRandomValidIdentityId();
  private static final String ROLE_NAME_1 = "ARoleName";
  private static final String ROLE_ID_2 = Strings.newRandomValidIdentityId();
  private static final String ROLE_NAME_2 = "BRoleName";

  @BeforeAll
  static void setup() {
    createRole(ROLE_ID_1, ROLE_NAME_1, "description");
    assertRoleCreated(ROLE_ID_1, ROLE_NAME_1, "description");

    createRole(ROLE_ID_2, ROLE_NAME_2, "description");
    assertRoleCreated(ROLE_ID_2, ROLE_NAME_2, "description");
  }

  @Test
  void searchShouldReturnRoleFilteredByRoleName() {
    final var roleSearchResponse =
        camundaClient.newRolesSearchRequest().filter(fn -> fn.name(ROLE_NAME_1)).send().join();

    assertThat(roleSearchResponse.items())
        .hasSize(1)
        .map(Role::getName)
        .containsExactly(ROLE_NAME_1);
  }

  @Test
  void searchShouldReturnRolesFilteredById() {
    final var roleSearchResponse =
        camundaClient.newRolesSearchRequest().filter(fn -> fn.roleId(ROLE_ID_1)).send().join();

    assertThat(roleSearchResponse.items())
        .hasSize(1)
        .map(Role::getRoleId)
        .containsExactly(ROLE_ID_1);
  }

  @Test
  void searchShouldReturnEmptyListWhenSearchingForNonExistingRoleId() {
    final var roleSearchResponse =
        camundaClient.newRolesSearchRequest().filter(fn -> fn.roleId("someRoleId")).send().join();
    assertThat(roleSearchResponse.items()).isEmpty();
  }

  @Test
  void searchShouldReturnEmptyListWhenSearchingForNonExistingRoleName() {
    final var roleSearchResponse =
        camundaClient.newRolesSearchRequest().filter(fn -> fn.name("someRoleName")).send().join();
    assertThat(roleSearchResponse.items()).isEmpty();
  }

  @Test
  void searchShouldReturnRolesSortedByName() {
    final var roleSearchResponse =
        camundaClient.newRolesSearchRequest().sort(s -> s.name().desc()).send().join();

    assertThat(roleSearchResponse.items())
        .hasSizeGreaterThanOrEqualTo(2)
        .map(Role::getName)
        // filtering here as "RPA", "Connectors" and "Admin" roles are also initialized in
        // IdentitySetupInitializer
        .filteredOn(r -> r.equals(ROLE_NAME_1) || r.equals(ROLE_NAME_2))
        .containsExactly(ROLE_NAME_2, ROLE_NAME_1);
  }

  private static void assertRoleCreated(
      final String roleId, final String roleName, final String description) {
    Awaitility.await("Role is created and exported")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var role = camundaClient.newRoleGetRequest(roleId).send().join();
              assertThat(role).isNotNull();
              assertThat(role.getRoleId()).isEqualTo(roleId);
              assertThat(role.getName()).isEqualTo(roleName);
              assertThat(role.getDescription()).isEqualTo(description);
            });
  }

  private static void createRole(
      final String roleId, final String roleName, final String description) {
    camundaClient
        .newCreateRoleCommand()
        .roleId(roleId)
        .name(roleName)
        .description(description)
        .send()
        .join();
  }
}
