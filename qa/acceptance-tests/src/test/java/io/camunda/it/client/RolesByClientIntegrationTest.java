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
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.gateway.protocol.rest.RoleClientSearchResult;
import io.camunda.zeebe.test.util.Strings;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
public class RolesByClientIntegrationTest {
  private static CamundaClient camundaClient;

  private static final String EXISTING_ROLE_ID = Strings.newRandomValidIdentityId();

  @AutoClose private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @BeforeAll
  static void setup() {
    createRole(EXISTING_ROLE_ID, "ARoleName", "description");

    Awaitility.await("Role is created and exported")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var role = camundaClient.newRoleGetRequest(EXISTING_ROLE_ID).send().join();
              assertThat(role).isNotNull();
              assertThat(role.getRoleId()).isEqualTo(EXISTING_ROLE_ID);
              assertThat(role.getName()).isEqualTo("ARoleName");
              assertThat(role.getDescription()).isEqualTo("description");
            });
  }

  @Test
  void shouldAssignRoleToClient() {
    // given
    final var clientId = Strings.newRandomValidIdentityId();

    // when
    camundaClient
        .newAssignRoleToClientCommand()
        .roleId(EXISTING_ROLE_ID)
        .clientId(clientId)
        .send()
        .join();

    // then
    Awaitility.await("Role is assigned to the client")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () ->
                assertThat(
                        searchClientsByRoleId(
                                camundaClient.getConfiguration().getRestAddress().toString(),
                                EXISTING_ROLE_ID)
                            .getItems())
                    .anyMatch(r -> clientId.equals(r.getClientId())));
  }

  @Test
  void shouldUnassignRoleFromClient() {
    // given
    final var clientId = Strings.newRandomValidIdentityId();

    camundaClient
        .newAssignRoleToClientCommand()
        .roleId(EXISTING_ROLE_ID)
        .clientId(clientId)
        .send()
        .join();

    Awaitility.await("Role is assigned to the client")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () ->
                assertThat(
                        searchClientsByRoleId(
                                camundaClient.getConfiguration().getRestAddress().toString(),
                                EXISTING_ROLE_ID)
                            .getItems())
                    .anyMatch(r -> clientId.equals(r.getClientId())));

    // when
    camundaClient
        .newUnassignRoleFromClientCommand()
        .roleId(EXISTING_ROLE_ID)
        .clientId(clientId)
        .send()
        .join();

    // then
    Awaitility.await("Role is unassigned from the client")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () ->
                assertThat(
                        searchClientsByRoleId(
                                camundaClient.getConfiguration().getRestAddress().toString(),
                                EXISTING_ROLE_ID)
                            .getItems())
                    .isEmpty());
  }

  @Test
  void shouldUnassignRoleFromClientOnRoleDeletion() {
    // given
    final var clientId = Strings.newRandomValidIdentityId();
    final var roleId = Strings.newRandomValidIdentityId();

    createRole(roleId, "someRoleName", "description");

    camundaClient.newAssignRoleToClientCommand().roleId(roleId).clientId(clientId).send().join();

    // when
    camundaClient.newDeleteRoleCommand(roleId).send().join();

    // then
    Awaitility.await("Role is unassigned from the client")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () ->
                assertThat(
                        searchClientsByRoleId(
                                camundaClient.getConfiguration().getRestAddress().toString(),
                                EXISTING_ROLE_ID)
                            .getItems())
                    .isEmpty());
  }

  @Test
  void shouldRejectAssigningRoleIfRoleAlreadyAssignedToClient() {
    // given
    final var clientId = Strings.newRandomValidIdentityId();

    camundaClient
        .newAssignRoleToClientCommand()
        .roleId(EXISTING_ROLE_ID)
        .clientId(clientId)
        .send()
        .join();

    // when/then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignRoleToClientCommand()
                    .roleId(EXISTING_ROLE_ID)
                    .clientId(clientId)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining(
            "Expected to add entity with ID '"
                + clientId
                + "' to role with ID '"
                + EXISTING_ROLE_ID
                + "', but the entity is already assigned to this role.");
  }

  @Test
  void shouldRejectUnassigningRoleIfRoleNotAssignedToClient() {
    // given
    final var clientId = Strings.newRandomValidIdentityId();

    // when/then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUnassignRoleFromClientCommand()
                    .roleId(EXISTING_ROLE_ID)
                    .clientId(clientId)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining(
            "Expected to remove entity with ID '"
                + clientId
                + "' from role with ID '"
                + EXISTING_ROLE_ID
                + "', but the entity is not assigned to this role.");
  }

  @Test
  void shouldReturnNotFoundOnAssigningRoleToClientIfRoleDoesNotExist() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignRoleToClientCommand()
                    .roleId(Strings.newRandomValidIdentityId())
                    .clientId(Strings.newRandomValidIdentityId())
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining("a role with this ID does not exist");
  }

  @Test
  void shouldReturnNotFoundOnUnassigningRoleFromClientIfRoleDoesNotExist() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUnassignRoleFromClientCommand()
                    .roleId(Strings.newRandomValidIdentityId())
                    .clientId(Strings.newRandomValidIdentityId())
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining("a role with this ID does not exist");
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

  // TODO: will be removed in the next PR for client search
  private static RoleClientSearchResult searchClientsByRoleId(
      final String restAddress, final String roleId)
      throws URISyntaxException, IOException, InterruptedException {
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(new URI("%s%s".formatted(restAddress, "v2/roles/" + roleId + "/clients/search")))
            .POST(HttpRequest.BodyPublishers.ofString(""))
            .build();

    final HttpResponse<String> response =
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    return OBJECT_MAPPER.readValue(response.body(), RoleClientSearchResult.class);
  }
}
