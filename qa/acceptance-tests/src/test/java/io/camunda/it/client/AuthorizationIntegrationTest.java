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

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.CreateAuthorizationResponse;
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.api.search.response.Authorization;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.test.util.Strings;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(
    named = "test.integration.camunda.data.secondary-storage.type",
    matches = "rdbms")
public class AuthorizationIntegrationTest {

  private static CamundaClient camundaClient;

  @Test
  void shouldCreateAndGetAuthorizationByAuthorizationKey() {
    // given
    final var ownerId = Strings.newRandomValidIdentityId();
    final var resourceId = Strings.newRandomValidIdentityId();
    final OwnerType ownerType = OwnerType.USER;
    final ResourceType resourceType = ResourceType.RESOURCE;
    final PermissionType permissionType = PermissionType.CREATE;

    // when
    final CreateAuthorizationResponse authorization =
        camundaClient
            .newCreateAuthorizationCommand()
            .ownerId(ownerId)
            .ownerType(ownerType)
            .resourceId(resourceId)
            .resourceType(resourceType)
            .permissionTypes(permissionType)
            .send()
            .join();
    final long authorizationKey = authorization.getAuthorizationKey();
    assertThat(authorizationKey).isGreaterThan(0);

    // then
    Awaitility.await()
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final Authorization retrievedAuthorization =
                  camundaClient.newAuthorizationGetRequest(authorizationKey).send().join();
              assertThat(retrievedAuthorization.getAuthorizationKey())
                  .isEqualTo(String.valueOf(authorizationKey));
              assertThat(retrievedAuthorization.getResourceId()).isEqualTo(resourceId);
              assertThat(retrievedAuthorization.getResourceType()).isEqualTo(resourceType);
              assertThat(retrievedAuthorization.getOwnerId()).isEqualTo(ownerId);
              assertThat(retrievedAuthorization.getOwnerType()).isEqualTo(ownerType);
              assertThat(retrievedAuthorization.getPermissionTypes())
                  .isEqualTo(List.of(permissionType));
            });
  }

  @Test
  void shouldReturnNotFoundWhenGettingNonExistentAuthorization() {
    // when / then
    final var nonExistingAuthorizationKey = 100L;
    assertThatThrownBy(
            () ->
                camundaClient.newAuthorizationGetRequest(nonExistingAuthorizationKey).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining(
            "Authorization with key '%s' not found".formatted(nonExistingAuthorizationKey));
  }

  @Test
  void searchShouldReturnAuthorizationsFilteredByOwnerId() {
    // when
    final var ownerId = Strings.newRandomValidIdentityId();

    final CreateAuthorizationResponse authorization =
        camundaClient
            .newCreateAuthorizationCommand()
            .ownerId(ownerId)
            .ownerType(OwnerType.USER)
            .resourceId(Strings.newRandomValidIdentityId())
            .resourceType(ResourceType.RESOURCE)
            .permissionTypes(PermissionType.CREATE)
            .send()
            .join();

    // create one more authorization
    camundaClient
        .newCreateAuthorizationCommand()
        .ownerId("anotherOwnerId")
        .ownerType(OwnerType.USER)
        .resourceId(Strings.newRandomValidIdentityId())
        .resourceType(ResourceType.RESOURCE)
        .permissionTypes(PermissionType.CREATE)
        .send()
        .join();

    Awaitility.await()
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var authorizationsSearchResponse =
                  camundaClient
                      .newAuthorizationSearchRequest()
                      .filter(fn -> fn.ownerId(ownerId))
                      .send()
                      .join();
              assertThat(authorizationsSearchResponse.items())
                  .hasSize(1)
                  .map(Authorization::getOwnerId)
                  .containsExactly(ownerId);
            });
  }

  @Test
  void searchShouldReturnAuthorizationsFilteredByResourceId() {
    // when
    final var resourceId = "resourceId";

    final CreateAuthorizationResponse authorization =
        camundaClient
            .newCreateAuthorizationCommand()
            .ownerId(Strings.newRandomValidIdentityId())
            .ownerType(OwnerType.USER)
            .resourceId(resourceId)
            .resourceType(ResourceType.RESOURCE)
            .permissionTypes(PermissionType.CREATE)
            .send()
            .join();

    // create one more authorization
    camundaClient
        .newCreateAuthorizationCommand()
        .ownerId(Strings.newRandomValidIdentityId())
        .ownerType(OwnerType.USER)
        .resourceId("someOtherId")
        .resourceType(ResourceType.RESOURCE)
        .permissionTypes(PermissionType.CREATE)
        .send()
        .join();

    Awaitility.await()
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var authorizationsSearchResponse =
                  camundaClient
                      .newAuthorizationSearchRequest()
                      .filter(fn -> fn.resourceIds(resourceId))
                      .send()
                      .join();
              assertThat(authorizationsSearchResponse.items())
                  .hasSize(1)
                  .map(Authorization::getResourceId)
                  .containsExactly(resourceId);
            });
  }

  @Test
  void searchShouldReturnAuthorizationsSortedByOwnerId() {
    // when
    camundaClient
        .newCreateAuthorizationCommand()
        .ownerId("aOwnerId")
        .ownerType(OwnerType.USER)
        .resourceId(Strings.newRandomValidIdentityId())
        .resourceType(ResourceType.RESOURCE)
        .permissionTypes(PermissionType.CREATE)
        .send()
        .join();

    // when

    camundaClient
        .newCreateAuthorizationCommand()
        .ownerId("bOwnerId")
        .ownerType(OwnerType.USER)
        .resourceId(Strings.newRandomValidIdentityId())
        .resourceType(ResourceType.RESOURCE)
        .permissionTypes(PermissionType.CREATE)
        .send()
        .join();

    Awaitility.await()
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var authorizationsSearchResponse =
                  camundaClient
                      .newAuthorizationSearchRequest()
                      .sort(s -> s.ownerId().desc())
                      .send()
                      .join();
              assertThat(authorizationsSearchResponse.items())
                  .map(Authorization::getOwnerId)
                  .contains("bOwnerId", "aOwnerId");
            });
  }

  @Test
  void searchShouldReturnEmptyListWhenSearchingForNonExistingAuthorizations() {
    final var searchResponse =
        camundaClient
            .newAuthorizationSearchRequest()
            .filter(fn -> fn.ownerId("nonExistingId"))
            .send()
            .join();
    assertThat(searchResponse.items()).isEmpty();
  }
}
