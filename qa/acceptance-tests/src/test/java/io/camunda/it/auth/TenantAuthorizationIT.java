/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.api.search.enums.PermissionType.CREATE;
import static io.camunda.client.api.search.enums.PermissionType.READ;
import static io.camunda.client.api.search.enums.PermissionType.UPDATE;
import static io.camunda.client.api.search.enums.ResourceType.GROUP;
import static io.camunda.client.api.search.enums.ResourceType.TENANT;
import static io.camunda.client.api.search.enums.ResourceType.USER;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.response.Client;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.Tenant;
import io.camunda.client.api.search.response.TenantGroup;
import io.camunda.client.api.search.response.TenantUser;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.Strings;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class TenantAuthorizationIT {

  public static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  public static final String PASSWORD = "password";

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  private static final String ADMIN = "admin";
  private static final String RESTRICTED = "restrictedUser";
  private static final String UNAUTHORIZED = "unauthorizedUser";
  private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(15);
  private static final String TENANT_ID_1 = "tenant1";
  private static final String TENANT_ID_2 = "tenant2";

  @UserDefinition
  private static final TestUser ADMIN_USER =
      new TestUser(
          ADMIN,
          PASSWORD,
          List.of(
              new Permissions(TENANT, CREATE, List.of("*")),
              new Permissions(TENANT, READ, List.of("*")),
              new Permissions(TENANT, UPDATE, List.of("*")),
              new Permissions(USER, CREATE, List.of("*")),
              new Permissions(GROUP, CREATE, List.of("*"))));

  @UserDefinition
  private static final TestUser RESTRICTED_USER =
      new TestUser(
          RESTRICTED,
          PASSWORD,
          List.of(new Permissions(TENANT, READ, List.of(TENANT_ID_1, TENANT_ID_2))));

  @UserDefinition
  private static final TestUser UNAUTHORIZED_USER = new TestUser(UNAUTHORIZED, PASSWORD, List.of());

  @AutoClose private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  @BeforeAll
  static void setUp(@Authenticated(ADMIN) final CamundaClient adminClient) {
    createTenant(adminClient, TENANT_ID_1);
    createTenant(adminClient, TENANT_ID_2);

    Awaitility.await("should create tenants and import in ES")
        .atMost(AWAIT_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var tenantsSearchResponse = adminClient.newTenantsSearchRequest().send().join();
              Assertions.assertThat(tenantsSearchResponse.items().stream().map(Tenant::getTenantId))
                  .containsAll(Arrays.asList(TENANT_ID_1, TENANT_ID_2));
            });
  }

  @Test
  void searchShouldReturnAuthorizedTenants(
      @Authenticated(RESTRICTED) final CamundaClient userClient) {
    // when
    final SearchResponse<Tenant> tenantSearchResponse =
        userClient.newTenantsSearchRequest().send().join();

    // then
    assertThat(tenantSearchResponse.items())
        .hasSize(2)
        .map(Tenant::getTenantId)
        .containsExactlyInAnyOrder(TENANT_ID_1, TENANT_ID_2);
  }

  @Test
  void searchShouldReturnEmptyListWhenUnauthorized(
      @Authenticated(UNAUTHORIZED) final CamundaClient userClient) {
    // when
    final SearchResponse<Tenant> tenantSearchResponse =
        userClient.newTenantsSearchRequest().send().join();

    // then
    assertThat(tenantSearchResponse.items()).isEmpty();
  }

  @Test
  void getByIdShouldReturnAuthorizedTenant(
      @Authenticated(RESTRICTED) final CamundaClient userClient) {
    // when
    final Tenant tenant = userClient.newTenantGetRequest(TENANT_ID_1).send().join();

    // then
    assertThat(tenant.getTenantId()).isEqualTo(TENANT_ID_1);
  }

  @Test
  void getByIdShouldReturnForbiddenForUnauthorizedTenantId(
      @Authenticated(UNAUTHORIZED) final CamundaClient userClient) {
    // when/then
    assertThatThrownBy(() -> userClient.newTenantGetRequest(TENANT_ID_1).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Unauthorized to perform operation 'READ' on resource 'TENANT'");
  }

  @Test
  void searchUsersByTenantShouldReturnEmptyListIfUnauthorized(
      @Authenticated(UNAUTHORIZED) final CamundaClient camundaClient) {
    final SearchResponse<TenantUser> response =
        camundaClient.newUsersByTenantSearchRequest(TENANT_ID_1).send().join();
    Assertions.assertThat(response.items()).isEmpty();
  }

  @Test
  void shouldSearchUsersByTenantIfAuthorized(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // given
    final String userName = Strings.newRandomValidIdentityId();

    adminClient
        .newCreateUserCommand()
        .username(userName)
        .name("user name")
        .password("password")
        .email("some@email.com")
        .send()
        .join();

    adminClient
        .newAssignUserToTenantCommand()
        .username(userName)
        .tenantId(TENANT_ID_1)
        .send()
        .join();

    // when/then
    Awaitility.await("Search returns correct users")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final SearchResponse<TenantUser> response =
                  adminClient.newUsersByTenantSearchRequest(TENANT_ID_1).send().join();
              Assertions.assertThat(response.items().stream().map(TenantUser::getUsername))
                  .contains(userName);
            });
  }

  @Test
  void shouldSearchGroupsByTenantIfAuthorized(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // given
    final String groupId = Strings.newRandomValidIdentityId();
    adminClient.newCreateGroupCommand().groupId(groupId).name("group name").send().join();
    adminClient.newAssignGroupToTenantCommand().groupId(groupId).tenantId("tenant1").send().join();

    // when/then
    Awaitility.await("Search returns groups by tenant")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final SearchResponse<TenantGroup> response =
                  adminClient.newGroupsByTenantSearchRequest("tenant1").send().join();
              Assertions.assertThat(response.items().stream().map(TenantGroup::getGroupId))
                  .contains(groupId);
            });
  }

  @Test
  void searchGroupsByTenantShouldReturnEmptyListIfUnauthorized(
      @Authenticated(UNAUTHORIZED) final CamundaClient camundaClient) {
    final SearchResponse<TenantGroup> response =
        camundaClient.newGroupsByTenantSearchRequest("tenant1").send().join();
    Assertions.assertThat(response.items()).isEmpty();
  }

  @Test
  void shouldSearchClientsByTenantIfAuthorized(
      @Authenticated(ADMIN) final CamundaClient adminClient)
      throws URISyntaxException, IOException, InterruptedException {
    // given
    final String clientId = Strings.newRandomValidIdentityId();
    adminClient
        .newAssignClientToTenantCommand()
        .clientId(clientId)
        .tenantId(TENANT_ID_1)
        .send()
        .join();

    // when/then
    Awaitility.await("Search returns clients by tenant")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final SearchResponse<Client> response =
                  adminClient.newClientsByTenantSearchRequest(TENANT_ID_1).send().join();
              Assertions.assertThat(response.items().stream().map(Client::getClientId))
                  .contains(clientId);
            });
  }

  @Test
  void searchClientsByTenantShouldReturnEmptyListIfUnauthorized(
      @Authenticated(UNAUTHORIZED) final CamundaClient camundaClient) {
    final SearchResponse<Client> response =
        camundaClient.newClientsByTenantSearchRequest(TENANT_ID_1).send().join();
    Assertions.assertThat(response.items()).isEmpty();
  }

  @Test
  void assignClientToTenantShouldReturnForbiddenIfUnauthorized(
      @Authenticated(RESTRICTED) final CamundaClient camundaClient) {
    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignClientToTenantCommand()
                    .clientId("clientId")
                    .tenantId(TENANT_ID_1)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("403: 'Forbidden'");
  }

  @Test
  void assignClientToTenantShouldAssignClientIfAuthorized(
      @Authenticated(ADMIN) final CamundaClient camundaClient) {
    // when
    final String clientId = "clientId";
    camundaClient
        .newAssignClientToTenantCommand()
        .clientId(clientId)
        .tenantId(TENANT_ID_1)
        .send()
        .join();

    // then
    Awaitility.await("Client is assigned to the tenant")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var clients =
                  camundaClient.newClientsByTenantSearchRequest(TENANT_ID_1).send().join();
              Assertions.assertThat(clients.items())
                  .anyMatch(r -> clientId.equals(r.getClientId()));
            });
  }

  private static void createTenant(final CamundaClient adminClient, final String tenantId) {
    adminClient
        .newCreateTenantCommand()
        .tenantId(tenantId)
        .name(UUID.randomUUID().toString())
        .send()
        .join();
  }
}
