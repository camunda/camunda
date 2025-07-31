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

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.CreateRoleResponse;
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.api.search.response.Client;
import io.camunda.client.api.search.response.Role;
import io.camunda.client.api.search.response.RoleGroup;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.Strings;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.awaitility.Awaitility;
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

  private static final String ROLE_ID_1 = Strings.newRandomValidIdentityId();
  private static final String ROLE_ID_2 = Strings.newRandomValidIdentityId();
  private static final String ROLE_NAME_1 = "ARoleName";
  private static final String ROLE_NAME_2 = "BRoleName";
  private static final String ADMIN = "admin";
  private static final String RESTRICTED = "restrictedUser";
  private static final String RESTRICTED_WITH_READ = "restrictedUser2";
  private static final String DEFAULT_PASSWORD = "password";
  private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(15);

  @UserDefinition
  private static final TestUser ADMIN_USER =
      new TestUser(
          ADMIN,
          DEFAULT_PASSWORD,
          List.of(
              new Permissions(ResourceType.ROLE, PermissionType.CREATE, List.of("*")),
              new Permissions(ResourceType.ROLE, PermissionType.UPDATE, List.of("*")),
              new Permissions(ResourceType.ROLE, PermissionType.READ, List.of("*")),
              new Permissions(ResourceType.ROLE, PermissionType.DELETE, List.of("*")),
              new Permissions(ResourceType.GROUP, PermissionType.CREATE, List.of("*")),
              new Permissions(ResourceType.GROUP, PermissionType.READ, List.of("*")),
              new Permissions(ResourceType.GROUP, PermissionType.UPDATE, List.of("*")),
              new Permissions(ResourceType.MAPPING_RULE, PermissionType.CREATE, List.of("*")),
              new Permissions(ResourceType.MAPPING_RULE, PermissionType.READ, List.of("*")),
              new Permissions(ResourceType.TENANT, PermissionType.CREATE, List.of("*")),
              new Permissions(ResourceType.TENANT, PermissionType.UPDATE, List.of("*")),
              new Permissions(ResourceType.AUTHORIZATION, PermissionType.CREATE, List.of("*")),
              new Permissions(ResourceType.AUTHORIZATION, PermissionType.READ, List.of("*")),
              new Permissions(ResourceType.AUTHORIZATION, PermissionType.UPDATE, List.of("*"))));

  @UserDefinition
  private static final TestUser RESTRICTED_USER =
      new TestUser(RESTRICTED, DEFAULT_PASSWORD, List.of());

  @UserDefinition
  private static final TestUser RESTRICTED_USER_WITH_READ_PERMISSION =
      new TestUser(
          RESTRICTED_WITH_READ,
          DEFAULT_PASSWORD,
          List.of(
              new Permissions(
                  ResourceType.ROLE, PermissionType.READ, List.of(ROLE_ID_1, ROLE_ID_2))));

  @BeforeAll
  static void setUp(@Authenticated(ADMIN) final CamundaClient adminClient) {
    createRole(adminClient, ROLE_ID_1, ROLE_NAME_1);
    createRole(adminClient, ROLE_ID_2, ROLE_NAME_2);

    Awaitility.await("should create roles and import in ES")
        .atMost(AWAIT_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var roleSearchResponse = adminClient.newRolesSearchRequest().send().join();
              assertThat(roleSearchResponse.items().stream().map(Role::getRoleId).toList())
                  .containsAll(Arrays.asList(ADMIN, ROLE_ID_1, ROLE_ID_2));
            });
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

    Awaitility.await("should create role and import in ES")
        .atMost(AWAIT_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var roleSearchResponse = adminClient.newRolesSearchRequest().send().join();
              assertThat(roleSearchResponse.items().stream().map(Role::getRoleId).toList())
                  .contains(roleId);
            });

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
        .hasMessageContaining("403: 'Forbidden'");
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
  void searchRolesShouldReturnRoleByIdIfAuthorized(
      @Authenticated(RESTRICTED_WITH_READ) final CamundaClient camundaClient) {
    final var roleSearchResponse =
        camundaClient.newRolesSearchRequest().filter(fn -> fn.roleId(ROLE_ID_1)).send().join();

    assertThat(roleSearchResponse.items())
        .hasSize(1)
        .map(Role::getRoleId)
        .containsExactly(ROLE_ID_1);
  }

  @Test
  void searchRolesShouldReturnRoleByNameIfAuthorized(
      @Authenticated(RESTRICTED_WITH_READ) final CamundaClient camundaClient) {
    final var roleSearchResponse =
        camundaClient.newRolesSearchRequest().filter(fn -> fn.name(ROLE_NAME_1)).send().join();

    assertThat(roleSearchResponse.items())
        .hasSize(1)
        .map(Role::getName)
        .containsExactly(ROLE_NAME_1);
  }

  @Test
  void searchRolesShouldReturnEmptyListIfUnauthorized(
      @Authenticated(RESTRICTED) final CamundaClient camundaClient) {
    final var roleSearchResponse = camundaClient.newRolesSearchRequest().send().join();
    assertThat(roleSearchResponse.items()).hasSize(0).map(Role::getName).isEmpty();
  }

  @Test
  void shouldAssignRoleToMappingRuleIfAuthorized(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    final String roleId = Strings.newRandomValidIdentityId();
    final String mappingRuleId = Strings.newRandomValidIdentityId();

    createRole(adminClient, roleId, "roleName");
    adminClient
        .newCreateMappingRuleCommand()
        .mappingRuleId(mappingRuleId)
        .name("mappingRuleName")
        .claimName("testClaimName")
        .claimValue("testClaimValue")
        .send()
        .join();

    adminClient
        .newAssignRoleToMappingRuleCommand()
        .roleId(roleId)
        .mappingRuleId(mappingRuleId)
        .send()
        .join();

    Awaitility.await("Mapping rule is assigned to the role")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () ->
                assertThat(
                        adminClient
                            .newMappingRulesByRoleSearchRequest(roleId)
                            .send()
                            .join()
                            .items())
                    .hasSize(1)
                    .anyMatch(m -> mappingRuleId.equals(m.getMappingRuleId())));

    adminClient.newDeleteRoleCommand(roleId).send().join();
  }

  @Test
  void assignRoleToMappingRuleShouldReturnForbiddenIfUnauthorized(
      @Authenticated(RESTRICTED_WITH_READ) final CamundaClient camundaClient) {
    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignRoleToMappingRuleCommand()
                    .roleId(Strings.newRandomValidIdentityId())
                    .mappingRuleId(Strings.newRandomValidIdentityId())
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("403: 'Forbidden'");
  }

  @Test
  void searchShouldReturnAuthorizedRoles(
      @Authenticated(RESTRICTED_WITH_READ) final CamundaClient camundaClient) {
    final var roleSearchResponse = camundaClient.newRolesSearchRequest().send().join();

    assertThat(roleSearchResponse.items())
        .map(Role::getName)
        .containsExactlyInAnyOrder(ROLE_NAME_1, ROLE_NAME_2);
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
  void shouldUnassignRoleFromClientIfAuthorized(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // given
    final String clientId = Strings.newRandomValidIdentityId();
    final String roleId = Strings.newRandomValidIdentityId();

    adminClient.newCreateRoleCommand().roleId(roleId).name("roleName").send().join();
    adminClient.newAssignRoleToClientCommand().roleId(roleId).clientId(clientId).send().join();

    // when
    adminClient.newUnassignRoleFromClientCommand().roleId(roleId).clientId(clientId).send().join();

    // then
    Awaitility.await("Role is unassigned from the client")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final SearchResponse<Client> response =
                  adminClient.newClientsByRoleSearchRequest(roleId).send().join();
              assertThat(response.items()).isEmpty();
            });

    // clean up
    adminClient.newDeleteRoleCommand(roleId).send().join();
  }

  @Test
  void shouldUnassignRoleFromTenantIfAuthorized(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    final String tenantId = Strings.newRandomValidIdentityId();

    adminClient
        .newCreateTenantCommand()
        .tenantId(tenantId)
        .name("tenantName")
        .description("description")
        .send()
        .join();
    adminClient.newAssignRoleToTenantCommand().roleId(ROLE_ID_1).tenantId(tenantId).send().join();

    adminClient
        .newUnassignRoleFromTenantCommand()
        .roleId(ROLE_ID_1)
        .tenantId(tenantId)
        .send()
        .join();

    Awaitility.await("Role is unassigned from the tenant")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () ->
                assertThat(
                        adminClient.newRolesByTenantSearchRequest(tenantId).send().join().items())
                    .isEmpty());
  }

  @Test
  void unassignRoleFromTenantShouldReturnForbiddenIfUnauthorized(
      @Authenticated(RESTRICTED_WITH_READ) final CamundaClient camundaClient) {
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUnassignRoleFromTenantCommand()
                    .roleId(Strings.newRandomValidIdentityId())
                    .tenantId(Strings.newRandomValidIdentityId())
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("403: 'Forbidden'");
  }

  @Test
  void unassignRoleFromClientShouldReturnForbiddenIfUnauthorized(
      @Authenticated(RESTRICTED_WITH_READ) final CamundaClient camundaClient) {
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUnassignRoleFromClientCommand()
                    .roleId(Strings.newRandomValidIdentityId())
                    .clientId(Strings.newRandomValidIdentityId())
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
                assertThat(adminClient.newRolesByGroupSearchRequest(groupId).send().join().items())
                    .anyMatch(r -> roleId.equals(r.getRoleId())));

    adminClient.newDeleteRoleCommand(roleId).send().join();
  }

  @Test
  void shouldAssignRoleToTenantIfAuthorized(@Authenticated(ADMIN) final CamundaClient adminClient) {
    final String tenantId = Strings.newRandomValidIdentityId();

    adminClient
        .newCreateTenantCommand()
        .tenantId(tenantId)
        .name("tenantName")
        .description("description")
        .send()
        .join();
    adminClient.newAssignRoleToTenantCommand().roleId(ROLE_ID_1).tenantId(tenantId).send().join();

    Awaitility.await("Role is assigned to the tenant")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () ->
                assertThat(
                        adminClient.newRolesByTenantSearchRequest(tenantId).send().join().items())
                    .anyMatch(r -> ROLE_ID_1.equals(r.getRoleId())));
  }

  @Test
  void assignRoleToTenantShouldReturnForbiddenIfUnauthorized(
      @Authenticated(RESTRICTED_WITH_READ) final CamundaClient camundaClient) {
    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignRoleToTenantCommand()
                    .roleId(Strings.newRandomValidIdentityId())
                    .tenantId(Strings.newRandomValidIdentityId())
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("403: 'Forbidden'");
  }

  @Test
  void shouldUnassignRoleFromMappingRuleIfAuthorized(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    final String mappingRuleId = Strings.newRandomValidIdentityId();

    adminClient
        .newCreateMappingRuleCommand()
        .mappingRuleId(mappingRuleId)
        .name("mappingRuleName")
        .claimName("claimName")
        .claimValue("claimValue")
        .send()
        .join();

    adminClient
        .newAssignRoleToMappingRuleCommand()
        .roleId(ROLE_ID_1)
        .mappingRuleId(mappingRuleId)
        .send()
        .join();

    adminClient
        .newUnassignRoleFromMappingRuleCommand()
        .roleId(ROLE_ID_1)
        .mappingRuleId(mappingRuleId)
        .send()
        .join();

    Awaitility.await("Mapping rule is unassigned from the role")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () ->
                assertThat(
                        adminClient
                            .newRolesByGroupSearchRequest(mappingRuleId)
                            .send()
                            .join()
                            .items())
                    .isEmpty());
  }

  @Test
  void unassignRoleFromMappingRuleShouldReturnForbiddenIfUnauthorized(
      @Authenticated(RESTRICTED_WITH_READ) final CamundaClient camundaClient) {
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUnassignRoleFromMappingRuleCommand()
                    .roleId(Strings.newRandomValidIdentityId())
                    .mappingRuleId(Strings.newRandomValidIdentityId())
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("403: 'Forbidden'");
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
                assertThat(adminClient.newRolesByGroupSearchRequest(groupId).send().join().items())
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

  @Test
  void shouldAssignRoleToClientIfAuthorized(@Authenticated(ADMIN) final CamundaClient adminClient) {
    // given
    final String clientId = Strings.newRandomValidIdentityId();
    final String roleId = Strings.newRandomValidIdentityId();
    adminClient.newCreateRoleCommand().roleId(roleId).name("roleName").send().join();

    // when
    adminClient.newAssignRoleToClientCommand().roleId(roleId).clientId(clientId).send().join();

    // then
    Awaitility.await("Role is assigned to the client")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final SearchResponse<Client> response =
                  adminClient.newClientsByRoleSearchRequest(roleId).send().join();
              assertThat(response.items().stream().map(Client::getClientId).toList())
                  .contains(clientId);
            });

    // clean up
    adminClient.newDeleteRoleCommand(roleId).send().join();
  }

  @Test
  void assignRoleToClientShouldReturnForbiddenIfUnauthorized(
      @Authenticated(RESTRICTED_WITH_READ) final CamundaClient camundaClient) {
    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignRoleToClientCommand()
                    .roleId(Strings.newRandomValidIdentityId())
                    .clientId(Strings.newRandomValidIdentityId())
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("403: 'Forbidden'");
  }

  @Test
  void shouldSearchClientsByRoleIfAuthorized(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // given
    final String clientId = Strings.newRandomValidIdentityId();
    final String roleId = Strings.newRandomValidIdentityId();
    createRole(adminClient, roleId, "roleName");

    adminClient.newAssignRoleToClientCommand().roleId(roleId).clientId(clientId).send().join();

    // when/then
    Awaitility.await("Search returns correct client ID")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final SearchResponse<Client> response =
                  adminClient.newClientsByRoleSearchRequest(roleId).send().join();
              assertThat(response.items().stream().map(Client::getClientId).toList())
                  .contains(clientId);
            });

    // clean up
    adminClient.newDeleteRoleCommand(roleId).send().join();
  }

  @Test
  void searchClientsByRoleShouldReturnEmptyListIfUnauthorized(
      @Authenticated(RESTRICTED) final CamundaClient camundaClient) {
    final SearchResponse<Client> response =
        camundaClient.newClientsByRoleSearchRequest(ROLE_ID_1).send().join();
    assertThat(response.items()).isEmpty();
  }

  private static void createRole(
      final CamundaClient adminClient, final String roleId, final String roleName) {
    createRole(adminClient, roleId, roleName, null);
  }

  @Test
  void searchGroupsByRoleShouldReturnGroupsIfAuthorized(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // given
    final String groupId = Strings.newRandomValidIdentityId();
    final String roleId = Strings.newRandomValidIdentityId();

    createRole(adminClient, roleId, "roleName");
    adminClient.newCreateGroupCommand().groupId(groupId).name("groupName").send().join();
    adminClient.newAssignRoleToGroupCommand().roleId(roleId).groupId(groupId).send().join();

    // when/then
    Awaitility.await("Group is searchable by role")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var response = adminClient.newGroupsByRoleSearchRequest(roleId).send().join();
              assertThat(response.items()).anyMatch(g -> groupId.equals(g.getGroupId()));
            });

    // clean up
    adminClient.newDeleteRoleCommand(roleId).send().join();
  }

  @Test
  void searchGroupsByRoleShouldReturnEmptyListIfUnauthorized(
      @Authenticated(RESTRICTED) final CamundaClient camundaClient) {
    final SearchResponse<RoleGroup> response =
        camundaClient.newGroupsByRoleSearchRequest(ROLE_ID_1).send().join();
    assertThat(response.items()).isEmpty();
  }

  @Test
  void shouldNotOverloadRoleIdByUsernameWhenApplyAuthorizationCheck(
      @Authenticated(RESTRICTED) final CamundaClient restrictedClient,
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // given
    createRole(adminClient, RESTRICTED, "roleName");
    final var authorization =
        adminClient
            .newCreateAuthorizationCommand()
            .ownerId(RESTRICTED)
            .ownerType(OwnerType.ROLE)
            .resourceId("*")
            .resourceType(ResourceType.ROLE)
            .permissionTypes(PermissionType.READ)
            .send()
            .join();

    // verify
    Awaitility.await("Authorization is searchable")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var response =
                  adminClient
                      .newAuthorizationGetRequest(authorization.getAuthorizationKey())
                      .send()
                      .join();
              assertThat(response).isNotNull();
            });

    // when
    final var roles = restrictedClient.newRolesSearchRequest().send().join();

    // then
    assertThat(roles.items()).isEmpty();
  }

  @Test
  void shouldNotOverloadRoleIdByGroupIdWhenApplyAuthorizationCheck(
      @Authenticated(RESTRICTED) final CamundaClient restrictedClient,
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // given
    adminClient.newCreateGroupCommand().groupId("foo").name("groupName").send().join();
    adminClient.newAssignUserToGroupCommand().username(RESTRICTED).groupId("foo").send().join();
    createRole(adminClient, "foo", "roleName");
    final var authorization =
        adminClient
            .newCreateAuthorizationCommand()
            .ownerId("foo")
            .ownerType(OwnerType.ROLE)
            .resourceId("*")
            .resourceType(ResourceType.ROLE)
            .permissionTypes(PermissionType.READ)
            .send()
            .join();

    // verify
    Awaitility.await("Authorization is searchable")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var response =
                  adminClient
                      .newAuthorizationGetRequest(authorization.getAuthorizationKey())
                      .send()
                      .join();
              assertThat(response).isNotNull();
            });

    // when
    final var roles = restrictedClient.newRolesSearchRequest().send().join();

    // then
    assertThat(roles.items()).isEmpty();
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
}
