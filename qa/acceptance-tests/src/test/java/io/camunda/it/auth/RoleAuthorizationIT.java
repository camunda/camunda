/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.CreateRoleResponse;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.api.search.response.Role;
import io.camunda.client.protocol.rest.MappingSearchQueryResult;
import io.camunda.client.protocol.rest.RoleResult;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.User;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.Strings;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class RoleAuthorizationIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private static final String ROLE_ID_1 = Strings.newRandomValidIdentityId();
  private static final String ROLE_ID_2 = Strings.newRandomValidIdentityId();
  private static final String ROLE_NAME_1 = "ARoleName";
  private static final String ROLE_NAME_2 = "BRoleName";
  private static final String ADMIN = "admin";
  private static final String RESTRICTED = "restrictedUser";
  private static final String RESTRICTED_WITH_READ = "restrictedUser2";
  private static final String DEFAULT_PASSWORD = "password";
  private static final String ROLE_SEARCH_ENDPOINT = "v2/roles/search";
  private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(15);

  @UserDefinition
  private static final User ADMIN_USER =
      new User(
          ADMIN,
          DEFAULT_PASSWORD,
          List.of(
              new Permissions(ResourceType.ROLE, PermissionType.CREATE, List.of("*")),
              new Permissions(ResourceType.ROLE, PermissionType.UPDATE, List.of("*")),
              new Permissions(ResourceType.ROLE, PermissionType.READ, List.of("*")),
              new Permissions(ResourceType.ROLE, PermissionType.DELETE, List.of("*")),
              new Permissions(ResourceType.GROUP, PermissionType.CREATE, List.of("*")),
              new Permissions(ResourceType.MAPPING_RULE, PermissionType.CREATE, List.of("*")),
              new Permissions(ResourceType.AUTHORIZATION, PermissionType.UPDATE, List.of("*"))));

  @UserDefinition
  private static final User RESTRICTED_USER = new User(RESTRICTED, DEFAULT_PASSWORD, List.of());

  @UserDefinition
  private static final User RESTRICTED_USER_WITH_READ_PERMISSION =
      new User(
          RESTRICTED_WITH_READ,
          DEFAULT_PASSWORD,
          List.of(new Permissions(ResourceType.ROLE, PermissionType.READ, List.of("*"))));

  @AutoClose private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  @BeforeAll
  static void setUp(@Authenticated(ADMIN) final CamundaClient adminClient) {
    createRole(adminClient, ROLE_ID_1, ROLE_NAME_1);
    createRole(adminClient, ROLE_ID_2, ROLE_NAME_2);
    waitForRolesToBeCreated(
        adminClient.getConfiguration().getRestAddress().toString(), ADMIN, ROLE_ID_1, ROLE_ID_2);
  }

  @Test
  void shouldCreateRoleAndGetRoleByIdIfAuthorized(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    final String roleId = Strings.newRandomValidIdentityId();
    final String name = "name";
    final String description = "description";

    final CreateRoleResponse createdRole =
        adminClient
            .newCreateRoleCommand()
            .roleId(roleId)
            .name(name)
            .description(description)
            .send()
            .join();

    assertThat(createdRole.getRoleId()).isEqualTo(roleId);
    assertThat(createdRole.getName()).isEqualTo(name);
    assertThat(createdRole.getDescription()).isEqualTo(description);

    waitForRolesToBeCreated(
        adminClient.getConfiguration().getRestAddress().toString(), ADMIN, roleId);

    final Role role = adminClient.newRoleGetRequest(roleId).send().join();

    assertThat(role.getRoleId()).isEqualTo(roleId);
    assertThat(role.getName()).isEqualTo(name);
    assertThat(role.getDescription()).isEqualTo(description);
    // clean up
    adminClient.newDeleteRoleCommand(roleId).send().join();
  }

  @Test
  void shouldDeleteRoleByIdIfAuthorized(@Authenticated(ADMIN) final CamundaClient adminClient) {
    final String roleId = Strings.newRandomValidIdentityId();
    final String name = "name";
    final String description = "description";

    adminClient
        .newCreateRoleCommand()
        .roleId(roleId)
        .name(name)
        .description(description)
        .send()
        .join();

    assertThatNoException()
        .isThrownBy(() -> adminClient.newDeleteRoleCommand(roleId).send().join());
  }

  @Test
  void deleteRoleShouldReturnForbiddenIfUnauthorized(
      @Authenticated(RESTRICTED_WITH_READ) final CamundaClient camundaClient) {
    assertThatThrownBy(
            () ->
                camundaClient
                    .newDeleteRoleCommand(Strings.newRandomValidIdentityId())
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("403: 'Forbidden'");
  }

  @Test
  void createRoleShouldReturnForbiddenIfUnauthorized(
      @Authenticated(RESTRICTED_WITH_READ) final CamundaClient camundaClient) {
    assertThatThrownBy(
            () ->
                camundaClient
                    .newCreateRoleCommand()
                    .roleId(Strings.newRandomValidIdentityId())
                    .name("name")
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("403: 'Forbidden'");
  }

  @Test
  void getRoleByIdShouldReturnRoleIfAuthorized(
      @Authenticated(RESTRICTED_WITH_READ) final CamundaClient camundaClient) {
    final var role = camundaClient.newRoleGetRequest(ROLE_ID_1).send().join();

    assertThat(role.getRoleId()).isEqualTo(ROLE_ID_1);
  }

  @Test
  void getRoleByIdShouldReturnNotFoundIfUnauthorized(
      @Authenticated(RESTRICTED) final CamundaClient camundaClient) {
    assertThatThrownBy(() -> camundaClient.newRoleGetRequest(ROLE_ID_1).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("404: 'Not Found'");
  }

  @Test
  void getRoleByIdShouldReturnNotFoundForNonExistentRoleIdIfAuthorized(
      @Authenticated(RESTRICTED_WITH_READ) final CamundaClient camundaClient) {
    assertThatThrownBy(
            () -> camundaClient.newRoleGetRequest(Strings.newRandomValidIdentityId()).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("404: 'Not Found'");
  }

  @Test
  void shouldAssignRoleToMappingIfAuthorized(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    final String roleId = Strings.newRandomValidIdentityId();
    final String mappingId = Strings.newRandomValidIdentityId();

    createRole(adminClient, roleId, "roleName");
    adminClient
        .newCreateMappingCommand()
        .mappingId(mappingId)
        .name("mappingName")
        .claimName("testClaimName")
        .claimValue("testClaimValue")
        .send()
        .join();

    adminClient.newAssignRoleToMappingCommand().roleId(roleId).mappingId(mappingId).send().join();

    Awaitility.await("Mapping is assigned to the role")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () ->
                assertThat(
                        searchMappingRuleByRole(
                                adminClient.getConfiguration().getRestAddress().toString(),
                                ADMIN,
                                roleId)
                            .getItems())
                    .hasSize(1)
                    .anyMatch(m -> mappingId.equals(m.getMappingId())));

    adminClient.newDeleteRoleCommand(roleId).send().join();
  }

  @Test
  void assignRoleToMappingShouldReturnForbiddenIfUnauthorized(
      @Authenticated(RESTRICTED_WITH_READ) final CamundaClient camundaClient) {
    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignRoleToMappingCommand()
                    .roleId(Strings.newRandomValidIdentityId())
                    .mappingId(Strings.newRandomValidIdentityId())
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("403: 'Forbidden'");
  }

  @Test
  void searchShouldReturnAuthorizedRoles(
      @Authenticated(RESTRICTED_WITH_READ) final CamundaClient camundaClient) throws Exception {
    final var roleSearchResponse =
        searchRoles(
            camundaClient.getConfiguration().getRestAddress().toString(), RESTRICTED_WITH_READ);

    assertThat(roleSearchResponse.items())
        .map(RoleResult::getName)
        .containsExactlyInAnyOrder("Admin", "RPA", "Connectors", ROLE_NAME_1, ROLE_NAME_2);
  }

  @Test
  void searchShouldReturnEmptyListWhenUnauthorized(
      @Authenticated(RESTRICTED) final CamundaClient camundaClient) throws Exception {
    final var roleSearchResponse =
        searchRoles(camundaClient.getConfiguration().getRestAddress().toString(), RESTRICTED);

    assertThat(roleSearchResponse.items()).hasSize(0).map(RoleResult::getName).isEmpty();
  }

  @Test
  void shouldUpdateRoleByIdIfAuthorized(@Authenticated(ADMIN) final CamundaClient adminClient) {
    final String roleId = Strings.newRandomValidIdentityId();
    final String name = "name";
    final String description = "description";

    createRole(adminClient, roleId, name, description);

    final var updatedRole =
        adminClient
            .newUpdateRoleCommand(roleId)
            .name("updatedName")
            .description("updatedDescription")
            .send()
            .join();

    assertThat(updatedRole.getRoleId()).isEqualTo(roleId);
    // cleanup
    adminClient.newDeleteRoleCommand(roleId).send().join();
  }

  @Test
  void updateRoleShouldReturnForbiddenIfUnauthorized(
      @Authenticated(RESTRICTED_WITH_READ) final CamundaClient camundaClient) {
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUpdateRoleCommand(Strings.newRandomValidIdentityId())
                    .name("name")
                    .description("description")
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("403: 'Forbidden'");
  }

  @Test
  void shouldAssignRoleToGroupIfAuthorized(@Authenticated(ADMIN) final CamundaClient adminClient) {
    final String roleId = Strings.newRandomValidIdentityId();
    final String groupId = Strings.newRandomValidIdentityId();

    createRole(adminClient, roleId, "roleName");
    adminClient.newCreateGroupCommand().groupId(groupId).name("groupName").send().join();
    adminClient.newAssignRoleToGroupCommand().roleId(roleId).groupId(groupId).send().join();

    Awaitility.await("Group is assigned to the role")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () ->
                assertThat(
                        searchRolesByGroupId(
                                adminClient.getConfiguration().getRestAddress().toString(),
                                ADMIN,
                                groupId)
                            .items())
                    .anyMatch(r -> roleId.equals(r.getRoleId())));

    adminClient.newDeleteRoleCommand(roleId).send().join();
  }

  @Test
  void assignRoleToGroupShouldReturnForbiddenIfUnauthorized(
      @Authenticated(RESTRICTED_WITH_READ) final CamundaClient camundaClient) {
    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignRoleToGroupCommand()
                    .roleId(Strings.newRandomValidIdentityId())
                    .groupId(Strings.newRandomValidIdentityId())
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("403: 'Forbidden'");
  }

  @Test
  void shouldUnassignRoleFromGroupIfAuthorized(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    final String groupId = Strings.newRandomValidIdentityId();

    adminClient.newCreateGroupCommand().groupId(groupId).name("groupName").send().join();
    adminClient.newAssignRoleToGroupCommand().roleId(ROLE_ID_1).groupId(groupId).send().join();
    adminClient.newUnassignRoleFromGroupCommand().roleId(ROLE_ID_1).groupId(groupId).send().join();

    Awaitility.await("Group is unassigned from the role")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () ->
                assertThat(
                        searchRolesByGroupId(
                                adminClient.getConfiguration().getRestAddress().toString(),
                                ADMIN,
                                groupId)
                            .items())
                    .isEmpty());
  }

  @Test
  void unassignRoleFromGroupShouldReturnForbiddenIfUnauthorized(
      @Authenticated(RESTRICTED_WITH_READ) final CamundaClient camundaClient) {
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUnassignRoleFromGroupCommand()
                    .roleId(Strings.newRandomValidIdentityId())
                    .groupId(Strings.newRandomValidIdentityId())
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("403: 'Forbidden'");
  }

  private static void createRole(
      final CamundaClient adminClient, final String roleId, final String roleName) {
    createRole(adminClient, roleId, roleName, null);
  }

  private static void createRole(
      final CamundaClient adminClient,
      final String roleId,
      final String roleName,
      final String description) {
    adminClient
        .newCreateRoleCommand()
        .roleId(roleId)
        .name(roleName)
        .description(description)
        .send()
        .join();
  }

  private static void waitForRolesToBeCreated(
      final String restAddress, final String name, final String... roleIds) {
    Awaitility.await("should create roles and import in ES")
        .atMost(AWAIT_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var roleSearchResponse = searchRoles(restAddress, name);
              assertThat(roleSearchResponse.items().stream().map(RoleResult::getRoleId).toList())
                  .containsAll(Arrays.asList(roleIds));
            });
  }

  // TODO replace with role search when implemented in Camunda Client #31716
  private static RoleSearchResponse searchRoles(final String restAddress, final String username)
      throws URISyntaxException, IOException, InterruptedException {
    final var encodedCredentials =
        Base64.getEncoder()
            .encodeToString("%s:%s".formatted(username, DEFAULT_PASSWORD).getBytes());
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(new URI("%s%s".formatted(restAddress, ROLE_SEARCH_ENDPOINT)))
            .POST(HttpRequest.BodyPublishers.ofString(""))
            .header("Authorization", "Basic %s".formatted(encodedCredentials))
            .build();

    final HttpResponse<String> response =
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

    return OBJECT_MAPPER.readValue(response.body(), RoleSearchResponse.class);
  }

  // TODO once available, this test should use the client to make the request
  private static RoleSearchResponse searchRolesByGroupId(
      final String restAddress, final String username, final String groupId)
      throws URISyntaxException, IOException, InterruptedException {
    final var encodedCredentials =
        Base64.getEncoder()
            .encodeToString("%s:%s".formatted(username, DEFAULT_PASSWORD).getBytes());
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(new URI("%s%s".formatted(restAddress, "v2/groups/" + groupId + "/roles/search")))
            .POST(HttpRequest.BodyPublishers.ofString(""))
            .header("Authorization", "Basic %s".formatted(encodedCredentials))
            .build();

    final HttpResponse<String> response =
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    return OBJECT_MAPPER.readValue(response.body(), RoleSearchResponse.class);
  }

  // TODO once available, this test should use the client to make the request
  private static MappingSearchQueryResult searchMappingRuleByRole(
      final String restAddress, final String username, final String roleId)
      throws URISyntaxException, IOException, InterruptedException {
    final var encodedCredentials =
        Base64.getEncoder()
            .encodeToString("%s:%s".formatted(username, DEFAULT_PASSWORD).getBytes());
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(
                new URI(
                    "%s%s".formatted(restAddress, "v2/roles/" + roleId + "/mapping-rules/search")))
            .POST(HttpRequest.BodyPublishers.ofString(""))
            .header("Authorization", "Basic %s".formatted(encodedCredentials))
            .build();

    final HttpResponse<String> response =
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    return OBJECT_MAPPER.readValue(response.body(), MappingSearchQueryResult.class);
  }

  private record RoleSearchResponse(List<RoleResult> items) {}
}
