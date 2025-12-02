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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.collection.Tuple;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

@MultiDbTest
public class RolesByTenantIT {

  private static final String ADMIN_USERNAME = "admin";
  private static final String PASSWORD = "password";

  private static CamundaClient camundaClient;
  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  @AutoClose private final HttpClient httpClient = HttpClient.newHttpClient();

  @Test
  void shouldAssignRoleToTenant() {
    // given
    final var tenantId = Strings.newRandomValidTenantId();
    final var roleId = "role-" + Strings.newRandomValidIdentityId();
    final var roleName = "name-" + Strings.newRandomValidIdentityId();
    final var roleDesc = "desc-" + Strings.newRandomValidIdentityId();
    createTenant(tenantId);
    createRole(roleId, roleName, roleDesc);
    // when
    camundaClient.newAssignRoleToTenantCommand().roleId(roleId).tenantId(tenantId).send().join();
    // then
    Awaitility.await("Role should be visible in tenant role search")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var roles = camundaClient.newRolesByTenantSearchRequest(tenantId).send().join();
              assertThat(roles.items())
                  .singleElement()
                  .satisfies(
                      role -> {
                        assertThat(role.getRoleId()).isEqualTo(roleId);
                        assertThat(role.getName()).isEqualTo(roleName);
                        assertThat(role.getDescription()).isEqualTo(roleDesc);
                      });
            });
  }

  @Test
  void shouldRejectIfTenantDoesNotExist() {
    // given
    final var nonExistingTenantId = Strings.newRandomValidTenantId();
    final var roleId = "role-" + Strings.newRandomValidIdentityId();
    final var roleName = "name-" + Strings.newRandomValidIdentityId();
    final var roleDesc = "desc-" + Strings.newRandomValidIdentityId();
    createRole(roleId, roleName, roleDesc);
    // when and then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignRoleToTenantCommand()
                    .roleId(roleId)
                    .tenantId(nonExistingTenantId)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("404: 'Not Found'")
        .hasMessageContaining("no tenant with this ID exists");
  }

  @Test
  void shouldRejectIfRoleDoesNotExist() {
    final var tenantId = Strings.newRandomValidTenantId();
    final var roleId = "role-" + Strings.newRandomValidIdentityId();
    createTenant(tenantId);
    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignRoleToTenantCommand()
                    .roleId(roleId)
                    .tenantId(tenantId)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("404: 'Not Found'")
        .hasMessageContaining("role doesn't exist");
  }

  @Test
  void shouldRejectIfRoleAlreadyAssigned() {
    // given
    final var tenantId = Strings.newRandomValidTenantId();
    final var roleId = "role-" + Strings.newRandomValidIdentityId();
    final var roleName = "name-" + Strings.newRandomValidIdentityId();
    final var roleDesc = "desc-" + Strings.newRandomValidIdentityId();
    createTenant(tenantId);
    createRole(roleId, roleName, roleDesc);
    camundaClient.newAssignRoleToTenantCommand().roleId(roleId).tenantId(tenantId).send().join();
    // when and then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignRoleToTenantCommand()
                    .roleId(roleId)
                    .tenantId(tenantId)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("409: 'Conflict'")
        .hasMessageContaining("the role is already assigned to the tenant");
  }

  @Test
  void shouldReturnAllRolesByTenant() {
    // given
    final var tenantId = Strings.newRandomValidTenantId();
    final var firstRoleId = "role-" + Strings.newRandomValidIdentityId();
    final var secondRoleId = "role-" + Strings.newRandomValidIdentityId();
    createTenant(tenantId);
    createRole(firstRoleId, "name1", "desc1");
    createRole(secondRoleId, "name2", "desc2");

    camundaClient
        .newAssignRoleToTenantCommand()
        .roleId(firstRoleId)
        .tenantId(tenantId)
        .send()
        .join();
    camundaClient
        .newAssignRoleToTenantCommand()
        .roleId(secondRoleId)
        .tenantId(tenantId)
        .send()
        .join();

    // when and then
    Awaitility.await("Roles should be visible in tenant role search")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var roles = camundaClient.newRolesByTenantSearchRequest(tenantId).send().join();
              assertThat(roles.items())
                  .extracting("roleId")
                  .containsExactlyInAnyOrder(firstRoleId, secondRoleId);
            });
  }

  @Test
  void shouldReturnEmptyListForTenantWithoutRoles() {
    // given
    final String emptyTenant = Strings.newRandomValidTenantId();
    createTenant(emptyTenant);
    waitForTenantsToBeCreated(emptyTenant);
    // when
    final var roles = camundaClient.newRolesByTenantSearchRequest(emptyTenant).send().join();
    // then
    assertThat(roles.items()).isEmpty();
  }

  @Test
  void shouldRejectSearchIfTenantIdIsNull() {
    assertThatThrownBy(() -> camundaClient.newRolesByTenantSearchRequest(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId must not be null");
  }

  @Test
  void shouldRejectSearchIfTenantIdIsEmpty() {
    assertThatThrownBy(() -> camundaClient.newRolesByTenantSearchRequest("").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId must not be empty");
  }

  @Test
  void shouldUnassignRoleFromTenant() {
    // given
    final var tenantId = Strings.newRandomValidTenantId();
    final var roleId = "role-" + Strings.newRandomValidIdentityId();
    createTenant(tenantId);
    createRole(roleId, "name", "desc");
    camundaClient.newAssignRoleToTenantCommand().roleId(roleId).tenantId(tenantId).send().join();
    // when
    camundaClient
        .newUnassignRoleFromTenantCommand()
        .roleId(roleId)
        .tenantId(tenantId)
        .send()
        .join();
    // then
    Awaitility.await("Role should be unassigned from tenant")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var roles = camundaClient.newRolesByTenantSearchRequest(tenantId).send().join();
              assertThat(roles.items()).noneMatch(role -> role.getRoleId().equals(roleId));
            });
  }

  @Test
  void shouldRejectUnassignIfRoleNotAssignedToTenant() {
    // given
    final var tenantId = Strings.newRandomValidTenantId();
    final var roleId = "role-" + Strings.newRandomValidIdentityId();
    createTenant(tenantId);
    createRole(roleId, "name", "desc");
    Awaitility.await("Role should be removed from tenant")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var roles = camundaClient.newRolesByTenantSearchRequest(tenantId).send().join();
              assertThat(roles.items()).noneMatch(role -> role.getRoleId().equals(roleId));
            });
    // when and then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUnassignRoleFromTenantCommand()
                    .roleId(roleId)
                    .tenantId(tenantId)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("404: 'Not Found'")
        .hasMessageContaining("the role is not assigned to this tenant");
  }

  @Test
  void shouldRejectUnassignWithNonExistingTenantId() {
    final var tenantId = Strings.newRandomValidTenantId();
    final var roleId = "role-" + Strings.newRandomValidIdentityId();
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUnassignRoleFromTenantCommand()
                    .roleId(roleId)
                    .tenantId(tenantId)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("404: 'Not Found'")
        .hasMessageContaining("no tenant with this ID exists");
  }

  @Test
  void shouldRejectUnassignWithNonExistingRoleId() {
    final var tenantId = Strings.newRandomValidTenantId();
    createTenant(tenantId);
    final var roleId = "non-existing-" + Strings.newRandomValidIdentityId();
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUnassignRoleFromTenantCommand()
                    .roleId(roleId)
                    .tenantId(tenantId)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("404: 'Not Found'")
        .hasMessageContaining("the role is not assigned to this tenant");
  }

  private static void createTenant(final String tenantId) {
    camundaClient.newCreateTenantCommand().tenantId(tenantId).name("tenant name").send().join();
  }

  private static void createRole(final String roleId, final String name, final String description) {
    camundaClient
        .newCreateRoleCommand()
        .roleId(roleId)
        .name(name)
        .description(description)
        .send()
        .join();
  }

  private void waitForTenantsToBeCreated(final String tenantId) {
    Awaitility.await("should create tenants and import in ES")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var tenantById = getTenantById(tenantId);
              // Validate the response
              assert tenantById.isRight();
            });
  }

  // TODO once available, this test should use the client to make the request
  private Either<Tuple<HttpStatus, String>, TenantResponse> getTenantById(final String tenantId)
      throws URISyntaxException, IOException, InterruptedException {
    final var encodedCredentials =
        Base64.getEncoder().encodeToString("%s:%s".formatted(ADMIN_USERNAME, PASSWORD).getBytes());
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(
                new URI(
                    "%s%s"
                        .formatted(
                            camundaClient.getConfiguration().getRestAddress().toString(),
                            "v2/tenants/%s".formatted(tenantId))))
            .GET()
            .header("Authorization", "Basic %s".formatted(encodedCredentials))
            .build();

    // Send the request and get the response
    final HttpResponse<String> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() == HttpStatus.OK.value()) {
      return Either.right(OBJECT_MAPPER.readValue(response.body(), TenantResponse.class));
    }
    return Either.left(Tuple.of(HttpStatus.resolve(response.statusCode()), response.body()));
  }

  private record TenantResponse(String tenantId) {}
}
