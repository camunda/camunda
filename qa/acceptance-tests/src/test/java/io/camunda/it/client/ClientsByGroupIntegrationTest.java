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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.protocol.rest.GroupClientSearchResult;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.test.util.Strings;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
public class ClientsByGroupIntegrationTest {
  private static CamundaClient camundaClient;

  @AutoClose private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

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
              final GroupClientSearchResult result =
                  searchClientsByGroupId(
                      camundaClient.getConfiguration().getRestAddress().toString(), groupId);
              assertThat(result.getItems()).anyMatch(r -> clientId.equals(r.getClientId()));
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
              final GroupClientSearchResult result =
                  searchClientsByGroupId(
                      camundaClient.getConfiguration().getRestAddress().toString(), groupId);
              assertThat(result.getItems()).anyMatch(r -> clientId.equals(r.getClientId()));
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
              final GroupClientSearchResult result =
                  searchClientsByGroupId(
                      camundaClient.getConfiguration().getRestAddress().toString(), groupId);
              assertThat(result.getItems()).noneMatch(r -> clientId.equals(r.getClientId()));
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

  // TODO: will be removed in the next PR for client search
  private static GroupClientSearchResult searchClientsByGroupId(
      final String restAddress, final String groupId)
      throws URISyntaxException, IOException, InterruptedException {
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(new URI("%s%s".formatted(restAddress, "v2/groups/" + groupId + "/clients/search")))
            .POST(HttpRequest.BodyPublishers.ofString(""))
            .build();

    final HttpResponse<String> response =
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    return OBJECT_MAPPER.readValue(response.body(), GroupClientSearchResult.class);
  }
}
