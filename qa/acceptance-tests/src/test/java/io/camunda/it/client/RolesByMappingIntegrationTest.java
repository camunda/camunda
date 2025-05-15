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
import io.camunda.client.protocol.rest.MappingSearchQueryResult;
import io.camunda.qa.util.multidb.MultiDbTest;
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

@MultiDbTest
public class RolesByMappingIntegrationTest {

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
              assertThat(role.getRoleKey()).isPositive();
            });
  }

  @Test
  void shouldAssignRoleToMapping() {
    final var mappingId = Strings.newRandomValidIdentityId();

    camundaClient
        .newCreateMappingCommand()
        .mappingId(mappingId)
        .name("mappingName")
        .claimName("testClaimName")
        .claimValue("testClaimValue")
        .send()
        .join();

    camundaClient
        .newAssignRoleToMappingCommand()
        .roleId(EXISTING_ROLE_ID)
        .mappingId(mappingId)
        .send()
        .join();

    Awaitility.await("Mapping is assigned to the role")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () ->
                assertThat(
                        searchMappingRuleByRole(
                                camundaClient.getConfiguration().getRestAddress().toString(),
                                EXISTING_ROLE_ID)
                            .getItems())
                    .hasSize(1)
                    .anyMatch(m -> mappingId.equals(m.getMappingId())));
  }

  @Test
  void shouldUnassignRoleFromMappingOnRoleDeletion() {
    // given
    final var roleId = Strings.newRandomValidIdentityId();
    final var mappingId = Strings.newRandomValidIdentityId();

    createRole(roleId, "ARoleName", "description");

    camundaClient
        .newCreateMappingCommand()
        .mappingId(mappingId)
        .name("mappingName")
        .claimName("aClaimName")
        .claimValue("aClaimValue")
        .send()
        .join();

    camundaClient.newAssignRoleToMappingCommand().roleId(roleId).mappingId(mappingId).send().join();

    Awaitility.await("Mapping is assigned to the role")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () ->
                assertThat(
                        searchMappingRuleByRole(
                                camundaClient.getConfiguration().getRestAddress().toString(),
                                roleId)
                            .getItems())
                    .hasSize(1)
                    .anyMatch(m -> mappingId.equals(m.getMappingId())));

    // when
    camundaClient.newDeleteRoleCommand(roleId).send().join();

    // then
    Awaitility.await("Mapping is unassigned from deleted role")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () ->
                assertThat(
                        searchMappingRuleByRole(
                                camundaClient.getConfiguration().getRestAddress().toString(),
                                roleId)
                            .getItems())
                    .hasSize(0)
                    .noneMatch(m -> mappingId.equals(m.getMappingId())));
  }

  @Test
  void shouldRejectAssigningRoleIfRoleAlreadyAssignedToMapping() {
    // given
    final var mappingId = Strings.newRandomValidIdentityId();

    camundaClient
        .newCreateMappingCommand()
        .mappingId(mappingId)
        .name("mappingName")
        .claimName("someClaimName")
        .claimValue("someClaimValue")
        .send()
        .join();

    camundaClient
        .newAssignRoleToMappingCommand()
        .roleId(EXISTING_ROLE_ID)
        .mappingId(mappingId)
        .send()
        .join();

    // when/then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignRoleToMappingCommand()
                    .roleId(EXISTING_ROLE_ID)
                    .mappingId(mappingId)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining(
            "Expected to add entity with ID '"
                + mappingId
                + "' to role with ID '"
                + EXISTING_ROLE_ID
                + "', but the entity is already assigned to this role.");
  }

  @Test
  void shouldReturnNotFoundOnAssigningRoleToMappingIfRoleDoesNotExist() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignRoleToMappingCommand()
                    .roleId(Strings.newRandomValidIdentityId())
                    .mappingId(Strings.newRandomValidIdentityId())
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining("a role with this ID does not exist");
  }

  @Test
  void shouldReturnNotFoundOnAssigningRoleToMappingIfMappingDoesNotExist() {
    // given
    final var mappingId = Strings.newRandomValidIdentityId();

    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignRoleToMappingCommand()
                    .roleId(EXISTING_ROLE_ID)
                    .mappingId(mappingId)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining(
            "Expected to add an entity with ID '"
                + mappingId
                + "' and type 'MAPPING' to role with ID '"
                + EXISTING_ROLE_ID
                + "', but the entity doesn't exist.");
  }

  @Test
  void shouldRejectAssigningRoleToMappingIfMissingMappingId() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignRoleToMappingCommand()
                    .roleId(EXISTING_ROLE_ID)
                    .mappingId(null)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("mappingId must not be null");
  }

  @Test
  void shouldRejectAssigningRoleToMappingIfMissingRoleId() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignRoleToMappingCommand()
                    .roleId(null)
                    .mappingId(Strings.newRandomValidIdentityId())
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleId must not be null");
  }

  // TODO once available, this test should use the client to make the request
  private static MappingSearchQueryResult searchMappingRuleByRole(
      final String restAddress, final String roleId)
      throws URISyntaxException, IOException, InterruptedException {
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(
                new URI(
                    "%s%s".formatted(restAddress, "v2/roles/" + roleId + "/mapping-rules/search")))
            .POST(HttpRequest.BodyPublishers.ofString(""))
            .build();

    final HttpResponse<String> response =
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    return OBJECT_MAPPER.readValue(response.body(), MappingSearchQueryResult.class);
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
