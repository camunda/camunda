/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.authorization;

import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_USERNAME;
import static io.camunda.zeebe.auth.Authorization.USER_GROUPS_CLAIMS;
import static io.camunda.zeebe.auth.Authorization.USER_TOKEN_CLAIMS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.security.configuration.AuthorizationsConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.request.AuthorizationRequest;
import io.camunda.zeebe.engine.state.appliers.AuthorizationCreatedApplier;
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
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
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
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class AuthorizationCheckBehaviorGroupsClaimsTest {

  @SuppressWarnings("unused") // injected by the extension
  private MutableProcessingState processingState;

  private AuthorizationCheckBehavior authorizationCheckBehavior;
  private UserCreatedApplier userCreatedApplier;
  private MappingRuleCreatedApplier mappingRuleCreatedApplier;
  private AuthorizationCreatedApplier authorizationCreatedApplier;
  private RoleCreatedApplier roleCreatedApplier;
  private RoleEntityAddedApplier roleEntityAddedApplier;
  private TenantCreatedApplier tenantCreatedApplier;
  private TenantEntityAddedApplier tenantEntityAddedApplier;
  private final Random random = new Random();

  @BeforeEach
  public void before() {
    final var securityConfig = new SecurityConfiguration();
    final var authConfig = new AuthorizationsConfiguration();
    final var engineConfig = new EngineConfiguration();
    authConfig.setEnabled(true);
    securityConfig.setAuthorizations(authConfig);
    authorizationCheckBehavior =
        new AuthorizationCheckBehavior(processingState, securityConfig, engineConfig);

    userCreatedApplier = new UserCreatedApplier(processingState.getUserState());
    mappingRuleCreatedApplier =
        new MappingRuleCreatedApplier(processingState.getMappingRuleState());
    authorizationCreatedApplier =
        new AuthorizationCreatedApplier(processingState.getAuthorizationState());
    roleCreatedApplier = new RoleCreatedApplier(processingState.getRoleState());
    roleEntityAddedApplier = new RoleEntityAddedApplier(processingState);
    tenantCreatedApplier = new TenantCreatedApplier(processingState.getTenantState());
    tenantEntityAddedApplier = new TenantEntityAddedApplier(processingState);
  }

  @Test
  public void shouldBeAuthorizedWhenGroupHasPermissions() {
    // given
    final var user = createUser();
    final var groups = List.of("group1", "group2");
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceIdScope = AuthorizationScope.id(UUID.randomUUID().toString());
    addPermission(
        groups.getFirst(),
        AuthorizationOwnerType.GROUP,
        resourceType,
        permissionType,
        resourceIdScope);
    final var command = mockCommand(user.getUsername(), groups);

    // when
    final var request =
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(resourceType)
            .permissionType(permissionType)
            .addResourceId(resourceIdScope.getResourceId())
            .build();
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    assertThat(authorized.isRight()).isTrue();
  }

  @Test
  void shouldGetResourceIdentifiersWhenGroupHasPermissions() {
    // given
    final var user = createUser();
    final var groups = List.of("group1", "group2");
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId1 = AuthorizationScope.of(UUID.randomUUID().toString());
    final var resourceId2 = AuthorizationScope.of(UUID.randomUUID().toString());
    addPermission(
        groups.getFirst(),
        AuthorizationOwnerType.GROUP,
        resourceType,
        permissionType,
        resourceId1,
        resourceId2);
    final var command = mockCommand(user.getUsername(), groups);

    // when
    final var request =
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(resourceType)
            .permissionType(permissionType)
            .build();
    final var resourceIdentifiers = authorizationCheckBehavior.getAllAuthorizedScopes(request);

    // then
    assertThat(resourceIdentifiers).containsExactlyInAnyOrder(resourceId1, resourceId2);
  }

  @Test
  void shouldGetAuthorizationsForMappingRuleThroughAssignedGroup() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    createMappingRule(claimName, claimValue);
    final var groups = List.of("group1", "group2");
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceIdScope = AuthorizationScope.id(UUID.randomUUID().toString());
    addPermission(
        groups.getFirst(),
        AuthorizationOwnerType.GROUP,
        resourceType,
        permissionType,
        resourceIdScope);
    final var command = mockCommandWithMappingRule(claimName, claimValue, groups);

    // when
    final var request =
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(resourceType)
            .permissionType(permissionType)
            .addResourceId(resourceIdScope.getResourceId())
            .build();
    final var authorizations = authorizationCheckBehavior.getAllAuthorizedScopes(request);

    // then
    assertThat(authorizations).containsExactlyInAnyOrder(resourceIdScope);
  }

  @Test
  void shouldBeAuthorizedForUserWithAssignedGroupWithAssignedRole() {
    // given
    final var user = createUser();
    final var groups = List.of("group1", "group2");
    final var role = createRoleAndAssignEntity(groups.get(0), EntityType.GROUP);

    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceIdScope = AuthorizationScope.id(UUID.randomUUID().toString());
    addPermission(
        role.getRoleId(),
        AuthorizationOwnerType.ROLE,
        resourceType,
        permissionType,
        resourceIdScope);
    final var command = mockCommand(user.getUsername(), groups);

    // when
    final var request =
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(resourceType)
            .permissionType(permissionType)
            .addResourceId(resourceIdScope.getResourceId())
            .build();
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
    final var resourceIdScope = AuthorizationScope.id(UUID.randomUUID().toString());
    addPermission(
        user.getUsername(),
        AuthorizationOwnerType.USER,
        resourceType,
        permissionType,
        resourceIdScope);
    final var groups = List.of("group1", "group2");
    final var tenantId = createAndAssignTenant(groups.getFirst(), EntityType.GROUP);
    final var command = mockCommand(user.getUsername(), groups);

    // when
    final var request =
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(resourceType)
            .permissionType(permissionType)
            .tenantId(tenantId)
            .addResourceId(resourceIdScope.getResourceId())
            .build();
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    assertThat(authorized.isRight()).isTrue();
  }

  @Test
  void shouldBeAuthorizedByPropertyAuthorizationWhenGroupClaimMatchesCandidateGroups() {
    // given
    final var user = createUser();
    final var groups = List.of("group-a", "group-b", "group-c");
    final var resourceType = AuthorizationResourceType.USER_TASK;
    final var permissionType = PermissionType.UPDATE;
    final var propertyScope = AuthorizationScope.property("candidateGroups");
    addPermission(
        user.getUsername(),
        AuthorizationOwnerType.USER,
        resourceType,
        permissionType,
        propertyScope);
    final var command = mockCommand(user.getUsername(), groups);

    // when: user's second group matches one of the candidate groups
    final var request =
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(resourceType)
            .permissionType(permissionType)
            .addResourceProperty("candidateGroups", List.of("other-group", "group-b"))
            .build();

    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    assertThat(authorized.isRight()).isTrue();
  }

  @Test
  void shouldNotBeAuthorizedByPropertyAuthorizationWhenGroupClaimNotInCandidateGroups() {
    // given
    final var user = createUser();
    final var groups = List.of("engineering", "platform-team");
    final var resourceType = AuthorizationResourceType.USER_TASK;
    final var permissionType = PermissionType.UPDATE;
    final var propertyScope = AuthorizationScope.property("candidateGroups");
    addPermission(
        user.getUsername(),
        AuthorizationOwnerType.USER,
        resourceType,
        permissionType,
        propertyScope);
    final var command = mockCommand(user.getUsername(), groups);

    // when: none of the user's groups match candidate groups
    final var request =
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(resourceType)
            .permissionType(permissionType)
            .addResourceProperty("candidateGroups", List.of("sales", "marketing"))
            .build();

    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    assertThat(authorized.isRight()).isFalse();
  }

  @Test
  void shouldBeAuthorizedByPropertyAuthorizationWhenMappingRuleGroupClaimInCandidateGroups() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mappingRule = createMappingRule(claimName, claimValue);
    final var groups = List.of("mapping-group-1", "mapping-group-2");
    final var resourceType = AuthorizationResourceType.USER_TASK;
    final var permissionType = PermissionType.UPDATE;
    final var propertyScope = AuthorizationScope.property("candidateGroups");
    addPermission(
        mappingRule.getMappingRuleId(),
        AuthorizationOwnerType.MAPPING_RULE,
        resourceType,
        permissionType,
        propertyScope);
    final var command = mockCommandWithMappingRule(claimName, claimValue, groups);

    // when
    final var request =
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(resourceType)
            .permissionType(permissionType)
            .addResourceProperty("candidateGroups", List.of("mapping-group-1", "other-group"))
            .build();

    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    assertThat(authorized.isRight()).isTrue();
  }

  @Test
  void shouldBeAuthorizedByPropertyAuthorizationForUserWithAssignedGroupWithAssignedRole() {
    // given
    final var user = createUser();
    final var groups = List.of("group1", "group2");
    final var role = createRoleAndAssignEntity(groups.getFirst(), EntityType.GROUP);

    final var resourceType = AuthorizationResourceType.USER_TASK;
    final var permissionType = PermissionType.UPDATE;
    final var propertyScope = AuthorizationScope.property("candidateGroups");
    addPermission(
        role.getRoleId(), AuthorizationOwnerType.ROLE, resourceType, permissionType, propertyScope);
    final var command = mockCommand(user.getUsername(), groups);

    // when
    final var request =
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(resourceType)
            .permissionType(permissionType)
            .addResourceProperty("candidateGroups", List.of("group1", "otherGroup"))
            .build();
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    assertThat(authorized.isRight()).isTrue();
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
              .setResourcePropertyName(authorizationScope.getResourcePropertyName())
              .setResourceType(resourceType)
              .setPermissionTypes(Set.of(permissionType));
      authorizationCreatedApplier.applyState(authorizationKey, authorization);
    }
  }

  private TypedRecord<?> mockCommand(final String username, final List<String> groups) {
    final var command = mock(TypedRecord.class);
    when(command.getAuthorizations())
        .thenReturn(Map.of(AUTHORIZED_USERNAME, username, USER_GROUPS_CLAIMS, groups));
    when(command.hasRequestMetadata()).thenReturn(true);
    return command;
  }

  private TypedRecord<?> mockCommandWithMappingRule(
      final String claimName, final String claimValue, final List<String> groups) {
    final var command = mock(TypedRecord.class);
    when(command.getAuthorizations())
        .thenReturn(
            Map.of(USER_TOKEN_CLAIMS, Map.of(claimName, claimValue), USER_GROUPS_CLAIMS, groups));
    when(command.hasRequestMetadata()).thenReturn(true);
    return command;
  }
}
