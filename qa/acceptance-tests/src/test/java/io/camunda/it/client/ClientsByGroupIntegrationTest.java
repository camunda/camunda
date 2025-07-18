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
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
public class ClientsByGroupIntegrationTest {

  private static CamundaClient camundaClient;

  @Test
  void shouldAssignClientToGroup() {
    // given
    final var clientId = "clientId";
    final var groupId = Strings.newRandomValidIdentityId();
    final var groupName = UUID.randomUUID().toString();
    final var description = UUID.randomUUID().toString();
    createGroup(groupId, groupName, description);

    // when
    camundaClient.newAssignClientToGroupCommand().clientId(clientId).groupId(groupId).send().join();

    // then
    Awaitility.await("Client is assigned to the group")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final SearchResponse<Client> result =
                  camundaClient.newClientsByGroupSearchRequest(groupId).send().join();
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
    final var groupId = Strings.newRandomValidIdentityId();
    final var groupName = UUID.randomUUID().toString();
    final var description = UUID.randomUUID().toString();
    createGroup(groupId, groupName, description);

    camundaClient.newAssignClientToGroupCommand().clientId(clientId).groupId(groupId).send().join();

    Awaitility.await("Client is assigned to the group")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final SearchResponse<Client> result =
                  camundaClient.newClientsByGroupSearchRequest(groupId).send().join();
              assertThat(result.items()).anyMatch(r -> clientId.equals(r.getClientId()));
            });

    // when
    camundaClient
        .newUnassignClientFromGroupCommand()
        .clientId(clientId)
        .groupId(groupId)
        .send()
        .join();

    // then
    Awaitility.await("Client is unassigned from the group")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final SearchResponse<Client> result =
                  camundaClient.newClientsByGroupSearchRequest(groupId).send().join();
              assertThat(result.items()).noneMatch(r -> clientId.equals(r.getClientId()));
            });
  }

  @Test
  void shouldRejectUnassigningIfClientIsNotAssignedToGroup() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();
    final var clientId = Strings.newRandomValidIdentityId();
    camundaClient.newCreateGroupCommand().groupId(groupId).name("groupName").send().join();

    // when/then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUnassignClientFromGroupCommand()
                    .clientId(clientId)
                    .groupId(groupId)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining(
            "Expected to remove entity with ID '"
                + clientId
                + "' from group with ID '"
                + groupId
                + "', but the entity is not assigned to this group.");
  }

  private static void createGroup(
      final String groupId, final String groupName, final String description) {
    camundaClient
        .newCreateGroupCommand()
        .groupId(groupId)
        .name(groupName)
        .description(description)
        .send()
        .join();
  }
}
