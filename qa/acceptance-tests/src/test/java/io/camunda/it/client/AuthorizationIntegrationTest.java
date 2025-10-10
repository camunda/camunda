/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker.DEFAULT_MAPPING_RULE_CLAIM_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.CreateAuthorizationResponse;
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.api.search.response.Authorization;
import io.camunda.qa.util.auth.GroupDefinition;
import io.camunda.qa.util.auth.MappingRuleDefinition;
import io.camunda.qa.util.auth.RoleDefinition;
import io.camunda.qa.util.auth.TestGroup;
import io.camunda.qa.util.auth.TestMappingRule;
import io.camunda.qa.util.auth.TestRole;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.test.util.Strings;
import java.util.List;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@MultiDbTest
public class AuthorizationIntegrationTest {

  private static final String ROLE_ID_1 = Strings.newRandomValidIdentityId();
  private static final String GROUP_ID_1 = Strings.newRandomValidIdentityId();
  private static final String USER_ID_1 = "0" + Strings.newRandomValidIdentityId();
  private static final String USER_ID_2 = "1" + Strings.newRandomValidIdentityId();
  private static final String USER_ID_3 = "2" + Strings.newRandomValidIdentityId();
  private static final String MAPPING_RULE_ID_1 = Strings.newRandomValidIdentityId();

  @UserDefinition
  private static final TestUser USER_1 = new TestUser(USER_ID_1, "password", List.of());

  @UserDefinition
  private static final TestUser USER_2 = new TestUser(USER_ID_2, "password", List.of());

  @UserDefinition
  private static final TestUser USER_3 = new TestUser(USER_ID_3, "password", List.of());

  @MappingRuleDefinition
  private static final TestMappingRule MAPPING_RULE_1 =
      new TestMappingRule(
          MAPPING_RULE_ID_1,
          DEFAULT_MAPPING_RULE_CLAIM_NAME,
          Strings.newRandomValidIdentityId(),
          List.of());

  @GroupDefinition
  private static final TestGroup GROUP_1 =
      TestGroup.withoutPermissions(GROUP_ID_1, GROUP_ID_1, List.of());

  @RoleDefinition
  private static final TestRole ROLE_1 =
      TestRole.withoutPermissions(ROLE_ID_1, ROLE_ID_1, List.of());

  private static CamundaClient camundaClient;

  @ParameterizedTest
  @MethodSource("getValidAuthorizationRequest")
  void shouldCreateAndGetAuthorizationByAuthorizationKey(
      final OwnerType ownerType,
      final ResourceType resourceType,
      final String ownerId,
      final String resourceId) {
    // given
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

  @ParameterizedTest
  @MethodSource("getValidAuthorizationRequest")
  void shouldUpdateValidAuthorizationByAuthorizationKey(
      final OwnerType ownerType,
      final ResourceType resourceType,
      final String ownerId,
      final String resourceId) {
    // given
    final PermissionType permissionType = PermissionType.CREATE;

    // when
    final CreateAuthorizationResponse authorization =
        camundaClient
            .newCreateAuthorizationCommand()
            .ownerId(ownerId)
            .ownerType(ownerType)
            .resourceId(resourceId)
            .resourceType(ResourceType.AUTHORIZATION)
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
              assertThat(retrievedAuthorization).isNotNull();
              assertThat(retrievedAuthorization.getResourceType())
                  .isEqualTo(ResourceType.AUTHORIZATION);
            });
    camundaClient
        .newUpdateAuthorizationCommand(authorizationKey)
        .ownerId(ownerId)
        .ownerType(ownerType)
        .resourceId(resourceId)
        .resourceType(resourceType)
        .permissionTypes(permissionType)
        .send()
        .join();
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

