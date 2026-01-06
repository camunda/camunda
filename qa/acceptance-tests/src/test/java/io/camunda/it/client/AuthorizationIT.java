/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.client.api.search.enums.PermissionType.CREATE;
import static io.camunda.client.api.search.enums.PermissionType.UPDATE;
import static io.camunda.it.client.AuthorizationIT.AuthorizationRequestParam.idBased;
import static io.camunda.it.client.AuthorizationIT.AuthorizationRequestParam.propertyBased;
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
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.test.util.Strings;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@CompatibilityTest
@MultiDbTest
public class AuthorizationIT {

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
  void shouldCreateAndGetAuthorizationByAuthorizationKey(final AuthorizationRequestParam request) {
    // when
    final CreateAuthorizationResponse authorization = createAuthorization(request);
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
              if (request.isIdBased()) {
                assertThat(retrievedAuthorization.getResourceId()).isEqualTo(request.resourceId);
                assertThat(retrievedAuthorization.getResourcePropertyName()).isNull();
              } else {
                assertThat(retrievedAuthorization.getResourcePropertyName())
                    .isEqualTo(request.resourcePropertyName);
                assertThat(retrievedAuthorization.getResourceId()).isNull();
              }
              assertThat(retrievedAuthorization.getResourceType()).isEqualTo(request.resourceType);
              assertThat(retrievedAuthorization.getOwnerId()).isEqualTo(request.ownerId);
              assertThat(retrievedAuthorization.getOwnerType()).isEqualTo(request.ownerType);
              assertThat(retrievedAuthorization.getPermissionTypes())
                  .isEqualTo(List.of(request.permissionType));
            });
  }

  private CreateAuthorizationResponse createAuthorization(final AuthorizationRequestParam request) {
    final var command =
        camundaClient
            .newCreateAuthorizationCommand()
            .ownerId(request.ownerId)
            .ownerType(request.ownerType);

    if (request.isIdBased()) {
      return command
          .resourceId(request.resourceId)
          .resourceType(request.resourceType)
          .permissionTypes(request.permissionType)
          .execute();
    } else {
      return command
          .resourcePropertyName(request.resourcePropertyName)
          .resourceType(request.resourceType)
          .permissionTypes(request.permissionType)
          .execute();
    }
  }

  @ParameterizedTest
  @MethodSource("getValidAuthorizationRequest")
  void shouldUpdateValidAuthorizationByAuthorizationKey(final AuthorizationRequestParam request) {
    // when
    final CreateAuthorizationResponse authorization =
        createAuthorization(
            new AuthorizationRequestParam(
                request.ownerType,
                // create authorization with a fixed resource type to update it later
                ResourceType.AUTHORIZATION,
                request.ownerId,
                request.resourceId,
                request.resourcePropertyName,
                request.permissionType));

    final long authorizationKey = authorization.getAuthorizationKey();
    assertThat(authorizationKey).isGreaterThan(0);

    // then
    Awaitility.await()
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final Authorization retrievedAuthorization =
                  camundaClient.newAuthorizationGetRequest(authorizationKey).execute();
              assertThat(retrievedAuthorization).isNotNull();
              assertThat(retrievedAuthorization.getResourceType())
                  .isEqualTo(ResourceType.AUTHORIZATION);
            });

    updateAuthorization(authorizationKey, request);

    Awaitility.await()
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final Authorization retrievedAuthorization =
                  camundaClient.newAuthorizationGetRequest(authorizationKey).send().join();
              assertThat(retrievedAuthorization.getAuthorizationKey())
                  .isEqualTo(String.valueOf(authorizationKey));
              if (request.isIdBased()) {
                assertThat(retrievedAuthorization.getResourceId()).isEqualTo(request.resourceId);
                assertThat(retrievedAuthorization.getResourcePropertyName()).isNull();
              } else {
                assertThat(retrievedAuthorization.getResourcePropertyName())
                    .isEqualTo(request.resourcePropertyName);
                assertThat(retrievedAuthorization.getResourceId()).isNull();
              }
              assertThat(retrievedAuthorization.getResourceType()).isEqualTo(request.resourceType);
              assertThat(retrievedAuthorization.getOwnerId()).isEqualTo(request.ownerId);
              assertThat(retrievedAuthorization.getOwnerType()).isEqualTo(request.ownerType);
              assertThat(retrievedAuthorization.getPermissionTypes())
                  .isEqualTo(List.of(request.permissionType));
            });
  }

  private void updateAuthorization(
      final long authorizationKey, final AuthorizationRequestParam request) {
    final var command =
        camundaClient
            .newUpdateAuthorizationCommand(authorizationKey)
            .ownerId(request.ownerId)
            .ownerType(request.ownerType);

    if (request.isIdBased()) {
      command
          .resourceId(request.resourceId)
          .resourceType(request.resourceType)
          .permissionTypes(request.permissionType)
          .execute();
    } else {
      command
          .resourcePropertyName(request.resourcePropertyName)
          .resourceType(request.resourceType)
          .permissionTypes(request.permissionType)
          .execute();
    }
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
    // when - then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newCreateAuthorizationCommand()
                    .ownerId(ownerId)
                    .ownerType(ownerType)
                    .resourceId(resourceId)
                    .resourceType(resourceType)
                    .permissionTypes(CREATE)
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
            .permissionTypes(CREATE)
            .send()
            .join();

    // create one more authorization
    camundaClient
        .newCreateAuthorizationCommand()
        .ownerId(USER_ID_2)
        .ownerType(OwnerType.USER)
        .resourceId(Strings.newRandomValidIdentityId())
        .resourceType(ResourceType.RESOURCE)
        .permissionTypes(CREATE)
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
            .permissionTypes(CREATE)
            .send()
            .join();

    // create one more authorization
    camundaClient
        .newCreateAuthorizationCommand()
        .ownerId(USER_ID_2)
        .ownerType(OwnerType.USER)
        .resourceId("someOtherId")
        .resourceType(ResourceType.RESOURCE)
        .permissionTypes(CREATE)
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
  void searchShouldReturnAuthorizationsFilteredByResourcePropertyName() {
    // when
    final var resourcePropertyName = "propCandidateGroups";

    final CreateAuthorizationResponse authorization =
        camundaClient
            .newCreateAuthorizationCommand()
            .ownerId(USER_ID_1)
            .ownerType(OwnerType.USER)
            .resourcePropertyName(resourcePropertyName)
            .resourceType(ResourceType.USER_TASK)
            .permissionTypes(PermissionType.CLAIM)
            .execute();

    // create one more property-based authorization
    camundaClient
        .newCreateAuthorizationCommand()
        .ownerId(USER_ID_2)
        .ownerType(OwnerType.USER)
        .resourcePropertyName("someOtherProperty")
        .resourceType(ResourceType.USER_TASK)
        .permissionTypes(PermissionType.CLAIM)
        .execute();

    Awaitility.await()
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var authorizationsSearchResponse =
                  camundaClient
                      .newAuthorizationSearchRequest()
                      .filter(fn -> fn.resourcePropertyNames(resourcePropertyName))
                      .execute();
              assertThat(authorizationsSearchResponse.items())
                  .singleElement()
                  .satisfies(
                      a -> {
                        assertThat(a.getAuthorizationKey())
                            .isEqualTo(Long.toString(authorization.getAuthorizationKey()));
                        assertThat(a.getResourcePropertyName()).isEqualTo(resourcePropertyName);
                      });
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
        .permissionTypes(CREATE)
        .send()
        .join();

    // when
    camundaClient
        .newCreateAuthorizationCommand()
        .ownerId(USER_ID_2)
        .ownerType(OwnerType.USER)
        .resourceId(Strings.newRandomValidIdentityId())
        .resourceType(ResourceType.RESOURCE)
        .permissionTypes(CREATE)
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
  void searchShouldReturnAuthorizationsSortedByResourceId() {
    // given: execute all authorization creation commands in parallel
    final var future1 =
        camundaClient
            .newCreateAuthorizationCommand()
            .ownerId(USER_ID_1)
            .ownerType(OwnerType.USER)
            .resourceId("resource-zebra")
            .resourceType(ResourceType.RESOURCE)
            .permissionTypes(CREATE)
            .send();

    final var future2 =
        camundaClient
            .newCreateAuthorizationCommand()
            .ownerId(USER_ID_1)
            .ownerType(OwnerType.USER)
            .resourceId("resource-alpha")
            .resourceType(ResourceType.RESOURCE)
            .permissionTypes(CREATE)
            .send();

    final var future3 =
        camundaClient
            .newCreateAuthorizationCommand()
            .ownerId(USER_ID_2)
            .ownerType(OwnerType.USER)
            .resourceId("resource-beta")
            .resourceType(ResourceType.RESOURCE)
            .permissionTypes(CREATE)
            .send();

    CompletableFuture.allOf(
            future1.toCompletableFuture(),
            future2.toCompletableFuture(),
            future3.toCompletableFuture())
        .join();

    Awaitility.await()
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              // when
              final var authorizationsSearchResponse =
                  camundaClient
                      .newAuthorizationSearchRequest()
                      .filter(
                          f -> f.resourceIds("resource-zebra", "resource-alpha", "resource-beta"))
                      .sort(s -> s.resourceId().asc())
                      .send()
                      .join();

              // then
              assertThat(authorizationsSearchResponse.items())
                  .map(Authorization::getResourceId)
                  .containsExactly("resource-alpha", "resource-beta", "resource-zebra");
            });
  }

  @Test
  void searchShouldReturnAuthorizationsSortedByResourcePropertyNames() {
    // given: execute all authorization creation commands in parallel
    final var future1 =
        camundaClient
            .newCreateAuthorizationCommand()
            .ownerId(USER_ID_1)
            .ownerType(OwnerType.USER)
            .resourcePropertyName("prop-oscar")
            .resourceType(ResourceType.USER_TASK)
            .permissionTypes(UPDATE)
            .send();

    final var future2 =
        camundaClient
            .newCreateAuthorizationCommand()
            .ownerId(USER_ID_1)
            .ownerType(OwnerType.USER)
            .resourcePropertyName("prop-tango")
            .resourceType(ResourceType.RESOURCE)
            .permissionTypes(CREATE)
            .send();

    final var future3 =
        camundaClient
            .newCreateAuthorizationCommand()
            .ownerId(USER_ID_2)
            .ownerType(OwnerType.USER)
            .resourcePropertyName("prop-lima")
            .resourceType(ResourceType.RESOURCE)
            .permissionTypes(CREATE)
            .send();

    CompletableFuture.allOf(
            future1.toCompletableFuture(),
            future2.toCompletableFuture(),
            future3.toCompletableFuture())
        .join();

    Awaitility.await()
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              // when
              final var authorizationsSearchResponse =
                  camundaClient
                      .newAuthorizationSearchRequest()
                      .filter(f -> f.resourcePropertyNames("prop-oscar", "prop-tango", "prop-lima"))
                      .sort(s -> s.resourcePropertyName().desc())
                      .execute();

              // then
              assertThat(authorizationsSearchResponse.items())
                  .map(Authorization::getResourcePropertyName)
                  .containsExactly("prop-tango", "prop-oscar", "prop-lima");
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

  public static Stream<AuthorizationRequestParam> getValidAuthorizationRequest() {
    return Stream.of(
        idBased(OwnerType.USER, ResourceType.RESOURCE, USER_ID_1, "resource1", CREATE),
        idBased(OwnerType.USER, ResourceType.RESOURCE, USER_ID_1, "*", CREATE),
        propertyBased(OwnerType.USER, ResourceType.RESOURCE, USER_ID_1, "alfa", CREATE),
        idBased(OwnerType.ROLE, ResourceType.RESOURCE, ROLE_ID_1, "resource1", CREATE),
        idBased(OwnerType.ROLE, ResourceType.RESOURCE, ROLE_ID_1, "*", CREATE),
        propertyBased(OwnerType.ROLE, ResourceType.RESOURCE, ROLE_ID_1, "bravo", CREATE),
        idBased(OwnerType.GROUP, ResourceType.RESOURCE, GROUP_ID_1, "resource1", CREATE),
        idBased(OwnerType.GROUP, ResourceType.RESOURCE, GROUP_ID_1, "*", CREATE),
        propertyBased(OwnerType.GROUP, ResourceType.RESOURCE, GROUP_ID_1, "charlie", CREATE),
        idBased(
            OwnerType.CLIENT,
            ResourceType.RESOURCE,
            Strings.newRandomValidIdentityId(),
            "*",
            CREATE),
        idBased(
            OwnerType.CLIENT,
            ResourceType.RESOURCE,
            Strings.newRandomValidIdentityId(),
            "resource1",
            CREATE),
        propertyBased(
            OwnerType.CLIENT,
            ResourceType.RESOURCE,
            Strings.newRandomValidIdentityId(),
            "delta",
            CREATE),
        idBased(
            OwnerType.MAPPING_RULE, ResourceType.RESOURCE, MAPPING_RULE_ID_1, "resource1", CREATE),
        idBased(OwnerType.MAPPING_RULE, ResourceType.RESOURCE, MAPPING_RULE_ID_1, "*", CREATE),
        propertyBased(
            OwnerType.MAPPING_RULE, ResourceType.RESOURCE, MAPPING_RULE_ID_1, "echo", CREATE),
        idBased(OwnerType.MAPPING_RULE, ResourceType.USER, MAPPING_RULE_ID_1, USER_ID_1, CREATE),
        idBased(OwnerType.MAPPING_RULE, ResourceType.USER, MAPPING_RULE_ID_1, "*", CREATE),
        propertyBased(
            OwnerType.MAPPING_RULE, ResourceType.USER_TASK, MAPPING_RULE_ID_1, "fox", UPDATE),
        idBased(OwnerType.MAPPING_RULE, ResourceType.ROLE, MAPPING_RULE_ID_1, ROLE_ID_1, CREATE),
        idBased(OwnerType.MAPPING_RULE, ResourceType.ROLE, MAPPING_RULE_ID_1, "*", CREATE),
        idBased(OwnerType.MAPPING_RULE, ResourceType.GROUP, MAPPING_RULE_ID_1, GROUP_ID_1, CREATE),
        idBased(OwnerType.MAPPING_RULE, ResourceType.GROUP, MAPPING_RULE_ID_1, "*", CREATE),
        idBased(OwnerType.ROLE, ResourceType.MAPPING_RULE, ROLE_ID_1, MAPPING_RULE_ID_1, CREATE),
        idBased(OwnerType.ROLE, ResourceType.MAPPING_RULE, ROLE_ID_1, "*", CREATE),
        propertyBased(OwnerType.ROLE, ResourceType.USER_TASK, ROLE_ID_1, "golf", UPDATE));
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

  public record AuthorizationRequestParam(
      OwnerType ownerType,
      ResourceType resourceType,
      String ownerId,
      String resourceId,
      String resourcePropertyName,
      PermissionType permissionType) {

    static AuthorizationRequestParam idBased(
        final OwnerType ownerType,
        final ResourceType resourceType,
        final String ownerId,
        final String resourceId,
        final PermissionType permissionType) {
      return new AuthorizationRequestParam(
          ownerType, resourceType, ownerId, resourceId, null, permissionType);
    }

    static AuthorizationRequestParam propertyBased(
        final OwnerType ownerType,
        final ResourceType resourceType,
        final String ownerId,
        final String resourcePropertyName,
        final PermissionType permissionType) {
      return new AuthorizationRequestParam(
          ownerType, resourceType, ownerId, null, resourcePropertyName, permissionType);
    }

    public boolean isIdBased() {
      return resourceId != null;
    }
  }
}
