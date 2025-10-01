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
<<<<<<< HEAD
=======
import io.camunda.client.api.search.response.Group;
import io.camunda.client.api.search.response.Role;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
>>>>>>> 91d0b42a (fix: adjust processor and checker to use the correct variable)
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.test.util.Strings;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
public class AuthorizationIntegrationTest {

<<<<<<< HEAD
  private static CamundaClient camundaClient;

  @Test
  void shouldCreateAndGetAuthorizationByAuthorizationKey() {
=======
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

  private static CamundaClient camundaClient;

  @BeforeAll
  public static void setup() {
    camundaClient
        .newCreateRoleCommand()
        .roleId(ROLE_ID_1)
        .name(ROLE_ID_1)
        .description(ROLE_ID_1 + " description")
        .send()
        .join();

    camundaClient
        .newCreateMappingRuleCommand()
        .mappingRuleId(MAPPING_RULE_ID_1)
        .claimName(MAPPING_RULE_ID_1 + "-cn")
        .claimValue(MAPPING_RULE_ID_1 + "-cv")
        .name(MAPPING_RULE_ID_1)
        .send()
        .join();

    camundaClient
        .newCreateGroupCommand()
        .groupId(GROUP_ID_1)
        .name(GROUP_ID_1)
        .description(GROUP_ID_1 + " description")
        .send()
        .join();
    Awaitility.await()
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final Group group = camundaClient.newGroupGetRequest(GROUP_ID_1).send().join();
              assertThat(group).isNotNull();
              final Role role = camundaClient.newRoleGetRequest(ROLE_ID_1).send().join();
              assertThat(role).isNotNull();
            });
  }

  @ParameterizedTest
  @MethodSource("getValidAuthorizationRequest")
  void shouldCreateAndGetAuthorizationByAuthorizationKey(
      final OwnerType ownerType,
      final ResourceType resourceType,
      final String ownerId,
      final String resourceId) {
>>>>>>> 91d0b42a (fix: adjust processor and checker to use the correct variable)
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

<<<<<<< HEAD
=======
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

>>>>>>> 91d0b42a (fix: adjust processor and checker to use the correct variable)
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
<<<<<<< HEAD
=======

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
>>>>>>> 91d0b42a (fix: adjust processor and checker to use the correct variable)
}
