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
import io.camunda.client.api.search.response.Client;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.test.util.Strings;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class ClientsByGroupIntegrationTest {

  private static CamundaClient camundaClient;
  private static final String GROUP_ID = Strings.newRandomValidIdentityId();

  @BeforeAll
  static void setup() {
    createGroup(GROUP_ID);
  }

  @Test
  void shouldAssignClientToGroup() {
    // given
    final var clientId = "clientId";

    // when
    camundaClient
        .newAssignClientToGroupCommand()
        .clientId(clientId)
        .groupId(GROUP_ID)
        .send()
        .join();

    // then
    Awaitility.await("Client is assigned to the group")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final SearchResponse<Client> result =
                  camundaClient.newClientsByGroupSearchRequest(GROUP_ID).send().join();
              assertThat(result.items()).anyMatch(r -> clientId.equals(r.getClientId()));
            });
  }

  @Test
  void shouldReturnNotFoundOnAssigningClientToGroupIfGroupDoesNotExist() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignClientToGroupCommand()
                    .clientId(Strings.newRandomValidIdentityId())
                    .groupId(Strings.newRandomValidIdentityId())
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining("a group with this ID does not exist");
  }

  @Test
  void shouldUnassignClientFromGroup() {
    // given
    final var clientId = "clientId_toRemove";

    camundaClient
        .newAssignClientToGroupCommand()
        .clientId(clientId)
        .groupId(GROUP_ID)
        .send()
        .join();

    Awaitility.await("Client is assigned to the group")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final SearchResponse<Client> result =
                  camundaClient.newClientsByGroupSearchRequest(GROUP_ID).send().join();
              assertThat(result.items()).anyMatch(r -> clientId.equals(r.getClientId()));
            });

    // when
    camundaClient
        .newUnassignClientFromGroupCommand()
        .clientId(clientId)
        .groupId(GROUP_ID)
        .send()
        .join();

    // then
    Awaitility.await("Client is unassigned from the group")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final SearchResponse<Client> result =
                  camundaClient.newClientsByGroupSearchRequest(GROUP_ID).send().join();
              assertThat(result.items()).noneMatch(r -> clientId.equals(r.getClientId()));
            });
  }

  @Test
  void shouldRejectUnassigningIfClientIsNotAssignedToGroup() {
    // given
    final var clientId = Strings.newRandomValidIdentityId();

    // when/then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUnassignClientFromGroupCommand()
                    .clientId(clientId)
                    .groupId(GROUP_ID)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining(
            "Expected to remove entity with ID '"
                + clientId
                + "' from group with ID '"
                + GROUP_ID
                + "', but the entity is not assigned to this group.");
  }

  @Test
  void searchClientsShouldReturnEmptyListWhenSearchingForNonExistingGroupId() {
    final var clientsSearchResponse =
        camundaClient.newClientsByGroupSearchRequest("someGroupId").send().join();
    assertThat(clientsSearchResponse.items()).isEmpty();
  }

  @Test
  void shouldRejectClientsByGroupSearchIfMissingGroupId() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newClientsByGroupSearchRequest(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("groupId must not be null");
  }

  @Test
  void shouldRejectClientsByGroupSearchIfEmptyGroupId() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newClientsByGroupSearchRequest("").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("groupId must not be empty");
  }

  @Test
  void shouldReturnClientsByGroup() {
    // given
    final var firstClientId = "someClientId";
    final var secondClientId = "otherClientId";

    // when
    camundaClient
        .newAssignClientToGroupCommand()
        .clientId(firstClientId)
        .groupId(GROUP_ID)
        .send()
        .join();

    camundaClient
        .newAssignClientToGroupCommand()
        .clientId(secondClientId)
        .groupId(GROUP_ID)
        .send()
        .join();

    // then
    Awaitility.await("Clients are assigned to the group and can be searched")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final SearchResponse<Client> result =
                  camundaClient.newClientsByGroupSearchRequest(GROUP_ID).send().join();
              assertThat(result.items())
                  .map(Client::getClientId)
                  .contains(firstClientId, secondClientId);
            });
  }

  @Test
  void shouldReturnClientsByGroupSorted() {
    // given
    final var firstClientId = "AClientId";
    final var secondClientId = "BClientId";
    final var groupId = Strings.newRandomValidIdentityId();
    createGroup(groupId);

    // when
    camundaClient
        .newAssignClientToGroupCommand()
        .clientId(firstClientId)
        .groupId(groupId)
        .send()
        .join();

    camundaClient
        .newAssignClientToGroupCommand()
        .clientId(secondClientId)
        .groupId(groupId)
        .send()
        .join();

    // then
    Awaitility.await("Clients are assigned to the group and can be searched")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var clients =
                  camundaClient
                      .newClientsByGroupSearchRequest(groupId)
                      .sort(fn -> fn.clientId().desc())
                      .send()
                      .join();
              assertThat(clients.items().size()).isEqualTo(2);
              assertThat(clients.items())
                  .extracting(Client::getClientId)
                  .containsExactly(secondClientId, firstClientId);
            });
  }

  private static void createGroup(final String groupId) {
    camundaClient.newCreateGroupCommand().groupId(groupId).name("groupName").send().join();
  }
}
