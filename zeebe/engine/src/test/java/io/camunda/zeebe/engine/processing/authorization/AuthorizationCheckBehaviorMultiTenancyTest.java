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
import static io.camunda.zeebe.auth.Authorization.USER_TOKEN_CLAIM_PREFIX;
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
import io.camunda.zeebe.engine.state.appliers.MappingCreatedApplier;
import io.camunda.zeebe.engine.state.appliers.TenantCreatedApplier;
import io.camunda.zeebe.engine.state.appliers.TenantEntityAddedApplier;
import io.camunda.zeebe.engine.state.appliers.UserCreatedApplier;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.MappingRecordValue;
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
  private MappingCreatedApplier mappingCreatedApplier;
  private AuthorizationCreatedApplier authorizationCreatedApplier;
  private GroupCreatedApplier groupCreatedApplier;
  private GroupEntityAddedApplier groupEntityAddedApplier;
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
    multiTenancyConfig.setEnabled(true);
    securityConfig.setMultiTenancy(multiTenancyConfig);
    authorizationCheckBehavior = new AuthorizationCheckBehavior(processingState, securityConfig);

    userCreatedApplier = new UserCreatedApplier(processingState.getUserState());
    mappingCreatedApplier = new MappingCreatedApplier(processingState.getMappingState());
    authorizationCreatedApplier =
        new AuthorizationCreatedApplier(processingState.getAuthorizationState());
    groupCreatedApplier = new GroupCreatedApplier(processingState.getGroupState());
    groupEntityAddedApplier = new GroupEntityAddedApplier(processingState);
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
    final var groupKey = createGroup(user.getUserKey(), EntityType.USER);
    final var tenantId = createAndAssignTenant(groupKey, EntityType.GROUP);
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
    final var mappingId = createMapping(claimName, claimValue).getMappingId();
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(
        mappingId, AuthorizationOwnerType.MAPPING, resourceType, permissionType, resourceId);
    final var tenantId = createAndAssignTenant(mappingId, EntityType.MAPPING);
    final var command = mockCommandWithMapping(claimName, claimValue);

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
    final var mapping = createMapping(claimName, claimValue);
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(
        mapping.getMappingId(),
        AuthorizationOwnerType.MAPPING,
        resourceType,
        permissionType,
        resourceId);
    final var groupKey = createGroup(mapping.getMappingKey(), EntityType.MAPPING);
    final var tenantId = createAndAssignTenant(groupKey, EntityType.GROUP);
    final var command = mockCommandWithMapping(claimName, claimValue);

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
    final var mappingId = createMapping(claimName, claimValue).getMappingId();
    final var tenantId1 = createAndAssignTenant(mappingId, EntityType.MAPPING);
    final var tenantId2 = createAndAssignTenant(mappingId, EntityType.MAPPING);
    final var command = mockCommandWithMapping(claimName, claimValue);

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
    final var groupKey = createGroup(user.getUserKey(), EntityType.USER);
    final var tenantId1 = createAndAssignTenant(groupKey, EntityType.GROUP);
    final var tenantId2 = createAndAssignTenant(groupKey, EntityType.GROUP);
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
    final var mapping = createMapping(claimName, claimValue);
    final var groupKey = createGroup(mapping.getMappingKey(), EntityType.MAPPING);
    final var tenantId1 = createAndAssignTenant(groupKey, EntityType.GROUP);
    final var tenantId2 = createAndAssignTenant(groupKey, EntityType.GROUP);
    final var command = mockCommandWithMapping(claimName, claimValue);

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
  void shouldGetDefaultAuthorizedTenantIdsIfUserKeyIsNotPresent() {
    // given
    final var command = mock(TypedRecord.class);

    // when
    final var authorizedTenantIds =
        authorizationCheckBehavior.getAuthorizedTenantIds(command).getAuthorizedTenantIds();

    // then
    assertThat(authorizedTenantIds).containsOnly(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  void shouldGetDefaultAuthorizedTenantIdsIfUserIsNotPresent() {
    // given
    final var command = mockCommand("not-exists");

    // when
    final var authorizedTenantIds = authorizationCheckBehavior.getAuthorizedTenantIds(command);

    // then
    assertThat(authorizedTenantIds.getAuthorizedTenantIds())
        .containsOnly(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(authorizedTenantIds.isAuthorizedForTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER))
        .isTrue();
    assertThat(
            authorizedTenantIds.isAuthorizedForTenantIds(
                List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER)))
        .isTrue();
    assertThat(authorizedTenantIds.isAuthorizedForTenantId("not-authorized")).isFalse();
  }

  @Test
  void shouldBeAuthorizedWhenMappingIsNotAssignedToRequestedTenant() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mapping = createMapping(claimName, claimValue);
    createAndAssignTenant(mapping.getMappingId(), EntityType.MAPPING);
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(
        mapping.getMappingId(),
        AuthorizationOwnerType.MAPPING,
        resourceType,
        permissionType,
        resourceId);
    final var command = mockCommandWithMapping(claimName, claimValue);

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
    final var mappingId = createMapping(claimName, claimValue).getMappingId();
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(
        mappingId, AuthorizationOwnerType.MAPPING, resourceType, permissionType, resourceId);
    final var anotherTenantId = "authorizedForAnotherTenant";
    createAndAssignTenant(mappingId, EntityType.MAPPING);
    final var command = mockCommandWithMapping(claimName, claimValue);

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
    final var mapping = createMapping(claimName, claimValue);

    // when
    final var tenantId = createAndAssignTenant(mapping.getMappingId(), EntityType.MAPPING);
    final var command = mockCommandWithMapping(claimName, claimValue);

    // then
    assertThat(authorizationCheckBehavior.getAuthorizedTenantIds(command).getAuthorizedTenantIds())
        .singleElement()
        .isEqualTo(tenantId);
  }

  @Test
  void shouldGetDefaultAuthorizedTenantForMapping() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    createMapping(claimName, claimValue);
    final var command = mockCommandWithMapping(claimName, claimValue);

    // when
    // then
    assertThat(authorizationCheckBehavior.getAuthorizedTenantIds(command).getAuthorizedTenantIds())
        .singleElement()
        .isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
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
    final var mapping = createMapping(claimName, claimValue);
    final var tenantId = createAndAssignTenant(mapping.getMappingId(), EntityType.MAPPING);
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(
        mapping.getMappingId(),
        AuthorizationOwnerType.MAPPING,
        resourceType,
        permissionType,
        resourceId);
    final var command = mockCommandWithMapping(claimName, claimValue);

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType, tenantId)
            .addResourceId(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    EitherAssert.assertThat(authorized).isRight();
  }

  private TypedRecord<?> mockCommandWithMapping(final String claimName, final String claimValue) {
    final var command = mock(TypedRecord.class);
    when(command.getAuthorizations())
        .thenReturn(Map.of(USER_TOKEN_CLAIM_PREFIX + claimName, claimValue));
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

  private MappingRecordValue createMapping(final String claimName, final String claimValue) {
    final var mappingKey = random.nextLong();
    final var mapping =
        new MappingRecord()
            .setMappingKey(mappingKey)
            .setMappingId(Strings.newRandomValidIdentityId())
            .setName(Strings.newRandomValidUsername())
            .setClaimName(claimName)
            .setClaimValue(claimValue);
    mappingCreatedApplier.applyState(mappingKey, mapping);
    return mapping;
  }

  private void addPermission(
      final String ownerId,
      final AuthorizationOwnerType ownerType,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType,
      final String... resourceIds) {
    for (final String resourceId : resourceIds) {
      final var authorizationKey = random.nextLong();
      final var authorization =
          new AuthorizationRecord()
              .setAuthorizationKey(authorizationKey)
              .setOwnerId(ownerId)
              .setOwnerType(ownerType)
              .setResourceId(resourceId)
              .setResourceType(resourceType)
              .setPermissionTypes(Set.of(permissionType));
      authorizationCreatedApplier.applyState(authorizationKey, authorization);
    }
  }

  private long createGroup(final long entityKey, final EntityType entityType) {
    final var groupKey = random.nextLong();
    final var groupId = String.valueOf(groupKey);
    final var group =
        new GroupRecord()
            .setGroupKey(groupKey)
            .setGroupId(groupId)
            .setName(UUID.randomUUID().toString())
            .setDescription(UUID.randomUUID().toString())
            .setEntityKey(entityKey)
            .setEntityType(entityType);
    groupCreatedApplier.applyState(groupKey, group);
    groupEntityAddedApplier.applyState(groupKey, group);
    return groupKey;
  }

  // TODO remove this method once Mappings and Groups are migrated to work with ids instead of
  private String createAndAssignTenant(final Long entityKey, final EntityType entityType) {
    return createAndAssignTenant(String.valueOf(entityKey), entityType);
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
