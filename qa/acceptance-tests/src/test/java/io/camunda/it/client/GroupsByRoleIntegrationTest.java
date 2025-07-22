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

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.Group;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.test.util.Strings;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
public class GroupsByRoleIntegrationTest {

  private static CamundaClient camundaClient;

  @Test
  void shouldReturnGroupsByRole() {
    final var roleId = Strings.newRandomValidIdentityId();
    final var groupId1 = Strings.newRandomValidIdentityId();
    final var groupId2 = Strings.newRandomValidIdentityId();

    createRole(roleId, "Role Name", "desc");
    createGroup(groupId1, "Group 1", "desc");
    createGroup(groupId2, "Group 2", "desc");

    assignGroupToRole(roleId, groupId1);
    assignGroupToRole(roleId, groupId2);

    Awaitility.await("Groups should appear in role group search")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final List<Group> groups =
                  camundaClient.newGroupsByRoleSearchRequest(roleId).send().join().items();
              assertThat(groups).extracting(Group::getGroupId).contains(groupId1, groupId2);
            });
  }

  @Test
  void shouldReturnGroupsByRoleSortedByGroupIdAsc() {
    final var roleId = Strings.newRandomValidIdentityId();
    final var groupId1 = "b" + Strings.newRandomValidIdentityId();
    final var groupId2 = "a" + Strings.newRandomValidIdentityId();

    createRole(roleId, "Role", "desc");
    createGroup(groupId1, "Group B", "desc");
    createGroup(groupId2, "Group A", "desc");

    assignGroupToRole(roleId, groupId1);
    assignGroupToRole(roleId, groupId2);

    Awaitility.await("Groups sorted ASC by groupId should appear")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var groups =
                  camundaClient
                      .newGroupsByRoleSearchRequest(roleId)
                      .sort(s -> s.groupId().asc())
                      .send()
                      .join()
                      .items();
              assertThat(groups).extracting(Group::getGroupId).containsExactly(groupId2, groupId1);
            });
  }

  @Test
  void shouldReturnGroupsByRoleSortedByGroupIdDesc() {
    final var roleId = Strings.newRandomValidIdentityId();
    final var groupId1 = "a" + Strings.newRandomValidIdentityId();
    final var groupId2 = "b" + Strings.newRandomValidIdentityId();

    createRole(roleId, "Role", "desc");
    createGroup(groupId1, "Group A", "desc");
    createGroup(groupId2, "Group B", "desc");

    assignGroupToRole(roleId, groupId1);
    assignGroupToRole(roleId, groupId2);

    Awaitility.await("Groups sorted DESC by groupId should appear")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var groups =
                  camundaClient
                      .newGroupsByRoleSearchRequest(roleId)
                      .sort(s -> s.groupId().desc())
                      .send()
                      .join()
                      .items();
              assertThat(groups).extracting(Group::getGroupId).containsExactly(groupId2, groupId1);
            });
  }

  private static void createRole(final String roleId, final String name, final String desc) {
    camundaClient.newCreateRoleCommand().roleId(roleId).name(name).description(desc).send().join();
  }

  private static void createGroup(final String groupId, final String name, final String desc) {
    camundaClient
        .newCreateGroupCommand()
        .groupId(groupId)
        .name(name)
        .description(desc)
        .send()
        .join();
  }

  private static void assignGroupToRole(final String roleId, final String groupId) {
    camundaClient.newAssignRoleToGroupCommand().roleId(roleId).groupId(groupId).send().join();
  }
}
