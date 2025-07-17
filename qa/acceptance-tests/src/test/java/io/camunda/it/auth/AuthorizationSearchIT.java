/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.api.search.enums.OwnerType.CLIENT;
import static io.camunda.client.api.search.enums.OwnerType.USER;
import static io.camunda.client.api.search.enums.PermissionType.CREATE;
import static io.camunda.client.api.search.enums.PermissionType.CREATE_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.DELETE;
import static io.camunda.client.api.search.enums.PermissionType.READ;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.AUTHORIZATION;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.CreateAuthorizationResponse;
import io.camunda.client.api.response.DeleteAuthorizationResponse;
import io.camunda.client.api.search.response.Authorization;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class AuthorizationSearchIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  private static final String ADMIN = "admin";
  private static final String RESTRICTED = "restrictedUser";
  private static final String DEFAULT_PASSWORD = "password";

  @UserDefinition
  private static final TestUser ADMIN_USER =
      new TestUser(
          ADMIN,
          DEFAULT_PASSWORD,
          List.of(
              new Permissions(AUTHORIZATION, READ, List.of("*")),
              new Permissions(AUTHORIZATION, CREATE, List.of("*")),
              new Permissions(AUTHORIZATION, DELETE, List.of("*"))));

  @UserDefinition
  private static final TestUser RESTRICTED_USER =
      new TestUser(RESTRICTED, DEFAULT_PASSWORD, List.of());

  @Test
  void getAuthorizationByAuthorizationKeyShouldReturnAuthorizationIfAuthorized(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // given
    final var resourceId = "test-resource-" + UUID.randomUUID();

    final CreateAuthorizationResponse response =
        adminClient
            .newCreateAuthorizationCommand()
            .ownerId(ADMIN)
            .ownerType(USER)
            .resourceId(resourceId)
            .resourceType(PROCESS_DEFINITION)
            .permissionTypes(CREATE_PROCESS_INSTANCE)
            .send()
            .join();

    Awaitility.await()
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var authorization =
                  adminClient
                      .newAuthorizationGetRequest(response.getAuthorizationKey())
                      .send()
                      .join();

              assertThat(authorization.getAuthorizationKey())
                  .isEqualTo(String.valueOf(response.getAuthorizationKey()));
            });
  }

  @Test
  void getAuthorizationByAuthorizationKeyShouldReturnNotFoundIfAuthorized(
      @Authenticated(RESTRICTED) final CamundaClient camundaClient) {
    assertThatThrownBy(() -> camundaClient.newAuthorizationGetRequest(100L).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("404: 'Not Found'");
  }

  @Test
  void getAuthorizationByShouldReturn403IfNotAuthorized(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(RESTRICTED) final CamundaClient camundaClient) {
    // given
    final var authorization = getAuthorization(adminClient).getAuthorizationKey();
    final var authorizationKey = Long.valueOf(authorization);
    assertThatThrownBy(
            () -> camundaClient.newAuthorizationGetRequest(authorizationKey).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("403: 'Forbidden'");
  }

  @Test
  void searchShouldReturnAuthorizationsIfAuthorized(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    final var response =
        adminClient
            .newAuthorizationSearchRequest()
            .filter(f -> f.ownerId(RESTRICTED))
            .send()
            .join();

    assertThat(response.items())
        .isNotEmpty()
        .map(Authorization::getOwnerId)
        .containsExactly(RESTRICTED);
  }

  @Test
  void searchShouldReturnEmptyListForRestrictedUser(
      @Authenticated(RESTRICTED) final CamundaClient client) throws Exception {
    final var response = client.newAuthorizationSearchRequest().send().join();
    assertThat(response.items()).isEmpty();
  }

  @Test
  void shouldBeAbleToQueryAuthorizationAfterAdding(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // given
    final var resourceId = "test-resource-" + UUID.randomUUID();

    adminClient
        .newCreateAuthorizationCommand()
        .ownerId(ADMIN)
        .ownerType(USER)
        .resourceId(resourceId)
        .resourceType(PROCESS_DEFINITION)
        .permissionTypes(CREATE_PROCESS_INSTANCE)
        .send()
        .join()
        .getAuthorizationKey();

    // then
    Awaitility.await()
        .untilAsserted(
            () ->
                assertThat(
                        adminClient
                            .newAuthorizationSearchRequest()
                            .filter(f -> f.ownerId(ADMIN))
                            .filter(f -> f.resourceType(PROCESS_DEFINITION))
                            .filter(f -> f.resourceIds(List.of(resourceId)))
                            .send()
                            .join()
                            .items())
                    .isEmpty());
  }

  @Test
  void shouldNotShowAuthorizationAfterRemoval(@Authenticated(ADMIN) final CamundaClient adminClient)
      throws Exception {
    // Given
    final String resourceId = "test-resource-" + UUID.randomUUID();
    final CreateAuthorizationResponse createResponse =
        adminClient
            .newCreateAuthorizationCommand()
            .ownerId(ADMIN)
            .ownerType(CLIENT)
            .resourceId(resourceId)
            .resourceType(PROCESS_DEFINITION)
            .permissionTypes(READ_PROCESS_DEFINITION, CREATE_PROCESS_INSTANCE)
            .send()
            .join();

    // Verify it was created
    Awaitility.await()
        .untilAsserted(
            () ->
                assertThat(
                        adminClient
                            .newAuthorizationSearchRequest()
                            .filter(f -> f.ownerId(ADMIN))
                            .filter(f -> f.resourceType(PROCESS_DEFINITION))
                            .filter(f -> f.resourceIds(resourceId))
                            .send()
                            .join()
                            .items())
                    .map(Authorization::getAuthorizationKey)
                    .containsExactly(String.valueOf(createResponse.getAuthorizationKey())));

    // When
    final DeleteAuthorizationResponse deleteResponse =
        adminClient
            .newDeleteAuthorizationCommand(createResponse.getAuthorizationKey())
            .send()
            .join();

    // then
    Awaitility.await()
        .untilAsserted(
            () ->
                assertThat(
                        adminClient
                            .newAuthorizationSearchRequest()
                            .filter(f -> f.ownerId(ADMIN))
                            .filter(f -> f.resourceType(PROCESS_DEFINITION))
                            .filter(f -> f.resourceIds(resourceId))
                            .send()
                            .join()
                            .items())
                    .isEmpty());
  }

  private Authorization getAuthorization(final CamundaClient client) {
    return client
        .newAuthorizationSearchRequest()
        .page(p -> p.limit(1))
        .send()
        .join()
        .items()
        .getFirst();
  }
}
