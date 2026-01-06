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

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.GroupUser;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.test.util.Strings;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@CompatibilityTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
public class UsersByGroupSearchTest {

  private static CamundaClient camundaClient;

  private static final String USER_USERNAME_1 = Strings.newRandomValidUsername();
  private static final String USER_USERNAME_2 = Strings.newRandomValidUsername();
  private static final String USER_USERNAME_3 = Strings.newRandomValidUsername();
  private static final String GROUP_ID = Strings.newRandomValidIdentityId();

  @BeforeAll
  static void setup() {
    createUser(USER_USERNAME_1);
    createUser(USER_USERNAME_2);
    createUser(USER_USERNAME_3);

    createGroup(GROUP_ID);

    assignUserToGroup(USER_USERNAME_1, GROUP_ID);
    assignUserToGroup(USER_USERNAME_2, GROUP_ID);

    waitForGroupsToBeUpdated();
  }

  @Test
  void shouldReturnUsersByGroup() {
    final var users = camundaClient.newUsersByGroupSearchRequest(GROUP_ID).send().join();

    assertThat(users.items().size()).isEqualTo(2);
    assertThat(users.items())
        .extracting(GroupUser::getUsername)
        .containsExactly(USER_USERNAME_1, USER_USERNAME_2);
  }

  @Test
  void shouldRejectIfMissingGroupId() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newUsersByGroupSearchRequest(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("groupId must not be null");
  }

  @Test
  void shouldRejectIfEmptyGroupId() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newUsersByGroupSearchRequest("").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("groupId must not be empty");
  }

  private static void createUser(final String username) {
    camundaClient
        .newCreateUserCommand()
        .username(username)
        .password("password")
        .name("name")
        .email("email@email.com")
        .send()
        .join();
  }

  private static void createGroup(final String groupId) {
    camundaClient.newCreateGroupCommand().groupId(groupId).name("name").send().join();
  }

  private static void assignUserToGroup(final String username, final String groupId) {
    camundaClient.newAssignUserToGroupCommand().username(username).groupId(groupId).send().join();
  }

  private static void waitForGroupsToBeUpdated() {
    Awaitility.await("should receive data from ES")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var users = camundaClient.newUsersByGroupSearchRequest(GROUP_ID).send().join();
              assertThat(users.items().size()).isEqualTo(2);
            });
  }
}
