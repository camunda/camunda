/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.authorization;

import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_ANONYMOUS_USER;
import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_USERNAME;
import static io.camunda.zeebe.auth.Authorization.USER_TOKEN_CLAIMS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.security.configuration.AuthorizationsConfiguration;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.identity.AuthorizedTenants;
import io.camunda.zeebe.engine.state.appliers.AuthorizationCreatedApplier;
import io.camunda.zeebe.engine.state.appliers.GroupCreatedApplier;
import io.camunda.zeebe.engine.state.appliers.GroupEntityAddedApplier;
import io.camunda.zeebe.engine.state.appliers.MappingRuleCreatedApplier;
import io.camunda.zeebe.engine.state.appliers.RoleCreatedApplier;
import io.camunda.zeebe.engine.state.appliers.RoleEntityAddedApplier;
import io.camunda.zeebe.engine.state.appliers.TenantCreatedApplier;
import io.camunda.zeebe.engine.state.appliers.TenantEntityAddedApplier;
import io.camunda.zeebe.engine.state.appliers.UserCreatedApplier;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.MappingRuleRecordValue;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
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
final class AuthorizationCheckBehaviorMultiTenancyTest {

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
  private TenantCreatedApplier tenantCreatedApplier;
  private TenantEntityAddedApplier tenantEntityAddedApplier;
  private final Random random = new Random();

  @BeforeEach
  void before() {
    final var securityConfig = new SecurityConfiguration();
    final var authConfig = new AuthorizationsConfiguration();
    authConfig.setEnabled(true);
    securityConfig.setAuthorizations(authConfig);
    final var multiTenancyConfig = new MultiTenancyConfiguration();
    multiTenancyConfig.setChecksEnabled(true);
    securityConfig.setMultiTenancy(multiTenancyConfig);
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
    tenantCreatedApplier = new TenantCreatedApplier(processingState.getTenantState());
    tenantEntityAddedApplier = new TenantEntityAddedApplier(processingState);
  }

  @Test
  void shouldBeAuthorizedForUserTenant() {
    // given
    final var user = createUser();
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(
        user.getUsername(), AuthorizationOwnerType.USER, resourceType, permissionType, resourceId);
    final var tenantId = createAndAssignTenant(user.getUsername(), EntityType.USER);
    final var command = mockCommand(user.getUsername());

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType, tenantId)
            .addResourceId(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    assertThat(authorized.isRight()).isTrue();
  }

  @Test
  void shouldBeAuthorizedForUserTenantThroughGroup() {
    // given
    final var user = createUser();
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(
        user.getUsername(), AuthorizationOwnerType.USER, resourceType, permissionType, resourceId);
    final var group = createGroupAndAssignEntity(user.getUsername(), EntityType.USER);
    final var tenantId = createAndAssignTenant(group.getGroupId(), EntityType.GROUP);
    final var command = mockCommand(user.getUsername());

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType, tenantId)
            .addResourceId(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    assertThat(authorized.isRight()).isTrue();
  }

