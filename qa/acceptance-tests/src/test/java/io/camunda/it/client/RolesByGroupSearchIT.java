/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.Role;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.test.util.Strings;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class RolesByGroupSearchIT {

  private static CamundaClient camundaClient;

  private static final String ROLE_ID_1 = "a" + Strings.newRandomValidUsername();
  private static final String ROLE_ID_2 = "b" + Strings.newRandomValidUsername();
  private static final String GROUP_ID = Strings.newRandomValidIdentityId();
  private static final String GROUP_ID_2 = Strings.newRandomValidIdentityId();

  @BeforeAll
  static void setup() {
    createRole(ROLE_ID_1);
    createRole(ROLE_ID_2);

    createGroup(GROUP_ID);
    createGroup(GROUP_ID_2);

    assignGroupToRole(GROUP_ID, ROLE_ID_1);
    assignGroupToRole(GROUP_ID, ROLE_ID_2);
    assignGroupToRole(GROUP_ID_2, ROLE_ID_1);

    waitForGroupsToBeUpdated();
  }

  @Test
  void shouldReturnRolesByGroup() {
    final var rolesGroup1 = camundaClient.newRolesByGroupSearchRequest(GROUP_ID).send().join();

    assertThat(rolesGroup1.items().size()).isEqualTo(2);
    assertThat(rolesGroup1.items())
        .extracting(Role::getRoleId, Role::getName)
        .contains(tuple(ROLE_ID_1, ROLE_ID_1 + "-name"), tuple(ROLE_ID_2, ROLE_ID_2 + "-name"));

    final var rolesGroup2 = camundaClient.newRolesByGroupSearchRequest(GROUP_ID_2).send().join();
    assertThat(rolesGroup2.items().size()).isEqualTo(1);
    assertThat(rolesGroup2.items())
        .extracting(Role::getRoleId, Role::getName)
        .contains(tuple(ROLE_ID_1, ROLE_ID_1 + "-name"));
  }

  @Test
  void shouldReturnRolesByGroupFiltered() {
    final var roles =
        camundaClient
            .newRolesByGroupSearchRequest(GROUP_ID)
            .filter(fn -> fn.roleId(ROLE_ID_1))
            .send()
            .join();

    assertThat(roles.items().size()).isEqualTo(1);
    assertThat(roles.items())
        .extracting(Role::getRoleId, Role::getName)
        .contains(tuple(ROLE_ID_1, ROLE_ID_1 + "-name"));
  }

  @Test
  void shouldReturnRolesByGroupSorted() {
    final var roles =
        camundaClient
            .newRolesByGroupSearchRequest(GROUP_ID)
            .sort(fn -> fn.roleId().desc())
            .send()
            .join();

    assertThat(roles.items().size()).isEqualTo(2);
    assertThat(roles.items())
        .extracting(Role::getRoleId, Role::getName)
        .contains(tuple(ROLE_ID_2, ROLE_ID_2 + "-name"), tuple(ROLE_ID_1, ROLE_ID_1 + "-name"));
  }

  @Test
  void shouldRejectIfMissingGroupId() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newRolesByGroupSearchRequest(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("groupId must not be null");
  }

  @Test
  void shouldRejectIfEmptyGroupId() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newRolesByGroupSearchRequest("").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("groupId must not be empty");
  }

  private static void createRole(final String roleId) {
    camundaClient.newCreateRoleCommand().roleId(roleId).name(roleId + "-name").send().join();
  }

  private static void createGroup(final String groupId) {
    camundaClient.newCreateGroupCommand().groupId(groupId).name("name").send().join();
  }

  private static void assignGroupToRole(final String groupId, final String roleId) {
    camundaClient.newAssignRoleToGroupCommand().roleId(roleId).groupId(groupId).send().join();
  }

  private static void waitForGroupsToBeUpdated() {
    Awaitility.await("should receive data from ES")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var rolesGroup1 =
                  camundaClient.newRolesByGroupSearchRequest(GROUP_ID).send().join();
              assertThat(rolesGroup1.items().size()).isEqualTo(2);

              final var rolesGroup2 =
                  camundaClient.newRolesByGroupSearchRequest(GROUP_ID_2).send().join();
              assertThat(rolesGroup2.items().size()).isEqualTo(1);
            });
  }
}
