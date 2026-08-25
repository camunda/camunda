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
import io.camunda.client.api.search.response.Group;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.test.util.Strings;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
@CompatibilityTest
public class GroupsSearchIT {

  private static CamundaClient camundaClient;

  private static final String GROUP_ID_1 = Strings.newRandomValidIdentityId();
  private static final String GROUP_NAME_1 = "AGroupName";
  private static final String GROUP_ID_2 = Strings.newRandomValidIdentityId();
  private static final String GROUP_NAME_2 = "BGroupName";

  @BeforeAll
  static void setup() {
    createGroup(GROUP_ID_1, GROUP_NAME_1);
    assertGroupCreated(GROUP_ID_1, GROUP_NAME_1);

    createGroup(GROUP_ID_2, GROUP_NAME_2);
    assertGroupCreated(GROUP_ID_2, GROUP_NAME_2);
  }

  @Test
  void searchShouldReturnGroupFilteredByGroupName() {
    final var groupSearchResponse =
        camundaClient.newGroupsSearchRequest().filter(fn -> fn.name(GROUP_NAME_1)).send().join();

    assertThat(groupSearchResponse.items())
        .hasSize(1)
        .map(Group::getName)
        .containsExactly(GROUP_NAME_1);
  }

  @Test
  void searchShouldReturnGroupsMatchingOrFilters() {
    final var groupSearchResponse =
        camundaClient
            .newGroupsSearchRequest()
            .filter(
                fn ->
                    fn.orFilters(
                        List.of(f1 -> f1.groupId(GROUP_ID_1), f2 -> f2.groupId(GROUP_ID_2))))
            .send()
            .join();

    assertThat(groupSearchResponse.items())
        .extracting(Group::getGroupId)
        .containsExactlyInAnyOrder(GROUP_ID_1, GROUP_ID_2);
  }

  private static void assertGroupCreated(final String groupId, final String groupName) {
    Awaitility.await("Group is created and exported")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var group = camundaClient.newGroupGetRequest(groupId).send().join();
              assertThat(group).isNotNull();
              assertThat(group.getGroupId()).isEqualTo(groupId);
              assertThat(group.getName()).isEqualTo(groupName);
            });
  }

  private static void createGroup(final String groupId, final String groupName) {
    camundaClient.newCreateGroupCommand().groupId(groupId).name(groupName).send().join();
  }
}
