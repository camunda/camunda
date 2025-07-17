/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.api.search.enums.PermissionType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.api.search.response.Client;
import io.camunda.client.api.search.response.Group;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.GroupDefinition;
import io.camunda.qa.util.auth.Membership;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.RoleDefinition;
import io.camunda.qa.util.auth.TestGroup;
import io.camunda.qa.util.auth.TestRole;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.Strings;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.function.Executable;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class GroupAuthorizationIT {

  public static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  private static final String ADMIN = "admin";
  private static final String RESTRICTED = "restrictedUser";
  private static final String RESTRICTED_WITH_READ = "restrictedUser2";
  private static final String DEFAULT_PASSWORD = "password";

  @UserDefinition
  private static final TestUser ADMIN_USER =
      new TestUser(
          ADMIN,
          DEFAULT_PASSWORD,
          List.of(
              new Permissions(ResourceType.ROLE, PermissionType.READ, List.of("*")),
              new Permissions(ResourceType.GROUP, PermissionType.READ, List.of("*")),
              new Permissions(ResourceType.GROUP, PermissionType.UPDATE, List.of("*"))));

  @UserDefinition
  private static final TestUser RESTRICTED_USER =
      new TestUser(RESTRICTED, DEFAULT_PASSWORD, List.of());

  @UserDefinition
  private static final TestUser RESTRICTED_USER_WITH_READ_PERMISSION =
      new TestUser(
          RESTRICTED_WITH_READ,
          DEFAULT_PASSWORD,
          List.of(
              new Permissions(ResourceType.GROUP, READ, List.of("*")),
              new Permissions(ResourceType.ROLE, READ, List.of("*"))));

  @GroupDefinition
  private static final TestGroup GROUP_1 =
      new TestGroup(Strings.newRandomValidIdentityId(), "AGroupName");

  @GroupDefinition
  private static final TestGroup GROUP_2 =
      new TestGroup(Strings.newRandomValidIdentityId(), "BGroupName");

  @RoleDefinition
  private static final TestRole ROLE =
      TestRole.withoutPermissions(
          Strings.newRandomValidIdentityId(),
          "roleName",
          List.of(new Membership(GROUP_1.id(), EntityType.GROUP)));

  @Test
  void searchShouldReturnAuthorizedGroups(
      @Authenticated(RESTRICTED_WITH_READ) final CamundaClient camundaClient) {
    final var groupSearchResponse = camundaClient.newGroupsSearchRequest().send().join();

    assertThat(groupSearchResponse.items())
        .hasSize(2)
        .map(Group::getGroupId)
        .containsExactlyInAnyOrder(GROUP_1.id(), GROUP_2.id());
  }

  @Test
  void searchShouldReturnGroupsSortedById(
      @Authenticated(RESTRICTED_WITH_READ) final CamundaClient camundaClient) {
    final var groupSearchResponse =
        camundaClient.newGroupsSearchRequest().sort(s -> s.name().desc()).send().join();

    assertThat(groupSearchResponse.items())
        .hasSize(2)
        .map(Group::getName)
        .containsExactly(GROUP_2.name(), GROUP_1.name());
  }

  @Test
  void searchShouldReturnGroupFilteredByGroupId(
      @Authenticated(RESTRICTED_WITH_READ) final CamundaClient camundaClient) {
    final var groupSearchResponse =
        camundaClient.newGroupsSearchRequest().filter(fn -> fn.groupId(GROUP_1.id())).send().join();

    assertThat(groupSearchResponse.items())
        .hasSize(1)
        .map(Group::getGroupId)
        .containsExactly(GROUP_1.id());
  }

  @Test
  void searchShouldReturnGroupsFilteredByName(
      @Authenticated(RESTRICTED_WITH_READ) final CamundaClient camundaClient) {
    final var groupSearchResponse =
        camundaClient.newGroupsSearchRequest().filter(fn -> fn.name(GROUP_2.name())).send().join();

    assertThat(groupSearchResponse.items())
        .hasSize(1)
        .map(Group::getName)
        .containsExactly(GROUP_2.name());
  }

  @Test
  void searchShouldReturnEmptyListWhenUnauthorized(
      @Authenticated(RESTRICTED) final CamundaClient camundaClient) {
    final var groupSearchResponse = camundaClient.newGroupsSearchRequest().send().join();

    assertThat(groupSearchResponse.items()).hasSize(0).map(Group::getName).isEmpty();
  }

  @Test
  void getGroupByIdShouldReturnGroupIfAuthorized(
      @Authenticated(RESTRICTED_WITH_READ) final CamundaClient camundaClient) {
    final var group = camundaClient.newGroupGetRequest(GROUP_1.id()).send().join();

    assertThat(group.getGroupId()).isEqualTo(GROUP_1.id());
  }

  @Test
  void getGroupByIdShouldReturnForbiddenIfUnauthorized(
      @Authenticated(RESTRICTED) final CamundaClient camundaClient) {
    // when
    final Executable executeGet =
        () -> camundaClient.newGroupGetRequest(GROUP_1.id()).send().join();
    // then
    final var problemException = assertThrows(ProblemException.class, executeGet);
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .isEqualTo("Unauthorized to perform operation 'READ' on resource 'GROUP'");
  }

  @Test
  void getRolesByGroupShouldReturnRolesIfAuthorized(
      @Authenticated(RESTRICTED_WITH_READ) final CamundaClient camundaClient) {
    final var roles = camundaClient.newRolesByGroupSearchRequest(GROUP_1.id()).send().join();

    assertThat(roles.items().size()).isEqualTo(1);
    assertThat(roles.items().getFirst().getRoleId()).isEqualTo(ROLE.id());
  }

  @Test
  void getRolesByGroupShouldReturnNotFoundIfUnauthorized(
      @Authenticated(RESTRICTED) final CamundaClient camundaClient) {
    final var roles = camundaClient.newRolesByGroupSearchRequest(GROUP_1.id()).send().join();
    assertThat(roles.items().size()).isEqualTo(0);
  }

  @Test
  void assignClientToGroupShouldAssignClientIfAuthorized(
      @Authenticated(ADMIN) final CamundaClient camundaClient) {
    // when
    final String clientId = "clientId";
    camundaClient
        .newAssignClientToGroupCommand()
        .clientId(clientId)
        .groupId(GROUP_1.id())
        .send()
        .join();

    // then
    Awaitility.await("Client is assigned to the group")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var clients =
                  camundaClient.newClientsByGroupSearchRequest(GROUP_1.id()).send().join();
              assertThat(clients.items()).anyMatch(r -> clientId.equals(r.getClientId()));
            });
  }

  @Test
  void assignClientToGroupShouldReturnNotFoundIfUnauthorized(
      @Authenticated(RESTRICTED) final CamundaClient camundaClient) {
    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignClientToGroupCommand()
                    .clientId("clientId")
                    .groupId(GROUP_1.id())
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("403: 'Forbidden'");
  }

  @Test
  void unassignClientFromGroupShouldReturnForbiddenIfUnauthorized(
      @Authenticated(RESTRICTED) final CamundaClient camundaClient) {
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUnassignClientFromGroupCommand()
                    .clientId("clientId")
                    .groupId(GROUP_1.id())
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("403: 'Forbidden'");
  }

  @Test
  void unassignClientFromGroupShouldUnassignClientIfAuthorized(
      @Authenticated(ADMIN) final CamundaClient camundaClient) {
    // given
    final String clientId = "clientId_toRemove";
    camundaClient
        .newAssignClientToGroupCommand()
        .clientId(clientId)
        .groupId(GROUP_1.id())
        .send()
        .join();

    Awaitility.await("Client is assigned to the group")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var clients =
                  camundaClient.newClientsByGroupSearchRequest(GROUP_1.id()).send().join();
              assertThat(clients.items()).anyMatch(r -> clientId.equals(r.getClientId()));
            });

    // when
    camundaClient
        .newUnassignClientFromGroupCommand()
        .clientId(clientId)
        .groupId(GROUP_1.id())
        .send()
        .join();

    // then
    Awaitility.await("Client is unassigned from the group")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var clients =
                  camundaClient.newClientsByGroupSearchRequest(GROUP_1.id()).send().join();
              assertThat(clients.items()).noneMatch(r -> clientId.equals(r.getClientId()));
            });
  }

  @Test
  void getClientsByGroupShouldReturnEmptyListIfUnauthorized(
      @Authenticated(RESTRICTED) final CamundaClient camundaClient) {
    final var clients = camundaClient.newClientsByGroupSearchRequest(GROUP_1.id()).send().join();
    assertThat(clients.items().size()).isEqualTo(0);
  }

  @Test
  void getClientsByGroupShouldReturnClientsIfAuthorized(
      @Authenticated(ADMIN) final CamundaClient camundaClient) {
    // when
    final String firstClientId = "someClientId";
    camundaClient
        .newAssignClientToGroupCommand()
        .clientId(firstClientId)
        .groupId(GROUP_1.id())
        .send()
        .join();

    final String secondClientId = "otherClientId";
    camundaClient
        .newAssignClientToGroupCommand()
        .clientId(secondClientId)
        .groupId(GROUP_1.id())
        .send()
        .join();

    // then
    Awaitility.await("Clients are assigned to the group and can be searched")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var clients =
                  camundaClient.newClientsByGroupSearchRequest(GROUP_1.id()).send().join();
              assertThat(clients.items())
                  .map(Client::getClientId)
                  .contains(firstClientId, secondClientId);
            });
  }
}