  // TODO: with https://github.com/camunda/camunda/issues/38527 flag for LOCAL_USER_ENABLED and
  // LOCAL_GROUP_ENABLED should be considered
  @ParameterizedTest
  @MethodSource("getInvalidAuthorizationCreateRequest")
  void shouldRejectAuthorizationsWithNotFoundOwnerAndResource(
      final OwnerType ownerType,
      final ResourceType resourceType,
      final String ownerId,
      final String resourceId,
      final String message) {
    // given
    final PermissionType permissionType = PermissionType.CREATE;

    assertThatThrownBy(
            () ->
                camundaClient
                    .newCreateAuthorizationCommand()
                    .ownerId(ownerId)
                    .ownerType(ownerType)
                    .resourceId(resourceId)
                    .resourceType(resourceType)
                    .permissionTypes(permissionType)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining(message);
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
    final var ownerId = USER_ID_3;

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
        .ownerId(USER_ID_2)
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
                      .filter(fn -> fn.ownerId(ownerId).resourceType(ResourceType.RESOURCE))
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
            .ownerId(USER_ID_1)
            .ownerType(OwnerType.USER)
            .resourceId(resourceId)
            .resourceType(ResourceType.RESOURCE)
            .permissionTypes(PermissionType.CREATE)
            .send()
            .join();

    // create one more authorization
    camundaClient
        .newCreateAuthorizationCommand()
        .ownerId(USER_ID_2)
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
        .ownerId(USER_ID_1)
        .ownerType(OwnerType.USER)
        .resourceId(Strings.newRandomValidIdentityId())
        .resourceType(ResourceType.RESOURCE)
        .permissionTypes(PermissionType.CREATE)
        .send()
        .join();

    // when

    camundaClient
        .newCreateAuthorizationCommand()
        .ownerId(USER_ID_2)
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
                  .contains(USER_ID_1, USER_ID_2);
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

  public static Stream<Arguments> getValidAuthorizationRequest() {
    return Stream.of(
        Arguments.of(OwnerType.USER, ResourceType.RESOURCE, USER_ID_1, "resource1"),
        Arguments.of(OwnerType.USER, ResourceType.RESOURCE, USER_ID_1, "*"),
        Arguments.of(OwnerType.ROLE, ResourceType.RESOURCE, ROLE_ID_1, "resource1"),
        Arguments.of(OwnerType.ROLE, ResourceType.RESOURCE, ROLE_ID_1, "*"),
        Arguments.of(OwnerType.GROUP, ResourceType.RESOURCE, GROUP_ID_1, "resource1"),
        Arguments.of(OwnerType.GROUP, ResourceType.RESOURCE, GROUP_ID_1, "*"),
        Arguments.of(
            OwnerType.CLIENT, ResourceType.RESOURCE, Strings.newRandomValidIdentityId(), "*"),
        Arguments.of(
            OwnerType.CLIENT,
            ResourceType.RESOURCE,
            Strings.newRandomValidIdentityId(),
            "resource1"),
        Arguments.of(OwnerType.MAPPING_RULE, ResourceType.RESOURCE, MAPPING_RULE_ID_1, "resource1"),
        Arguments.of(OwnerType.MAPPING_RULE, ResourceType.RESOURCE, MAPPING_RULE_ID_1, "*"),
        Arguments.of(OwnerType.MAPPING_RULE, ResourceType.USER, MAPPING_RULE_ID_1, USER_ID_1),
        Arguments.of(OwnerType.MAPPING_RULE, ResourceType.USER, MAPPING_RULE_ID_1, "*"),
        Arguments.of(OwnerType.MAPPING_RULE, ResourceType.ROLE, MAPPING_RULE_ID_1, ROLE_ID_1),
        Arguments.of(OwnerType.MAPPING_RULE, ResourceType.ROLE, MAPPING_RULE_ID_1, "*"),
        Arguments.of(OwnerType.MAPPING_RULE, ResourceType.GROUP, MAPPING_RULE_ID_1, GROUP_ID_1),
        Arguments.of(OwnerType.MAPPING_RULE, ResourceType.GROUP, MAPPING_RULE_ID_1, "*"),
        Arguments.of(OwnerType.ROLE, ResourceType.MAPPING_RULE, ROLE_ID_1, MAPPING_RULE_ID_1),
        Arguments.of(OwnerType.ROLE, ResourceType.MAPPING_RULE, ROLE_ID_1, "*"));
  }

  public static Stream<Arguments> getInvalidAuthorizationCreateRequest() {
    return Stream.of(
        Arguments.of(
            OwnerType.MAPPING_RULE,
            ResourceType.RESOURCE,
            Strings.newRandomValidIdentityId(),
            "*",
            "a mapping rule with this ID does not exist"),
        Arguments.of(
            OwnerType.MAPPING_RULE,
            ResourceType.ROLE,
            MAPPING_RULE_ID_1,
            Strings.newRandomValidIdentityId(),
            "a role with this ID does not exist"),
        Arguments.of(
            OwnerType.USER,
            ResourceType.MAPPING_RULE,
            USER_ID_1,
            Strings.newRandomValidIdentityId(),
            "a mapping rule with this ID does not exist"),
        Arguments.of(
            OwnerType.ROLE,
            ResourceType.RESOURCE,
            Strings.newRandomValidIdentityId(),
            "*",
            "a role with this ID does not exist"));
  }
}