  @Test
  void shouldBeAuthorizedForMappingTenant() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mappingRuleId = createMappingRule(claimName, claimValue).getMappingRuleId();
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(
        mappingRuleId,
        AuthorizationOwnerType.MAPPING_RULE,
        resourceType,
        permissionType,
        resourceId);
    final var tenantId = createAndAssignTenant(mappingRuleId, EntityType.MAPPING_RULE);
    final var command = mockCommandWithMappingRule(claimName, claimValue);

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType, tenantId)
            .addResourceId(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    assertThat(authorized.isRight()).isTrue();
  }

  @Test
  void shouldBeAuthorizedForMappingTenantThroughGroup() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mappingRule = createMappingRule(claimName, claimValue);
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(
        mappingRule.getMappingRuleId(),
        AuthorizationOwnerType.MAPPING_RULE,
        resourceType,
        permissionType,
        resourceId);
    final var group =
        createGroupAndAssignEntity(mappingRule.getMappingRuleId(), EntityType.MAPPING_RULE);
    final var tenantId = createAndAssignTenant(group.getGroupId(), EntityType.GROUP);
    final var command = mockCommandWithMappingRule(claimName, claimValue);

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType, tenantId)
            .addResourceId(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    assertThat(authorized.isRight()).isTrue();
  }

  @Test
  void shouldGetUserAuthorizedTenantIds() {
    // given
    final var user = createUser();
    final var tenantId1 = createAndAssignTenant(user.getUsername(), EntityType.USER);
    final var tenantId2 = createAndAssignTenant(user.getUsername(), EntityType.USER);
    final var command = mockCommand(user.getUsername());

    // when
    final var authorizedTenantIds = authorizationCheckBehavior.getAuthorizedTenantIds(command);

    // then
    assertThat(authorizedTenantIds.getAuthorizedTenantIds())
        .containsExactlyInAnyOrder(tenantId1, tenantId2);
    assertThat(authorizedTenantIds.isAuthorizedForTenantId(tenantId1)).isTrue();
    assertThat(authorizedTenantIds.isAuthorizedForTenantId(tenantId2)).isTrue();
    assertThat(authorizedTenantIds.isAuthorizedForTenantIds(List.of(tenantId1, tenantId2)))
        .isTrue();
    assertThat(authorizedTenantIds.isAuthorizedForTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER))
        .isFalse();
  }

  @Test
  void shouldGetMappingAuthorizedTenantIds() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mappingRuleId = createMappingRule(claimName, claimValue).getMappingRuleId();
    final var tenantId1 = createAndAssignTenant(mappingRuleId, EntityType.MAPPING_RULE);
    final var tenantId2 = createAndAssignTenant(mappingRuleId, EntityType.MAPPING_RULE);
    final var command = mockCommandWithMappingRule(claimName, claimValue);

    // when
    final var authorizedTenantIds = authorizationCheckBehavior.getAuthorizedTenantIds(command);

    // then
    assertThat(authorizedTenantIds.getAuthorizedTenantIds())
        .containsExactlyInAnyOrder(tenantId1, tenantId2);
    assertThat(authorizedTenantIds.isAuthorizedForTenantId(tenantId1)).isTrue();
    assertThat(authorizedTenantIds.isAuthorizedForTenantId(tenantId2)).isTrue();
    assertThat(authorizedTenantIds.isAuthorizedForTenantIds(List.of(tenantId1, tenantId2)))
        .isTrue();
    assertThat(authorizedTenantIds.isAuthorizedForTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER))
        .isFalse();
  }

  @Test
  void shouldGetUserAuthorizedTenantIdsThroughGroup() {
    // given
    final var user = createUser();
    final var group = createGroupAndAssignEntity(user.getUsername(), EntityType.USER);
    final var tenantId1 = createAndAssignTenant(group.getGroupId(), EntityType.GROUP);
    final var tenantId2 = createAndAssignTenant(group.getGroupId(), EntityType.GROUP);
    final var command = mockCommand(user.getUsername());

    // when
    final var authorizedTenantIds = authorizationCheckBehavior.getAuthorizedTenantIds(command);

    // then
    assertThat(authorizedTenantIds.getAuthorizedTenantIds())
        .containsExactlyInAnyOrder(tenantId1, tenantId2);
    assertThat(authorizedTenantIds.isAuthorizedForTenantId(tenantId1)).isTrue();
    assertThat(authorizedTenantIds.isAuthorizedForTenantId(tenantId2)).isTrue();
    assertThat(authorizedTenantIds.isAuthorizedForTenantIds(List.of(tenantId1, tenantId2)))
        .isTrue();
    assertThat(authorizedTenantIds.isAuthorizedForTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER))
        .isFalse();
  }

  @Test
  void shouldGetMappingAuthorizedTenantIdsThroughGroup() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mappingRule = createMappingRule(claimName, claimValue);
    final var group =
        createGroupAndAssignEntity(mappingRule.getMappingRuleId(), EntityType.MAPPING_RULE);
    final var tenantId1 = createAndAssignTenant(group.getGroupId(), EntityType.GROUP);
    final var tenantId2 = createAndAssignTenant(group.getGroupId(), EntityType.GROUP);
    final var command = mockCommandWithMappingRule(claimName, claimValue);

    // when
    final var authorizedTenantIds = authorizationCheckBehavior.getAuthorizedTenantIds(command);

    // then
    assertThat(authorizedTenantIds.getAuthorizedTenantIds())
        .containsExactlyInAnyOrder(tenantId1, tenantId2);
    assertThat(authorizedTenantIds.isAuthorizedForTenantId(tenantId1)).isTrue();
    assertThat(authorizedTenantIds.isAuthorizedForTenantId(tenantId2)).isTrue();
    assertThat(authorizedTenantIds.isAuthorizedForTenantIds(List.of(tenantId1, tenantId2)))
        .isTrue();
    assertThat(authorizedTenantIds.isAuthorizedForTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER))
        .isFalse();
  }

  @Test
  void shouldGetEmptyTenantIdsListIfUserKeyIsNotPresent() {
    // given
    final var command = mock(TypedRecord.class);

    // when
    final var authorizedTenantIds =
        authorizationCheckBehavior.getAuthorizedTenantIds(command).getAuthorizedTenantIds();

    // then
    assertThat(authorizedTenantIds).isEmpty();
  }

  @Test
  void shouldGetEmptyTenantIdListIfUserIsNotPresent() {
    // given
    final var command = mockCommand("not-exists");

    // when
    final var authorizedTenantIds = authorizationCheckBehavior.getAuthorizedTenantIds(command);

    // then
    assertThat(authorizedTenantIds.getAuthorizedTenantIds()).isEmpty();
    assertThat(authorizedTenantIds.isAuthorizedForTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER))
        .isFalse();
    assertThat(
            authorizedTenantIds.isAuthorizedForTenantIds(
                List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER)))
        .isFalse();
    assertThat(authorizedTenantIds.isAuthorizedForTenantId("not-authorized")).isFalse();
  }

  @Test
  void shouldBeAuthorizedForInternalData() {
    // given
    final var user = createUser();
    final var resourceType = AuthorizationResourceType.GROUP;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(
        user.getUsername(), AuthorizationOwnerType.USER, resourceType, permissionType, resourceId);
    createAndAssignTenant(user.getUsername(), EntityType.USER);
    final var command = mockCommand(user.getUsername());

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType, null, false, false)
            .addResourceId(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    assertThat(authorized.isRight()).isTrue();
  }

  @Test
  void shouldBeUnauthorizedWhenMappingIsNotAssignedToRequestedTenant() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mappingRule = createMappingRule(claimName, claimValue);
    createAndAssignTenant(mappingRule.getMappingRuleId(), EntityType.MAPPING_RULE);
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(
        mappingRule.getMappingRuleId(),
        AuthorizationOwnerType.MAPPING_RULE,
        resourceType,
        permissionType,
        resourceId);
    final var command = mockCommandWithMappingRule(claimName, claimValue);

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType, "anotherTenantId")
            .addResourceId(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    EitherAssert.assertThat(authorized).isLeft();
  }

  @Test
  void shouldBeUnauthorizedForMappingTenant() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mappingRuleId = createMappingRule(claimName, claimValue).getMappingRuleId();
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(
        mappingRuleId,
        AuthorizationOwnerType.MAPPING_RULE,
        resourceType,
        permissionType,
        resourceId);
    final var anotherTenantId = "authorizedForAnotherTenant";
    createAndAssignTenant(mappingRuleId, EntityType.MAPPING_RULE);
    final var command = mockCommandWithMappingRule(claimName, claimValue);

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType, anotherTenantId)
            .addResourceId(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    assertThat(authorized.isRight()).isFalse();
  }

  @Test
  void shouldBeUnauthorizedForUserTenant() {
    // given
    final var user = createUser();
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(
        user.getUsername(), AuthorizationOwnerType.USER, resourceType, permissionType, resourceId);
    final var anotherTenantId = "authorizedForAnotherTenant";
    createAndAssignTenant(user.getUsername(), EntityType.USER);
    final var command = mockCommand(user.getUsername());

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType, anotherTenantId)
            .addResourceId(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    assertThat(authorized.isRight()).isFalse();
  }

  @Test
  void shouldGetAuthorizedTenantsForMapping() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mappingRule = createMappingRule(claimName, claimValue);

    // when
    final var tenantId =
        createAndAssignTenant(mappingRule.getMappingRuleId(), EntityType.MAPPING_RULE);
    final var command = mockCommandWithMappingRule(claimName, claimValue);

    // then
    assertThat(authorizationCheckBehavior.getAuthorizedTenantIds(command).getAuthorizedTenantIds())
        .singleElement()
        .isEqualTo(tenantId);
  }

  @Test
  void shouldGetEmptyListForTenantForMapping() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    createMappingRule(claimName, claimValue);
    final var command = mockCommandWithMappingRule(claimName, claimValue);

    // when
    // then
    assertThat(authorizationCheckBehavior.getAuthorizedTenantIds(command).getAuthorizedTenantIds())
        .isEmpty();
  }

  @Test
  void shouldReturnAnonymousAuthorizedTenants() {
    // given
    final var command = mockCommandWithAnonymousUser();

    // when
    final var authorizedTenants = authorizationCheckBehavior.getAuthorizedTenantIds(command);

    assertThat(authorizedTenants).isEqualTo(AuthorizedTenants.ANONYMOUS);
  }

  @Test
  void shouldBeAuthorizedWhenMappingIsAssignedToRequestedTenant() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mappingRule = createMappingRule(claimName, claimValue);
    final var tenantId =
        createAndAssignTenant(mappingRule.getMappingRuleId(), EntityType.MAPPING_RULE);
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(
        mappingRule.getMappingRuleId(),
        AuthorizationOwnerType.MAPPING_RULE,
        resourceType,
        permissionType,
        resourceId);
    final var command = mockCommandWithMappingRule(claimName, claimValue);

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType, tenantId)
            .addResourceId(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    EitherAssert.assertThat(authorized).isRight();
  }

  @Test
  void shouldBeAuthorizedWhenUserIsAssignedToRequestedTenantThroughRole() {
    // given
    final var user = createUser();
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(
        user.getUsername(), AuthorizationOwnerType.USER, resourceType, permissionType, resourceId);
    final var role = createRoleAndAssignEntity(user.getUsername(), EntityType.USER);
    final var tenantId = createAndAssignTenant(role.getRoleId(), EntityType.ROLE);
    final var command = mockCommand(user.getUsername());

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType, tenantId)
            .addResourceId(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    assertThat(authorized.isRight()).isTrue();
  }

  @Test
  void shouldBeAuthorizedWhenUserIsAssignedToRequestedTenantThroughGroupWithRole() {
    // given
    final var user = createUser();
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(
        user.getUsername(), AuthorizationOwnerType.USER, resourceType, permissionType, resourceId);
    final var group = createGroupAndAssignEntity(user.getUsername(), EntityType.USER);
    final var role = createRoleAndAssignEntity(group.getGroupId(), EntityType.GROUP);
    final var tenantId = createAndAssignTenant(role.getRoleId(), EntityType.ROLE);
    final var command = mockCommand(user.getUsername());

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType, tenantId)
            .addResourceId(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    assertThat(authorized.isRight()).isTrue();
  }

  @Test
  void shouldBeAuthorizedWhenMappingIsAssignedToRequestedTenantThroughRole() {
    // given
    final var mappingRule =
        createMappingRule(Strings.newRandomValidIdentityId(), Strings.newRandomValidIdentityId());
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(
        mappingRule.getMappingRuleId(),
        AuthorizationOwnerType.MAPPING_RULE,
        resourceType,
        permissionType,
        resourceId);
    final var role =
        createRoleAndAssignEntity(mappingRule.getMappingRuleId(), EntityType.MAPPING_RULE);
    final var tenantId = createAndAssignTenant(role.getRoleId(), EntityType.ROLE);
    final var command =
        mockCommandWithMappingRule(mappingRule.getClaimName(), mappingRule.getClaimValue());

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType, tenantId)
            .addResourceId(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    assertThat(authorized.isRight()).isTrue();
  }

  @Test
  void shouldBeAuthorizedWhenMappingIsAssignedToRequestedTenantThroughGroupWithRole() {
    // given
    final var mappingRule =
        createMappingRule(Strings.newRandomValidIdentityId(), Strings.newRandomValidIdentityId());
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(
        mappingRule.getMappingRuleId(),
        AuthorizationOwnerType.MAPPING_RULE,
        resourceType,
        permissionType,
        resourceId);
    final var group =
        createGroupAndAssignEntity(mappingRule.getMappingRuleId(), EntityType.MAPPING_RULE);
    final var role = createRoleAndAssignEntity(group.getGroupId(), EntityType.GROUP);
    final var tenantId = createAndAssignTenant(role.getRoleId(), EntityType.ROLE);
    final var command =
        mockCommandWithMappingRule(mappingRule.getClaimName(), mappingRule.getClaimValue());

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType, tenantId)
            .addResourceId(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    assertThat(authorized.isRight()).isTrue();
  }

  @Test
  void shouldGetTenantsWhenUserIsAssignedToRequestedTenantThroughRole() {
    // given
    final var user = createUser();
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(
        user.getUsername(), AuthorizationOwnerType.USER, resourceType, permissionType, resourceId);
    final var role = createRoleAndAssignEntity(user.getUsername(), EntityType.USER);
    final var tenantId = createAndAssignTenant(role.getRoleId(), EntityType.ROLE);
    final var command = mockCommand(user.getUsername());

    // when
    final var tenantIds = authorizationCheckBehavior.getAuthorizedTenantIds(command);

    // then
    assertThat(tenantIds.getAuthorizedTenantIds()).containsOnly(tenantId);
  }

  @Test
  void shouldGetTenantsWhenUserIsAssignedToRequestedTenantThroughGroupWithRole() {
    // given
    final var user = createUser();
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(
        user.getUsername(), AuthorizationOwnerType.USER, resourceType, permissionType, resourceId);
    final var group = createGroupAndAssignEntity(user.getUsername(), EntityType.USER);
    final var role = createRoleAndAssignEntity(group.getGroupId(), EntityType.GROUP);
    final var tenantId = createAndAssignTenant(role.getRoleId(), EntityType.ROLE);
    final var command = mockCommand(user.getUsername());

    // when
    final var tenantIds = authorizationCheckBehavior.getAuthorizedTenantIds(command);

    // then
    assertThat(tenantIds.getAuthorizedTenantIds()).containsOnly(tenantId);
  }

  @Test
  void shouldGetTenantsWhenMappingIsAssignedToRequestedTenantThroughRole() {
    // given
    final var mappingRule =
        createMappingRule(Strings.newRandomValidIdentityId(), Strings.newRandomValidIdentityId());
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(
        mappingRule.getMappingRuleId(),
        AuthorizationOwnerType.MAPPING_RULE,
        resourceType,
        permissionType,
        resourceId);
    final var role =
        createRoleAndAssignEntity(mappingRule.getMappingRuleId(), EntityType.MAPPING_RULE);
    final var tenantId = createAndAssignTenant(role.getRoleId(), EntityType.ROLE);
    final var command =
        mockCommandWithMappingRule(mappingRule.getClaimName(), mappingRule.getClaimValue());

    // when
    final var tenantIds = authorizationCheckBehavior.getAuthorizedTenantIds(command);

    // then
    assertThat(tenantIds.getAuthorizedTenantIds()).containsOnly(tenantId);
  }

  @Test
  void shouldGetTenantsWhenMappingIsAssignedToRequestedTenantThroughGroupWithRole() {
    // given
    final var mappingRule =
        createMappingRule(Strings.newRandomValidIdentityId(), Strings.newRandomValidIdentityId());
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(
        mappingRule.getMappingRuleId(),
        AuthorizationOwnerType.MAPPING_RULE,
        resourceType,
        permissionType,
        resourceId);
    final var group =
        createGroupAndAssignEntity(mappingRule.getMappingRuleId(), EntityType.MAPPING_RULE);
    final var role = createRoleAndAssignEntity(group.getGroupId(), EntityType.GROUP);
    final var tenantId = createAndAssignTenant(role.getRoleId(), EntityType.ROLE);
    final var command =
        mockCommandWithMappingRule(mappingRule.getClaimName(), mappingRule.getClaimValue());

    // when
    final var tenantIds = authorizationCheckBehavior.getAuthorizedTenantIds(command);

    // then
    assertThat(tenantIds.getAuthorizedTenantIds()).containsOnly(tenantId);
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
      final String... resourceIds) {
    for (final String resourceId : resourceIds) {
      final var authorizationKey = random.nextLong();
      final var resourceMatcher =
          "*".equals(resourceId)
              ? AuthorizationResourceMatcher.ANY
              : AuthorizationResourceMatcher.ID;
      final var authorization =
          new AuthorizationRecord()
              .setAuthorizationKey(authorizationKey)
              .setOwnerId(ownerId)
              .setOwnerType(ownerType)
              .setResourceMatcher(resourceMatcher)
              .setResourceId(resourceId)
              .setResourceType(resourceType)
              .setPermissionTypes(Set.of(permissionType));
      authorizationCreatedApplier.applyState(authorizationKey, authorization);
    }
  }

  private GroupRecord createGroupAndAssignEntity(
      final String entityId, final EntityType entityType) {
    final var groupKey = random.nextLong();
    final var groupId = Strings.newRandomValidIdentityId();
    final var group =
        new GroupRecord()
            .setGroupKey(groupKey)
            .setGroupId(groupId)
            .setName(UUID.randomUUID().toString())
            .setDescription(UUID.randomUUID().toString())
            .setEntityId(entityId)
            .setEntityType(entityType);
    groupCreatedApplier.applyState(groupKey, group);
    groupEntityAddedApplier.applyState(groupKey, group);
    return group;
  }

  private RoleRecord createRoleAndAssignEntity(final String entityId, final EntityType entityType) {
    final var roleKey = random.nextLong();
    final var roleId = Strings.newRandomValidIdentityId();
    final var role =
        new RoleRecord()
            .setRoleKey(roleKey)
            .setRoleId(roleId)
            .setName(UUID.randomUUID().toString())
            .setDescription(UUID.randomUUID().toString())
            .setEntityId(entityId)
            .setEntityType(entityType);
    roleCreatedApplier.applyState(roleKey, role);
    roleEntityAddedApplier.applyState(roleKey, role);
    return role;
  }

  private TenantRecord createTenant() {
    final var tenantKey = random.nextLong();
    final var tenant =
        new TenantRecord()
            .setTenantKey(tenantKey)
            .setTenantId(Strings.newRandomValidIdentityId())
            .setName(UUID.randomUUID().toString())
            .setDescription(UUID.randomUUID().toString());
    tenantCreatedApplier.applyState(tenantKey, tenant);
    return tenant;
  }

  private String createAndAssignTenant(final String entityId, final EntityType entityType) {
    final var tenant = createTenant();
    tenant.setEntityId(entityId).setEntityType(entityType);
    tenantEntityAddedApplier.applyState(tenant.getTenantKey(), tenant);
    return tenant.getTenantId();
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
}
