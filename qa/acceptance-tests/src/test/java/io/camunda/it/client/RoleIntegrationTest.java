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
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.test.util.Strings;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class RoleIntegrationTest {

  private static CamundaClient camundaClient;

  private static final String ROLE_ID_1 = Strings.newRandomValidIdentityId();
  private static final String ROLE_ID_2 = Strings.newRandomValidIdentityId();
  private static final String ROLE_ID_3 = Strings.newRandomValidIdentityId();
  private static final String ROLE_ID_4 = Strings.newRandomValidIdentityId();
  private static final String ROLE_NAME_1 = "ARoleName";
  private static final String ROLE_NAME_2 = "BRoleName";
  private static final String ROLE_NAME_3 = "CRoleName";
  private static final String ROLE_NAME_4 = "CRoleName";
  private static final String DESCRIPTION = "description";

  @AutoClose private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @Test
  void shouldCreateAndGetRoleById() {
    // when
    camundaClient
        .newCreateRoleCommand()
        .roleId(ROLE_ID_1)
        .name(ROLE_NAME_1)
        .description(DESCRIPTION)
        .send()
        .join();

    // then
    assertRoleCreated(ROLE_ID_1, ROLE_NAME_1, DESCRIPTION);
  }

  @Test
  void shouldRejectCreationIfRoleIdAlreadyExists() {
    // given
    camundaClient
        .newCreateRoleCommand()
        .roleId(ROLE_ID_2)
        .name(ROLE_NAME_2)
        .description(DESCRIPTION)
        .send()
        .join();

    // when/then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newCreateRoleCommand()
                    .roleId(ROLE_ID_2)
                    .name(ROLE_NAME_2)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 409: 'Conflict'")
        .hasMessageContaining("a role with this ID already exists");
  }

  @Test
  void shouldRejectCreationIfMissingRoleId() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newRoleGetRequest(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleId must not be null");
  }

  @Test
  void shouldRejectCreationIfEmptyRoleId() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newRoleGetRequest("").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleId must not be empty");
  }

  @Test
  void shouldRejectCreationIfMissingRoleName() {
    // when / then
    assertThatThrownBy(
            () -> camundaClient.newCreateRoleCommand().roleId("someRoleId").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be null");
  }

  @Test
  void shouldReturnNotFoundWhenGetRoleDoesNotExist() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newRoleGetRequest("someRoleId").send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining("Role with role ID someRoleId not found");
  }

  @Test
  void shouldRejectGetRoleIfNullRoleId() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newRoleGetRequest(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleId must not be null");
  }

  @Test
  void shouldRejectGetRoleIfEmptyRoleId() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newRoleGetRequest("").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleId must not be empty");
  }

  @Test
  void shouldDeleteRoleById() {
    // given
    camundaClient
        .newCreateRoleCommand()
        .roleId(ROLE_ID_3)
        .name(ROLE_NAME_3)
        .description(DESCRIPTION)
        .send()
        .join();

    assertRoleCreated(ROLE_ID_3, ROLE_NAME_3, DESCRIPTION);

    // when
    camundaClient.newDeleteRoleCommand(ROLE_ID_3).send().join();

    // then
    Awaitility.await("Role is deleted")
        .untilAsserted(
            () ->
                assertThatThrownBy(() -> camundaClient.newRoleGetRequest(ROLE_ID_3).send().join())
                    .isInstanceOf(ProblemException.class)
                    .hasMessageContaining("Failed with code 404: 'Not Found'"));
  }

  @Test
  void shouldReturnNotFoundWhenRoleIdDoesNotExist() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newDeleteRoleCommand("someRoleId").send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining("a role with this ID doesn't exist");
  }

  @Test
  void shouldRejectDeletionIfNullRoleId() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newDeleteRoleCommand(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleId must not be null");
  }

  @Test
  void shouldRejectDeletionIfEmptyRoleId() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newDeleteRoleCommand("").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleId must not be empty");
  }

  @Test
  void shouldDeleteAuthorizationsWhenDeletingRole() {
    // when
    camundaClient
        .newCreateRoleCommand()
        .roleId(ROLE_ID_4)
        .name(ROLE_NAME_4)
        .description(DESCRIPTION)
        .send()
        .join();

    assertRoleCreated(ROLE_ID_4, ROLE_NAME_4, DESCRIPTION);

    camundaClient
        .newCreateAuthorizationCommand()
        .ownerId(ROLE_ID_4)
        .ownerType(OwnerType.ROLE)
        .resourceId("resourceId")
        .resourceType(ResourceType.RESOURCE)
        .permissionTypes(PermissionType.CREATE, PermissionType.READ)
        .send()
        .join();

    // Verify it was created
    Awaitility.await()
        .untilAsserted(
            () ->
                assertThat(
                        searchAuthorizations(
                                camundaClient.getConfiguration().getRestAddress().toString(),
                                "admin")
                            .items())
                    .anyMatch(
                        auth ->
                            auth.resourceId().equals("resourceId")
                                && auth.resourceType().equals(ResourceType.RESOURCE)
                                && auth.ownerId().equals(ROLE_ID_4)));

    camundaClient.newDeleteRoleCommand(ROLE_ID_4).send().join();

    // then
    Awaitility.await("Role is deleted")
        .untilAsserted(
            () ->
                assertThatThrownBy(() -> camundaClient.newRoleGetRequest(ROLE_ID_4).send().join())
                    .isInstanceOf(ProblemException.class)
                    .hasMessageContaining("Failed with code 404: 'Not Found'"));

    Awaitility.await("Authorization is deleted")
        .untilAsserted(
            () ->
                assertThat(
                        searchAuthorizations(
                                camundaClient.getConfiguration().getRestAddress().toString(),
                                "admin")
                            .items())
                    .noneMatch(
                        auth ->
                            auth.resourceId().equals("resourceId")
                                && auth.resourceType().equals(ResourceType.RESOURCE)
                                && auth.ownerId().equals(ROLE_ID_4)));
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
              assertThat(role.getRoleKey()).isPositive();
            });
  }

  // TODO once available, this test should use the client to make the request
  private static AuthorizationSearchResponse searchAuthorizations(
      final String restAddress, final String username)
      throws URISyntaxException, IOException, InterruptedException {
    final var encodedCredentials =
        Base64.getEncoder().encodeToString("%s:%s".formatted(username, "password").getBytes());
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(new URI("%s%s".formatted(restAddress, "v2/authorizations/search")))
            .POST(HttpRequest.BodyPublishers.ofString(""))
            .header("Authorization", "Basic %s".formatted(encodedCredentials))
            .build();

    final HttpResponse<String> response =
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    return OBJECT_MAPPER.readValue(response.body(), AuthorizationSearchResponse.class);
  }

  private record AuthorizationSearchResponse(
      List<RoleIntegrationTest.AuthorizationResponse> items) {}

  private record AuthorizationResponse(
      String ownerId,
      OwnerType ownerType,
      ResourceType resourceType,
      String resourceId,
      List<PermissionType> permissionTypes,
      String authorizationKey) {}
}
