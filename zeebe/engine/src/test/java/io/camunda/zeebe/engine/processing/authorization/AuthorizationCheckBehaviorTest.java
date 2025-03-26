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
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.state.appliers.AuthorizationCreatedApplier;
import io.camunda.zeebe.engine.state.appliers.GroupCreatedApplier;
import io.camunda.zeebe.engine.state.appliers.GroupEntityAddedApplier;
import io.camunda.zeebe.engine.state.appliers.MappingCreatedApplier;
import io.camunda.zeebe.engine.state.appliers.RoleCreatedApplier;
import io.camunda.zeebe.engine.state.appliers.RoleEntityAddedApplier;
import io.camunda.zeebe.engine.state.appliers.TenantCreatedApplier;
import io.camunda.zeebe.engine.state.appliers.TenantEntityAddedApplier;
import io.camunda.zeebe.engine.state.appliers.UserCreatedApplier;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.MappingRecordValue;
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
  private MappingCreatedApplier mappingCreatedApplier;
  private AuthorizationCreatedApplier authorizationCreatedApplier;
  private GroupCreatedApplier groupCreatedApplier;
  private GroupEntityAddedApplier groupEntityAddedApplier;
  private TenantCreatedApplier tenantCreatedApplier;
  private TenantEntityAddedApplier tenantEntityAddedApplier;
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
    mappingCreatedApplier = new MappingCreatedApplier(processingState.getMappingState());
    authorizationCreatedApplier =
        new AuthorizationCreatedApplier(processingState.getAuthorizationState());
    groupCreatedApplier = new GroupCreatedApplier(processingState.getGroupState());
    groupEntityAddedApplier = new GroupEntityAddedApplier(processingState);
    tenantCreatedApplier = new TenantCreatedApplier(processingState.getTenantState());
    tenantEntityAddedApplier = new TenantEntityAddedApplier(processingState);
    roleCreatedApplier = new RoleCreatedApplier(processingState.getRoleState());
    roleEntityAddedApplier = new RoleEntityAddedApplier(processingState);
  }

  @Test
  void shouldBeAuthorizedWhenUserHasPermission() {
    // given
    final var user = createUser();
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(
        user.getUsername(), AuthorizationOwnerType.USER, resourceType, permissionType, resourceId);
    final var command = mockCommand(user.getUsername());

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType).addResourceId(resourceId);
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
    final var resourceId1 = UUID.randomUUID().toString();
    final var resourceId2 = UUID.randomUUID().toString();
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
    final var resourceIdentifiers =
        authorizationCheckBehavior.getAllAuthorizedResourceIdentifiers(request);

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
    final var resourceIdentifiers =
        authorizationCheckBehavior.getAllAuthorizedResourceIdentifiers(request);

    // then
    assertThat(resourceIdentifiers).isEmpty();
  }

  @Test
  void shouldBeAuthorizedWhenRoleHasPermissions() {
    // given
    final var user = createUser();
    final var roleKey = createRole(user.getUserKey(), EntityType.USER);
    final var roleId = String.valueOf(roleKey);
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(roleId, AuthorizationOwnerType.ROLE, resourceType, permissionType, resourceId);
    final var command = mockCommand(user.getUsername());

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType).addResourceId(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    assertThat(authorized.isRight()).isTrue();
  }

  @Test
  void shouldGetResourceIdentifiersWhenRoleHasPermissions() {
    // given
    final var user = createUser();
    final var roleKey = createRole(user.getUserKey(), EntityType.USER);
    final var roleId = String.valueOf(roleKey);
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId1 = UUID.randomUUID().toString();
    final var resourceId2 = UUID.randomUUID().toString();
    addPermission(
        roleId,
        AuthorizationOwnerType.ROLE,
        resourceType,
        permissionType,
        resourceId1,
        resourceId2);
    final var command = mockCommand(user.getUsername());

    // when
    final var request = new AuthorizationRequest(command, resourceType, permissionType);
    final var resourceIdentifiers =
        authorizationCheckBehavior.getAllAuthorizedResourceIdentifiers(request);

    // then
    assertThat(resourceIdentifiers).containsExactlyInAnyOrder(resourceId1, resourceId2);
  }

  @Test
  public void shouldBeAuthorizedWhenGroupHasPermissions() {
    // given
    final var user = createUser();
    final var groupKey = createGroup(user.getUserKey(), EntityType.USER);
    final var groupId = String.valueOf(groupKey);
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(groupId, AuthorizationOwnerType.GROUP, resourceType, permissionType, resourceId);
    final var command = mockCommand(user.getUsername());

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType).addResourceId(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    assertThat(authorized.isRight()).isTrue();
  }

  @Test
  void shouldGetResourceIdentifiersWhenGroupHasPermissions() {
    // given
    final var user = createUser();
    final var groupKey = createGroup(user.getUserKey(), EntityType.USER);
    final var groupId = String.valueOf(groupKey);
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId1 = UUID.randomUUID().toString();
    final var resourceId2 = UUID.randomUUID().toString();
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
    final var resourceIdentifiers =
        authorizationCheckBehavior.getAllAuthorizedResourceIdentifiers(request);

    // then
    assertThat(resourceIdentifiers).containsExactlyInAnyOrder(resourceId1, resourceId2);
  }

  @Test
  void shouldBeAuthorizedWhenAnonymousAuthenticationProvided() {
    // given
    final var user = createUser();
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(
        user.getUsername(), AuthorizationOwnerType.USER, resourceType, permissionType, resourceId);
    final var command = mockCommandWithAnonymousUser();

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType).addResourceId(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    assertThat(authorized.isRight()).isTrue();
  }

  @Test
  void shouldBeAuthorizedWhenMappingHasPermission() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mapping = createMapping(claimName, claimValue);
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(
        mapping.getId(), AuthorizationOwnerType.MAPPING, resourceType, permissionType, resourceId);
    final var command = mockCommandWithMapping(claimName, claimValue);

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType).addResourceId(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    EitherAssert.assertThat(authorized).isRight();
  }

  @Test
  void shouldBeAuthorizedWhenMappingIsAuthorizedThroughGroup() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mappingKey = createMapping(claimName, claimValue).getMappingKey();
    final var groupKey = createGroup(mappingKey, EntityType.MAPPING);
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(
        String.valueOf(groupKey),
        AuthorizationOwnerType.GROUP,
        resourceType,
        permissionType,
        resourceId);
    final var command = mockCommandWithMapping(claimName, claimValue);

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType).addResourceId(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    EitherAssert.assertThat(authorized).isRight();
  }

  @Test
  void shouldBeAuthorizedWhenMappingIsAuthorizedThroughRole() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mappingKey = createMapping(claimName, claimValue).getMappingKey();
    final var roleKey = createRole(mappingKey, EntityType.MAPPING);
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(
        String.valueOf(roleKey),
        AuthorizationOwnerType.ROLE,
        resourceType,
        permissionType,
        resourceId);

    final var command = mockCommandWithMapping(claimName, claimValue);

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType).addResourceId(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    EitherAssert.assertThat(authorized).isRight();
  }

  @Test
  void shouldNotBeAuthorizedWhenMappingHasNoPermission() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    createMapping(claimName, claimValue);
    final var command = mockCommandWithMapping(claimName, claimValue);

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
    final var firstMapping = createMapping(firstClaimName, firstClaimValue);
    final var secondClaimName = UUID.randomUUID().toString();
    final var secondClaimValue = UUID.randomUUID().toString();
    final var secondMapping = createMapping(secondClaimName, secondClaimValue);

    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var firstResourceId = UUID.randomUUID().toString();
    final var secondResourceId = UUID.randomUUID().toString();
    addPermission(
        String.valueOf(firstMapping.getId()),
        AuthorizationOwnerType.MAPPING,
        resourceType,
        permissionType,
        firstResourceId);
    addPermission(
        String.valueOf(secondMapping.getId()),
        AuthorizationOwnerType.MAPPING,
        resourceType,
        permissionType,
        secondResourceId);

    // when
    final var command = mock(TypedRecord.class);
    when(command.getAuthorizations())
        .thenReturn(
            Map.of(
                USER_TOKEN_CLAIM_PREFIX + firstClaimName,
                firstClaimValue,
                USER_TOKEN_CLAIM_PREFIX + secondClaimName,
                secondClaimValue));
    when(command.hasRequestMetadata()).thenReturn(true);

    // then
    EitherAssert.assertThat(
            authorizationCheckBehavior.isAuthorized(
                new AuthorizationRequest(command, resourceType, permissionType)
                    .addResourceId(firstResourceId)))
        .isRight();
    EitherAssert.assertThat(
            authorizationCheckBehavior.isAuthorized(
                new AuthorizationRequest(command, resourceType, permissionType)
                    .addResourceId(secondResourceId)))
        .isRight();
  }

  @Test
  void shouldBeAuthorizedThroughMappingWithMultipleClaimValues() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var firstClaimValue = UUID.randomUUID().toString();
    final var firstMapping = createMapping(claimName, firstClaimValue);
    final var secondClaimValue = UUID.randomUUID().toString();
    final var secondMapping = createMapping(claimName, secondClaimValue);

    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var firstResourceId = UUID.randomUUID().toString();
    final var secondResourceId = UUID.randomUUID().toString();
    addPermission(
        firstMapping.getId(),
        AuthorizationOwnerType.MAPPING,
        resourceType,
        permissionType,
        firstResourceId);
    addPermission(
        secondMapping.getId(),
        AuthorizationOwnerType.MAPPING,
        resourceType,
        permissionType,
        secondResourceId);

    // when
    final var command = mock(TypedRecord.class);
    when(command.getAuthorizations())
        .thenReturn(
            Map.of(
                USER_TOKEN_CLAIM_PREFIX + claimName, List.of(firstClaimValue, secondClaimValue)));
    when(command.hasRequestMetadata()).thenReturn(true);

    // then
    EitherAssert.assertThat(
            authorizationCheckBehavior.isAuthorized(
                new AuthorizationRequest(command, resourceType, permissionType)
                    .addResourceId(firstResourceId)))
        .isRight();
    EitherAssert.assertThat(
            authorizationCheckBehavior.isAuthorized(
                new AuthorizationRequest(command, resourceType, permissionType)
                    .addResourceId(secondResourceId)))
        .isRight();
  }

  @Test
  void shouldGetAuthorizationsForMapping() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mapping = createMapping(claimName, claimValue);
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(
        mapping.getId(), AuthorizationOwnerType.MAPPING, resourceType, permissionType, resourceId);
    final var command = mockCommandWithMapping(claimName, claimValue);

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType).addResourceId(resourceId);
    final var authorizations =
        authorizationCheckBehavior.getAllAuthorizedResourceIdentifiers(request);

    // then
    assertThat(authorizations).containsExactlyInAnyOrder(resourceId);
  }

  @Test
  void shouldGetAuthorizationsForMappingThroughAssignedRole() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mapping = createMapping(claimName, claimValue);
    final var mappingKey = mapping.getMappingKey();
    final var roleKey = createRole(mappingKey, EntityType.MAPPING);
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(
        String.valueOf(roleKey),
        AuthorizationOwnerType.ROLE,
        resourceType,
        permissionType,
        resourceId);
    final var command = mockCommandWithMapping(claimName, claimValue);

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType).addResourceId(resourceId);
    final var authorizations =
        authorizationCheckBehavior.getAllAuthorizedResourceIdentifiers(request);

    // then
    assertThat(authorizations).containsExactlyInAnyOrder(resourceId);
  }

  @Test
  void shouldGetAuthorizationsForMappingThroughAssignedGroup() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mapping = createMapping(claimName, claimValue);
    final var mappingKey = mapping.getMappingKey();
    final var groupKey = createGroup(mappingKey, EntityType.MAPPING);
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(
        String.valueOf(groupKey),
        AuthorizationOwnerType.GROUP,
        resourceType,
        permissionType,
        resourceId);
    final var command = mockCommandWithMapping(claimName, claimValue);

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType).addResourceId(resourceId);
    final var authorizations =
        authorizationCheckBehavior.getAllAuthorizedResourceIdentifiers(request);

    // then
    assertThat(authorizations).containsExactlyInAnyOrder(resourceId);
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

  private long createRole(final long entityKey, final EntityType entityType) {
    final var roleKey = random.nextLong();
    final var role =
        new RoleRecord()
            .setRoleKey(roleKey)
            .setRoleId(Strings.newRandomValidIdentityId())
            .setName(UUID.randomUUID().toString())
            .setDescription(UUID.randomUUID().toString())
            .setEntityKey(entityKey)
            .setEntityType(entityType);
    roleCreatedApplier.applyState(roleKey, role);
    roleEntityAddedApplier.applyState(roleKey, role);
    return roleKey;
  }

  private long createGroup(final long entityKey, final EntityType entityType) {
    final var groupKey = random.nextLong();
    final var group =
        new GroupRecord()
            .setGroupKey(groupKey)
            .setGroupId(Strings.newRandomValidIdentityId())
            .setName(UUID.randomUUID().toString())
            .setDescription(UUID.randomUUID().toString())
            .setEntityKey(entityKey)
            .setEntityType(entityType);
    groupCreatedApplier.applyState(groupKey, group);
    groupEntityAddedApplier.applyState(groupKey, group);
    return groupKey;
  }

  private MappingRecordValue createMapping(final String claimName, final String claimValue) {
    final var mappingKey = random.nextLong();
    final var mapping =
        new MappingRecord()
            .setMappingKey(mappingKey)
            .setId(Strings.newRandomValidIdentityId())
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
