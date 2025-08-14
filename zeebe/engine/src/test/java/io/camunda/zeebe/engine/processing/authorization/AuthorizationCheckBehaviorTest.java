/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.authorization;

import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_ANONYMOUS_USER;
import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_CLIENT_ID;
import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_USERNAME;
import static io.camunda.zeebe.auth.Authorization.USER_TOKEN_CLAIMS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.security.configuration.AuthorizationsConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.state.appliers.AuthorizationCreatedApplier;
import io.camunda.zeebe.engine.state.appliers.GroupCreatedApplier;
import io.camunda.zeebe.engine.state.appliers.GroupEntityAddedApplier;
import io.camunda.zeebe.engine.state.appliers.MappingRuleCreatedApplier;
import io.camunda.zeebe.engine.state.appliers.RoleCreatedApplier;
import io.camunda.zeebe.engine.state.appliers.RoleEntityAddedApplier;
import io.camunda.zeebe.engine.state.appliers.UserCreatedApplier;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.MappingRuleRecordValue;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class AuthorizationCheckBehaviorTest {

  @SuppressWarnings("unused") // injected by the extension
  private MutableProcessingState processingState;

  private AuthorizationCheckBehavior authorizationCheckBehavior;
  private UserCreatedApplier userCreatedApplier;
  private MappingRuleCreatedApplier mappingRuleCreatedApplier;
  private AuthorizationCreatedApplier authorizationCreatedApplier;
  private GroupCreatedApplier groupCreatedApplier;
  private GroupEntityAddedApplier groupEntityAddedApplier;
  private RoleCreatedApplier roleCreatedApplier;
  private RoleEntityAddedApplier roleEntityAddedApplier;
  private final Random random = new Random();

  @BeforeEach
  public void before() {
    final var securityConfig = new SecurityConfiguration();
    final var authConfig = new AuthorizationsConfiguration();
    authConfig.setEnabled(true);
    securityConfig.setAuthorizations(authConfig);
    authorizationCheckBehavior = new AuthorizationCheckBehavior(processingState, securityConfig);

    userCreatedApplier = new UserCreatedApplier(processingState.getUserState());
    mappingRuleCreatedApplier =
        new MappingRuleCreatedApplier(processingState.getMappingRuleState());
    authorizationCreatedApplier =
        new AuthorizationCreatedApplier(processingState.getAuthorizationState());
    groupCreatedApplier = new GroupCreatedApplier(processingState.getGroupState());
    groupEntityAddedApplier = new GroupEntityAddedApplier(processingState);
    roleCreatedApplier = new RoleCreatedApplier(processingState.getRoleState());
    roleEntityAddedApplier = new RoleEntityAddedApplier(processingState);
  }

  @Test
  void shouldBeAuthorizedWhenUserHasPermission() {
    // given
    final var user = createUser();
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = AuthorizationScope.of(UUID.randomUUID().toString());
    addPermission(
        user.getUsername(), AuthorizationOwnerType.USER, resourceType, permissionType, resourceId);
    final var command = mockCommand(user.getUsername());

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType)
            .addAuthorizationScope(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    assertThat(authorized.isRight()).isTrue();
  }

  @Test
  void shouldNotBeAuthorizedWhenUserHasNoPermission() {
    // given
    final var user = createUser();
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.DELETE;
    final var resourceId = UUID.randomUUID().toString();
    final var command = mockCommand(user.getUsername());

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType).addResourceId(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    assertThat(authorized.isRight()).isFalse();
  }

  @Test
  void shouldGetResourceIdentifiersWhenUserHasPermissions() {
    // given
    final var user = createUser();
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId1 = AuthorizationScope.of(UUID.randomUUID().toString());
    final var resourceId2 = AuthorizationScope.of(UUID.randomUUID().toString());
    addPermission(
        user.getUsername(),
        AuthorizationOwnerType.USER,
        resourceType,
        permissionType,
        resourceId1,
        resourceId2);
    final var command = mockCommand(user.getUsername());

    // when
    final var request = new AuthorizationRequest(command, resourceType, permissionType);
    final var resourceIdentifiers = authorizationCheckBehavior.getAllAuthorizedScopes(request);

    // then
    assertThat(resourceIdentifiers).containsExactlyInAnyOrder(resourceId1, resourceId2);
  }

  @Test
  void shouldGetEmptySetWhenUserHasNoPermissions() {
    // given
    final var user = createUser();
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.DELETE;
    final var command = mockCommand(user.getUsername());

    // when
    final var request = new AuthorizationRequest(command, resourceType, permissionType);
    final var resourceIdentifiers = authorizationCheckBehavior.getAllAuthorizedScopes(request);

    // then
    assertThat(resourceIdentifiers).isEmpty();
  }

  @Test
  void shouldBeAuthorizedWhenRoleHasPermissions() {
    // given
    final var user = createUser();
    final var role = createRoleAndAssignEntity(user.getUsername(), EntityType.USER);
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = AuthorizationScope.of(UUID.randomUUID().toString());
    addPermission(
        role.getRoleId(), AuthorizationOwnerType.ROLE, resourceType, permissionType, resourceId);
    final var command = mockCommand(user.getUsername());

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType)
            .addAuthorizationScope(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    assertThat(authorized.isRight()).isTrue();
  }

  @Test
  void shouldGetResourceIdentifiersWhenRoleHasPermissions() {
    // given
    final var user = createUser();
    final var role = createRoleAndAssignEntity(user.getUsername(), EntityType.USER);
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId1 = AuthorizationScope.of(UUID.randomUUID().toString());
    final var resourceId2 = AuthorizationScope.of(UUID.randomUUID().toString());
    addPermission(
        role.getRoleId(),
        AuthorizationOwnerType.ROLE,
        resourceType,
        permissionType,
        resourceId1,
        resourceId2);
    final var command = mockCommand(user.getUsername());

    // when
    final var request = new AuthorizationRequest(command, resourceType, permissionType);
    final var resourceIdentifiers = authorizationCheckBehavior.getAllAuthorizedScopes(request);

    // then
    assertThat(resourceIdentifiers).containsExactlyInAnyOrder(resourceId1, resourceId2);
  }

  @Test
  public void shouldBeAuthorizedWhenGroupHasPermissions() {
    // given
    final var user = createUser();
    final var group = createGroupAndAssignEntity(user.getUsername(), EntityType.USER);
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = AuthorizationScope.of(UUID.randomUUID().toString());
    addPermission(
        group.getGroupId(), AuthorizationOwnerType.GROUP, resourceType, permissionType, resourceId);
    final var command = mockCommand(user.getUsername());

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType)
            .addAuthorizationScope(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    assertThat(authorized.isRight()).isTrue();
  }

  @Test
  void shouldGetResourceIdentifiersWhenGroupHasPermissions() {
    // given
    final var user = createUser();
    final var group = createGroupAndAssignEntity(user.getUsername(), EntityType.USER);
    final var groupId = group.getGroupId();
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId1 = AuthorizationScope.of(UUID.randomUUID().toString());
    final var resourceId2 = AuthorizationScope.of(UUID.randomUUID().toString());
    addPermission(
        groupId,
        AuthorizationOwnerType.GROUP,
        resourceType,
        permissionType,
        resourceId1,
        resourceId2);
    final var command = mockCommand(user.getUsername());

    // when
    final var request = new AuthorizationRequest(command, resourceType, permissionType);
    final var resourceIdentifiers = authorizationCheckBehavior.getAllAuthorizedScopes(request);

    // then
    assertThat(resourceIdentifiers).containsExactlyInAnyOrder(resourceId1, resourceId2);
  }

  @Test
  void shouldBeAuthorizedWhenAnonymousAuthenticationProvided() {
    // given
    final var user = createUser();
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = AuthorizationScope.of(UUID.randomUUID().toString());
    addPermission(
        user.getUsername(), AuthorizationOwnerType.USER, resourceType, permissionType, resourceId);
    final var command = mockCommandWithAnonymousUser();

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType)
            .addAuthorizationScope(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    assertThat(authorized.isRight()).isTrue();
  }

  @Test
  void shouldBeAuthorizedWhenMappingHasPermission() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mappingRule = createMappingRule(claimName, claimValue);
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = AuthorizationScope.of(UUID.randomUUID().toString());
    addPermission(
        mappingRule.getMappingRuleId(),
        AuthorizationOwnerType.MAPPING_RULE,
        resourceType,
        permissionType,
        resourceId);
    final var command = mockCommandWithMappingRule(claimName, claimValue);

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType)
            .addAuthorizationScope(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    EitherAssert.assertThat(authorized).isRight();
  }

  @Test
  void shouldBeAuthorizedWhenMappingIsAuthorizedThroughGroup() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mappingRuleId = createMappingRule(claimName, claimValue).getMappingRuleId();
    final var group = createGroupAndAssignEntity(mappingRuleId, EntityType.MAPPING_RULE);
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = AuthorizationScope.of(UUID.randomUUID().toString());
    addPermission(
        group.getGroupId(), AuthorizationOwnerType.GROUP, resourceType, permissionType, resourceId);
    final var command = mockCommandWithMappingRule(claimName, claimValue);

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType)
            .addAuthorizationScope(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    EitherAssert.assertThat(authorized).isRight();
  }

  @Test
  void shouldBeAuthorizedWhenMappingIsAuthorizedThroughRole() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mappingRule = createMappingRule(claimName, claimValue);
    final var role =
        createRoleAndAssignEntity(mappingRule.getMappingRuleId(), EntityType.MAPPING_RULE);
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = AuthorizationScope.of(UUID.randomUUID().toString());
    addPermission(
        role.getRoleId(), AuthorizationOwnerType.ROLE, resourceType, permissionType, resourceId);

    final var command = mockCommandWithMappingRule(claimName, claimValue);

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType)
            .addAuthorizationScope(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    EitherAssert.assertThat(authorized).isRight();
  }

  @Test
  void shouldNotBeAuthorizedWhenMappingHasNoPermission() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    createMappingRule(claimName, claimValue);
    final var command = mockCommandWithMappingRule(claimName, claimValue);

    // when
    final var request =
        new AuthorizationRequest(command, AuthorizationResourceType.RESOURCE, PermissionType.DELETE)
            .addResourceId(UUID.randomUUID().toString());
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    EitherAssert.assertThat(authorized).isLeft();
  }

  @Test
  void shouldBeAuthorizedThroughMultipleMappings() {
    // given
    final var firstClaimName = UUID.randomUUID().toString();
    final var firstClaimValue = UUID.randomUUID().toString();
    final var firstMapping = createMappingRule(firstClaimName, firstClaimValue);
    final var secondClaimName = UUID.randomUUID().toString();
    final var secondClaimValue = UUID.randomUUID().toString();
    final var secondMapping = createMappingRule(secondClaimName, secondClaimValue);

    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var firstResourceId = AuthorizationScope.of(UUID.randomUUID().toString());
    final var secondResourceId = AuthorizationScope.of(UUID.randomUUID().toString());
    addPermission(
        firstMapping.getMappingRuleId(),
        AuthorizationOwnerType.MAPPING_RULE,
        resourceType,
        permissionType,
        firstResourceId);
    addPermission(
        secondMapping.getMappingRuleId(),
        AuthorizationOwnerType.MAPPING_RULE,
        resourceType,
        permissionType,
        secondResourceId);

    // when
    final var command = mock(TypedRecord.class);
    when(command.getAuthorizations())
        .thenReturn(
            Map.of(
                USER_TOKEN_CLAIMS,
                Map.of(firstClaimName, firstClaimValue, secondClaimName, secondClaimValue)));
    when(command.hasRequestMetadata()).thenReturn(true);

    // then
    EitherAssert.assertThat(
            authorizationCheckBehavior.isAuthorized(
                new AuthorizationRequest(command, resourceType, permissionType)
                    .addAuthorizationScope(firstResourceId)))
        .isRight();
    EitherAssert.assertThat(
            authorizationCheckBehavior.isAuthorized(
                new AuthorizationRequest(command, resourceType, permissionType)
                    .addAuthorizationScope(secondResourceId)))
        .isRight();
  }

  @Test
  void shouldBeAuthorizedThroughMappingWithMultipleClaimValues() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var firstClaimValue = UUID.randomUUID().toString();
    final var firstMapping = createMappingRule(claimName, firstClaimValue);
    final var secondClaimValue = UUID.randomUUID().toString();
    final var secondMapping = createMappingRule(claimName, secondClaimValue);

    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var firstResourceId = AuthorizationScope.of(UUID.randomUUID().toString());
    final var secondResourceId = AuthorizationScope.of(UUID.randomUUID().toString());
    addPermission(
        firstMapping.getMappingRuleId(),
        AuthorizationOwnerType.MAPPING_RULE,
        resourceType,
        permissionType,
        firstResourceId);
    addPermission(
        secondMapping.getMappingRuleId(),
        AuthorizationOwnerType.MAPPING_RULE,
        resourceType,
        permissionType,
        secondResourceId);

    // when
    final var command = mock(TypedRecord.class);
    when(command.getAuthorizations())
        .thenReturn(
            Map.of(
                USER_TOKEN_CLAIMS, Map.of(claimName, List.of(firstClaimValue, secondClaimValue))));
    when(command.hasRequestMetadata()).thenReturn(true);

    // then
    EitherAssert.assertThat(
            authorizationCheckBehavior.isAuthorized(
                new AuthorizationRequest(command, resourceType, permissionType)
                    .addAuthorizationScope(firstResourceId)))
        .isRight();
    EitherAssert.assertThat(
            authorizationCheckBehavior.isAuthorized(
                new AuthorizationRequest(command, resourceType, permissionType)
                    .addAuthorizationScope(secondResourceId)))
        .isRight();
  }

  @Test
  void shouldGetAuthorizationsForMapping() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mappingRule = createMappingRule(claimName, claimValue);
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = AuthorizationScope.of(UUID.randomUUID().toString());
    addPermission(
        mappingRule.getMappingRuleId(),
        AuthorizationOwnerType.MAPPING_RULE,
        resourceType,
        permissionType,
        resourceId);
    final var command = mockCommandWithMappingRule(claimName, claimValue);

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType)
            .addAuthorizationScope(resourceId);
    final var authorizations = authorizationCheckBehavior.getAllAuthorizedScopes(request);

    // then
    assertThat(authorizations).containsExactlyInAnyOrder(resourceId);
  }

  @Test
  void shouldGetAuthorizationsForNestedMapping() {
    // given
    final var claimName = "$.nested.claim";
    final var claimValue = UUID.randomUUID().toString();
    final var mappingRule = createMappingRule(claimName, claimValue);
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = AuthorizationScope.of(UUID.randomUUID().toString());
    addPermission(
        mappingRule.getMappingRuleId(),
        AuthorizationOwnerType.MAPPING_RULE,
        resourceType,
        permissionType,
        resourceId);
    final var command = mock(TypedRecord.class);
    when(command.getAuthorizations())
        .thenReturn(Map.of(USER_TOKEN_CLAIMS, Map.of("nested", Map.of("claim", claimValue))));
    when(command.hasRequestMetadata()).thenReturn(true);

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType)
            .addAuthorizationScope(resourceId);
    final var authorizations = authorizationCheckBehavior.getAllAuthorizedScopes(request);

    // then
    assertThat(authorizations).containsExactlyInAnyOrder(resourceId);
  }

  @Test
  void shouldGetAuthorizationsForMappingThroughAssignedRole() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mappingRule = createMappingRule(claimName, claimValue);
    final var role =
        createRoleAndAssignEntity(mappingRule.getMappingRuleId(), EntityType.MAPPING_RULE);
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = AuthorizationScope.of(UUID.randomUUID().toString());
    addPermission(
        role.getRoleId(), AuthorizationOwnerType.ROLE, resourceType, permissionType, resourceId);
    final var command = mockCommandWithMappingRule(claimName, claimValue);

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType)
            .addAuthorizationScope(resourceId);
    final var authorizations = authorizationCheckBehavior.getAllAuthorizedScopes(request);

    // then
    assertThat(authorizations).containsExactlyInAnyOrder(resourceId);
  }

  @Test
  void shouldGetAuthorizationsForMappingThroughAssignedGroup() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mappingRule = createMappingRule(claimName, claimValue);
    final var group =
        createGroupAndAssignEntity(mappingRule.getMappingRuleId(), EntityType.MAPPING_RULE);
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = AuthorizationScope.of(UUID.randomUUID().toString());
    addPermission(
        group.getGroupId(), AuthorizationOwnerType.GROUP, resourceType, permissionType, resourceId);
    final var command = mockCommandWithMappingRule(claimName, claimValue);

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType)
            .addAuthorizationScope(resourceId);
    final var authorizations = authorizationCheckBehavior.getAllAuthorizedScopes(request);

    // then
    assertThat(authorizations).containsExactlyInAnyOrder(resourceId);
  }

  @Test
  void shouldBeAuthorizedForUserWithAssignedGroupWithAssignedRole() {
    // given
    final var user = createUser();
    final var group = createGroupAndAssignEntity(user.getUsername(), EntityType.USER);
    final var role = createRoleAndAssignEntity(group.getGroupId(), EntityType.GROUP);

    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = AuthorizationScope.of(UUID.randomUUID().toString());
    addPermission(
        role.getRoleId(), AuthorizationOwnerType.ROLE, resourceType, permissionType, resourceId);
    final var command = mockCommand(user.getUsername());

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType)
            .addAuthorizationScope(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    assertThat(authorized.isRight()).isTrue();
  }

  @Test
  void shouldGetAuthorizationsForUserWithAssignedGroupWithAssignedRole() {
    // given
    final var user = createUser();
    final var group = createGroupAndAssignEntity(user.getUsername(), EntityType.USER);
    final var role = createRoleAndAssignEntity(group.getGroupId(), EntityType.GROUP);

    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = AuthorizationScope.of(UUID.randomUUID().toString());
    addPermission(
        role.getRoleId(), AuthorizationOwnerType.ROLE, resourceType, permissionType, resourceId);
    final var command = mockCommand(user.getUsername());

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType)
            .addAuthorizationScope(resourceId);
    final var allAuthorizedResourceIdentifiers =
        authorizationCheckBehavior.getAllAuthorizedScopes(request);
    final var directAuthorizedResourceIdentifiers =
        authorizationCheckBehavior.getDirectAuthorizedAuthorizationScopes(
            AuthorizationOwnerType.USER, user.getUsername(), resourceType, permissionType);

    // then
    assertThat(allAuthorizedResourceIdentifiers).containsExactly(resourceId);
    assertThat(directAuthorizedResourceIdentifiers).isEmpty();
  }

  @Test
  void shouldBeAuthorizedForMappingWithAssignedGroupWithAssignedRole() {
    // given
    final var mappingRule =
        createMappingRule(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    final var group =
        createGroupAndAssignEntity(mappingRule.getMappingRuleId(), EntityType.MAPPING_RULE);
    final var role = createRoleAndAssignEntity(group.getGroupId(), EntityType.GROUP);

    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = AuthorizationScope.of(UUID.randomUUID().toString());
    addPermission(
        role.getRoleId(), AuthorizationOwnerType.ROLE, resourceType, permissionType, resourceId);
    final var command =
        mockCommandWithMappingRule(mappingRule.getClaimName(), mappingRule.getClaimValue());

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType)
            .addAuthorizationScope(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    assertThat(authorized.isRight()).isTrue();
  }

  @Test
  void shouldGetAuthorizationsForMappingWithAssignedGroupWithAssignedRole() {
    // given
    final var mappingRule =
        createMappingRule(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    final var group =
        createGroupAndAssignEntity(mappingRule.getMappingRuleId(), EntityType.MAPPING_RULE);
    final var role = createRoleAndAssignEntity(group.getGroupId(), EntityType.GROUP);

    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = AuthorizationScope.of(UUID.randomUUID().toString());
    addPermission(
        role.getRoleId(), AuthorizationOwnerType.ROLE, resourceType, permissionType, resourceId);
    final var command =
        mockCommandWithMappingRule(mappingRule.getClaimName(), mappingRule.getClaimValue());

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType)
            .addAuthorizationScope(resourceId);
    final var allAuthorizedResourceIdentifiers =
        authorizationCheckBehavior.getAllAuthorizedScopes(request);
    final var directAuthorizedResourceIdentifiers =
        authorizationCheckBehavior.getDirectAuthorizedAuthorizationScopes(
            AuthorizationOwnerType.MAPPING_RULE,
            mappingRule.getMappingRuleId(),
            resourceType,
            permissionType);

    // then
    assertThat(allAuthorizedResourceIdentifiers).containsExactly(resourceId);
    assertThat(directAuthorizedResourceIdentifiers).isEmpty();
  }

  @Test
  void shouldBeAuthorizedWhenClientHasPermission() {
    // given
    final var clientId = createClientId();
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = AuthorizationScope.of(UUID.randomUUID().toString());
    addPermission(
        clientId, AuthorizationOwnerType.CLIENT, resourceType, permissionType, resourceId);
    final var command = mockCommandWithClientId(clientId);

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType)
            .addAuthorizationScope(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    assertThat(authorized.isRight()).isTrue();
  }

  @Test
  void shouldNotBeAuthorizedWhenApplicationHasNoPermission() {
    // given
    final var clientId = createClientId();
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.DELETE;
    final var resourceId = AuthorizationScope.of(UUID.randomUUID().toString());
    final var command = mockCommandWithClientId(clientId);

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType)
            .addAuthorizationScope(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    assertThat(authorized.isRight()).isFalse();
  }

  @Test
  void shouldGetResourceIdentifiersWhenApplicationHasPermissions() {
    // given
    final var clientId = createClientId();
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId1 = AuthorizationScope.of(UUID.randomUUID().toString());
    final var resourceId2 = AuthorizationScope.of(UUID.randomUUID().toString());
    addPermission(
        clientId,
        AuthorizationOwnerType.CLIENT,
        resourceType,
        permissionType,
        resourceId1,
        resourceId2);
    final var command = mockCommandWithClientId(clientId);

    // when
    final var request = new AuthorizationRequest(command, resourceType, permissionType);
    final var resourceIdentifiers = authorizationCheckBehavior.getAllAuthorizedScopes(request);

    // then
    assertThat(resourceIdentifiers).containsExactlyInAnyOrder(resourceId1, resourceId2);
  }

  @Test
  void shouldGetEmptySetWhenApplicationHasNoPermissions() {
    // given
    final var clientId = createClientId();
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.DELETE;
    final var command = mockCommandWithClientId(clientId);

    // when
    final var request = new AuthorizationRequest(command, resourceType, permissionType);
    final var resourceIdentifiers = authorizationCheckBehavior.getAllAuthorizedScopes(request);

    // then
    assertThat(resourceIdentifiers).isEmpty();
  }

  private TypedRecord<?> mockCommandWithMappingRule(
      final String claimName, final String claimValue) {
    final var command = mock(TypedRecord.class);
    when(command.getAuthorizations())
        .thenReturn(Map.of(USER_TOKEN_CLAIMS, Map.of(claimName, claimValue)));
    when(command.hasRequestMetadata()).thenReturn(true);
    return command;
  }

  private UserRecordValue createUser() {
    final var userKey = random.nextLong();
    final var user =
        new UserRecord()
            .setUserKey(userKey)
            .setUsername(Strings.newRandomValidUsername())
            .setName(UUID.randomUUID().toString())
            .setEmail(UUID.randomUUID().toString())
            .setPassword(UUID.randomUUID().toString());
    userCreatedApplier.applyState(userKey, user);
    return user;
  }

  private RoleRecord createRoleAndAssignEntity(final String entityId, final EntityType entityType) {
    final var role =
        new RoleRecord()
            .setRoleId(Strings.newRandomValidIdentityId())
            .setName(UUID.randomUUID().toString())
            .setDescription(UUID.randomUUID().toString())
            .setEntityId(entityId)
            .setEntityType(entityType);
    roleCreatedApplier.applyState(1L, role);
    roleEntityAddedApplier.applyState(1L, role);
    return role;
  }

  private GroupRecord createGroupAndAssignEntity(
      final String entityId, final EntityType entityType) {
    final var groupId = Strings.newRandomValidIdentityId();
    final var group =
        new GroupRecord()
            .setGroupId(groupId)
            .setName(UUID.randomUUID().toString())
            .setDescription(UUID.randomUUID().toString())
            .setEntityId(entityId)
            .setEntityType(entityType);
    groupCreatedApplier.applyState(1L, group);
    groupEntityAddedApplier.applyState(1L, group);
    return group;
  }

  private MappingRuleRecordValue createMappingRule(
      final String claimName, final String claimValue) {
    final var mappingRule =
        new MappingRuleRecord()
            .setMappingRuleId(UUID.randomUUID().toString())
            .setName(Strings.newRandomValidUsername())
            .setClaimName(claimName)
            .setClaimValue(claimValue);
    mappingRuleCreatedApplier.applyState(random.nextLong(), mappingRule);
    return mappingRule;
  }

  private void addPermission(
      final String ownerId,
      final AuthorizationOwnerType ownerType,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType,
      final AuthorizationScope... authorizationScopes) {
    for (final AuthorizationScope authorizationScope : authorizationScopes) {
      final var authorizationKey = random.nextLong();
      final var authorization =
          new AuthorizationRecord()
              .setAuthorizationKey(authorizationKey)
              .setOwnerId(ownerId)
              .setOwnerType(ownerType)
              .setResourceMatcher(authorizationScope.getMatcher())
              .setResourceId(authorizationScope.getResourceId())
              .setResourceType(resourceType)
              .setPermissionTypes(Set.of(permissionType));
      authorizationCreatedApplier.applyState(authorizationKey, authorization);
    }
  }

  private TypedRecord<?> mockCommand(final String username) {
    final var command = mock(TypedRecord.class);
    when(command.getAuthorizations()).thenReturn(Map.of(AUTHORIZED_USERNAME, username));
    when(command.hasRequestMetadata()).thenReturn(true);
    return command;
  }

  private TypedRecord<?> mockCommandWithAnonymousUser() {
    final var command = mock(TypedRecord.class);
    when(command.getAuthorizations()).thenReturn(Map.of(AUTHORIZED_ANONYMOUS_USER, true));
    when(command.hasRequestMetadata()).thenReturn(true);
    return command;
  }

  private TypedRecord<?> mockCommandWithClientId(final String clientId) {
    final var command = mock(TypedRecord.class);
    when(command.getAuthorizations()).thenReturn(Map.of(AUTHORIZED_CLIENT_ID, clientId));
    when(command.hasRequestMetadata()).thenReturn(true);
    return command;
  }

  private String createClientId() {
    return Strings.newRandomValidIdentityId();
  }
}
