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
import io.camunda.zeebe.engine.util.EngineRule;
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
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class AuthorizationCheckBehaviorMultiTenancyTest {
  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();
  private AuthorizationCheckBehavior authorizationCheckBehavior;

  @Before
  public void before() {
    final var processingState = engine.getProcessingState();
    final var securityConfig = new SecurityConfiguration();
    final var authConfig = new AuthorizationsConfiguration();
    authConfig.setEnabled(true);
    securityConfig.setAuthorizations(authConfig);
    final var multiTenancyConfig = new MultiTenancyConfiguration();
    multiTenancyConfig.setEnabled(true);
    securityConfig.setMultiTenancy(multiTenancyConfig);
    authorizationCheckBehavior = new AuthorizationCheckBehavior(processingState, securityConfig);
  }

  @Test
  public void shouldBeAuthorizedForUserTenant() {
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
  public void shouldBeAuthorizedForUserTenantThroughGroup() {
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
  public void shouldBeAuthorizedForMappingTenant() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mappingId = createMapping(claimName, claimValue).getId();
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
  public void shouldBeAuthorizedForMappingTenantThroughGroup() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mapping = createMapping(claimName, claimValue);
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(
        mapping.getId(), AuthorizationOwnerType.MAPPING, resourceType, permissionType, resourceId);
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
  public void shouldGetUserAuthorizedTenantIds() {
    // given
    final var user = createUser();
    final var tenantId1 = createAndAssignTenant(user.getUsername(), EntityType.USER);
    final var tenantId2 = createAndAssignTenant(user.getUsername(), EntityType.USER);
    final var command = mockCommand(user.getUsername());

    // when
    final var authorizedTenantIds =
        runTaskInActor(() -> authorizationCheckBehavior.getAuthorizedTenantIds(command));

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
  public void shouldGetMappingAuthorizedTenantIds() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mappingId = createMapping(claimName, claimValue).getId();
    final var tenantId1 = createAndAssignTenant(mappingId, EntityType.MAPPING);
    final var tenantId2 = createAndAssignTenant(mappingId, EntityType.MAPPING);
    final var command = mockCommandWithMapping(claimName, claimValue);

    // when
    final var authorizedTenantIds =
        runTaskInActor(() -> authorizationCheckBehavior.getAuthorizedTenantIds(command));

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
  public void shouldGetUserAuthorizedTenantIdsThroughGroup() {
    // given
    final var user = createUser();
    final var groupKey = createGroup(user.getUserKey(), EntityType.USER);
    final var tenantId1 = createAndAssignTenant(groupKey, EntityType.GROUP);
    final var tenantId2 = createAndAssignTenant(groupKey, EntityType.GROUP);
    final var command = mockCommand(user.getUsername());

    // when
    final var authorizedTenantIds =
        runTaskInActor(() -> authorizationCheckBehavior.getAuthorizedTenantIds(command));

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
  public void shouldGetMappingAuthorizedTenantIdsThroughGroup() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mapping = createMapping(claimName, claimValue);
    final var groupKey = createGroup(mapping.getMappingKey(), EntityType.MAPPING);
    final var tenantId1 = createAndAssignTenant(groupKey, EntityType.GROUP);
    final var tenantId2 = createAndAssignTenant(groupKey, EntityType.GROUP);
    final var command = mockCommandWithMapping(claimName, claimValue);

    // when
    final var authorizedTenantIds =
        runTaskInActor(() -> authorizationCheckBehavior.getAuthorizedTenantIds(command));

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
  public void shouldGetDefaultAuthorizedTenantIdsIfUserKeyIsNotPresent() {
    // given
    final var command = mock(TypedRecord.class);

    // when
    final var authorizedTenantIds =
        runTaskInActor(
            () ->
                authorizationCheckBehavior
                    .getAuthorizedTenantIds(command)
                    .getAuthorizedTenantIds());

    // then
    assertThat(authorizedTenantIds).containsOnly(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  public void shouldGetDefaultAuthorizedTenantIdsIfUserIsNotPresent() {
    // given
    final var command = mockCommand("not-exists");

    // when
    final var authorizedTenantIds =
        runTaskInActor(() -> authorizationCheckBehavior.getAuthorizedTenantIds(command));

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
  public void shouldBeAuthorizedWhenMappingIsNotAssignedToRequestedTenant() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mapping =
        engine.mapping().newMapping(claimName).withClaimValue(claimValue).create().getValue();
    final var tenantId = "tenant";
    engine.tenant().newTenant().withTenantId(tenantId).create();
    engine
        .tenant()
        .addEntity(tenantId)
        .withEntityType(EntityType.MAPPING)
        .withEntityId(mapping.getId())
        .add();
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(
        mapping.getId(), AuthorizationOwnerType.MAPPING, resourceType, permissionType, resourceId);
    final var command = mockCommandWithMapping(claimName, claimValue);

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType, "anotherTenantId")
            .addResourceId(resourceId);
    final var authorized = runTaskInActor(() -> authorizationCheckBehavior.isAuthorized(request));

    // then
    EitherAssert.assertThat(authorized).isLeft();
  }

  @Test
  public void shouldBeUnauthorizedForMappingTenant() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mappingId = createMapping(claimName, claimValue).getId();
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
    final var authorized = runTaskInActor(() -> authorizationCheckBehavior.isAuthorized(request));

    // then
    assertThat(authorized.isRight()).isFalse();
  }

  @Test
  public void shouldBeUnauthorizedForUserTenant() {
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
  public void shouldGetAuthorizedTenantsForMapping() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mappingId = Strings.newRandomValidIdentityId();
    engine
        .mapping()
        .newMapping(claimName)
        .withClaimValue(claimValue)
        .withId(mappingId)
        .create()
        .getValue();
    final var tenantId = "tenant";
    engine.tenant().newTenant().withTenantId(tenantId).create();
    final var command = mockCommandWithMapping(claimName, claimValue);

    // when
    engine
        .tenant()
        .addEntity(tenantId)
        .withEntityType(EntityType.MAPPING)
        .withEntityId(mappingId)
        .add();

    // then
    assertThat(
            runTaskInActor(() -> authorizationCheckBehavior.getAuthorizedTenantIds(command))
                .getAuthorizedTenantIds())
        .singleElement()
        .isEqualTo(tenantId);
  }

  @Test
  public void shouldGetDefaultAuthorizedTenantForMapping() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    engine.mapping().newMapping(claimName).withClaimValue(claimValue).create().getValue();
    final var command = mockCommandWithMapping(claimName, claimValue);

    // when
    // then
    assertThat(
            runTaskInActor(() -> authorizationCheckBehavior.getAuthorizedTenantIds(command))
                .getAuthorizedTenantIds())
        .singleElement()
        .isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  public void shouldReturnAnonymousAuthorizedTenants() {
    // given
    final var command = mockCommandWithAnonymousUser();

    // when
    final var authorizedTenants =
        runTaskInActor(() -> authorizationCheckBehavior.getAuthorizedTenantIds(command));

    assertThat(authorizedTenants).isEqualTo(AuthorizedTenants.ANONYMOUS);
  }

  @Test
  public void shouldBeAuthorizedWhenMappingIsAssignedToRequestedTenant() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mapping =
        engine.mapping().newMapping(claimName).withClaimValue(claimValue).create().getValue();
    final var tenantId = "tenant";
    engine.tenant().newTenant().withTenantId(tenantId).create();
    engine
        .tenant()
        .addEntity(tenantId)
        .withEntityType(EntityType.MAPPING)
        .withEntityId(mapping.getId())
        .add();
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(
        mapping.getId(), AuthorizationOwnerType.MAPPING, resourceType, permissionType, resourceId);
    final var command = mockCommandWithMapping(claimName, claimValue);

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType, tenantId)
            .addResourceId(resourceId);
    final var authorized = runTaskInActor(() -> authorizationCheckBehavior.isAuthorized(request));

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
    return engine
        .user()
        .newUser(UUID.randomUUID().toString())
        .withName(UUID.randomUUID().toString())
        .withEmail(UUID.randomUUID().toString())
        .withPassword(UUID.randomUUID().toString())
        .create()
        .getValue();
  }

  private MappingRecordValue createMapping(final String claimName, final String claimValue) {
    return engine
        .mapping()
        .newMapping(claimName)
        .withClaimValue(claimValue)
        .withId(Strings.newRandomValidIdentityId())
        .create()
        .getValue();
  }

  private void addPermission(
      final String ownerId,
      final AuthorizationOwnerType ownerType,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType,
      final String... resourceIds) {
    for (final String resourceId : resourceIds) {
      engine
          .authorization()
          .newAuthorization()
          .withPermissions(permissionType)
          .withOwnerId(ownerId)
          .withOwnerType(ownerType)
          .withResourceType(resourceType)
          .withResourceId(resourceId)
          .create();
    }
  }

  private long createGroup(final long entityKey, final EntityType entityType) {
    final var groupKey = engine.group().newGroup(UUID.randomUUID().toString()).create().getKey();
    engine.group().addEntity(groupKey).withEntityKey(entityKey).withEntityType(entityType).add();
    return groupKey;
  }

  // TODO remove this method once Mappings and Groups are migrated to work with ids instead of
  private String createAndAssignTenant(final Long entityKey, final EntityType entityType) {
    return createAndAssignTenant(String.valueOf(entityKey), entityType);
  }

  private String createAndAssignTenant(final String entityId, final EntityType entityType) {
    final var tenantId = UUID.randomUUID().toString();
    engine.tenant().newTenant().withTenantId(tenantId).create();
    engine.tenant().addEntity(tenantId).withEntityId(entityId).withEntityType(entityType).add();
    return tenantId;
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

  private <A> A runTaskInActor(final Supplier<A> supplier) {
    return engine.getStreamProcessor(1).call(supplier::get).join();
  }
}
