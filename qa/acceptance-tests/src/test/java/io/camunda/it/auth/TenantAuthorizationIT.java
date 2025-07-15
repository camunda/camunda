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
import static io.camunda.client.api.search.enums.ResourceType.TENANT;
import static io.camunda.client.api.search.enums.ResourceType.USER;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.Tenant;
import io.camunda.client.api.search.response.TenantUser;
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
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
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

  @UserDefinition
  private static final TestUser ADMIN_USER =
      new TestUser(
          ADMIN,
          PASSWORD,
          List.of(
              new Permissions(TENANT, CREATE, List.of("*")),
              new Permissions(TENANT, READ, List.of("*")),
              new Permissions(TENANT, UPDATE, List.of("*")),
              new Permissions(USER, CREATE, List.of("*"))));

  @UserDefinition
  private static final TestUser RESTRICTED_USER =
      new TestUser(
          RESTRICTED,
          PASSWORD,
          List.of(new Permissions(TENANT, READ, List.of("tenant1", "tenant2"))));

  @UserDefinition
  private static final TestUser UNAUTHORIZED_USER = new TestUser(UNAUTHORIZED, PASSWORD, List.of());

  @BeforeAll
  static void setUp(@Authenticated(ADMIN) final CamundaClient adminClient) {
    createTenant(adminClient, "tenant1");
    createTenant(adminClient, "tenant2");

    Awaitility.await("should create tenants and import in ES")
        .atMost(AWAIT_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var tenantsSearchResponse = adminClient.newTenantsSearchRequest().send().join();
              Assertions.assertThat(
                      tenantsSearchResponse.items().stream().map(Tenant::getTenantId).toList())
                  .containsAll(Arrays.asList("tenant1", "tenant2"));
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
        .containsExactlyInAnyOrder("tenant1", "tenant2");
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
    final Tenant tenant = userClient.newTenantGetRequest("tenant1").send().join();

    // then
    assertThat(tenant.getTenantId()).isEqualTo("tenant1");
  }

  @Test
  void getByIdShouldReturnForbiddenForUnauthorizedTenantId(
      @Authenticated(UNAUTHORIZED) final CamundaClient userClient) {
    // when/then
    assertThatThrownBy(() -> userClient.newTenantGetRequest("tenant1").send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Unauthorized to perform operation 'READ' on resource 'TENANT'");
  }

  @Test
  void searchUsersByTenantShouldReturnEmptyListIfUnauthorized(
      @Authenticated(UNAUTHORIZED) final CamundaClient camundaClient) {
    final SearchResponse<TenantUser> response =
        camundaClient.newUsersByTenantSearchRequest("tenant1").send().join();
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

    adminClient.newAssignUserToTenantCommand().username(userName).tenantId("tenant1").send().join();

    // when/then
    Awaitility.await("Search returns correct users")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final SearchResponse<TenantUser> response =
                  adminClient.newUsersByTenantSearchRequest("tenant1").send().join();
              Assertions.assertThat(response.items().stream().map(TenantUser::getUsername).toList())
                  .contains(userName);
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
