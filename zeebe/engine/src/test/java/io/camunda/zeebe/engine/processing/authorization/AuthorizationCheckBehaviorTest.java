/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.authorization;

import static io.camunda.zeebe.auth.impl.Authorization.AUTHORIZED_ANONYMOUS_USER;
import static io.camunda.zeebe.auth.impl.Authorization.AUTHORIZED_USER_KEY;
import static io.camunda.zeebe.auth.impl.Authorization.USER_TOKEN_CLAIM_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.security.configuration.AuthorizationsConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.identity.AuthorizedTenants;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class AuthorizationCheckBehaviorTest {
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
    authorizationCheckBehavior = new AuthorizationCheckBehavior(processingState, securityConfig);
  }

  @Test
  public void shouldBeAuthorizedWhenUserHasPermission() {
    // given
    final var userKey = createUser();
    final var resourceType = AuthorizationResourceType.DEPLOYMENT;
    final var permissionType = PermissionType.DELETE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(userKey, resourceType, permissionType, resourceId);
    final var command = mockCommand(userKey);

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType).addResourceId(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    assertThat(authorized.isRight()).isTrue();
  }

  @Test
  public void shouldNotBeAuthorizedWhenUserHasNoPermission() {
    // given
    final var userKey = createUser();
    final var resourceType = AuthorizationResourceType.DEPLOYMENT;
    final var permissionType = PermissionType.DELETE;
    final var resourceId = UUID.randomUUID().toString();
    final var command = mockCommand(userKey);

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType).addResourceId(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    assertThat(authorized.isRight()).isFalse();
  }

  @Test
  public void shouldGetResourceIdentifiersWhenUserHasPermissions() {
    // given
    final var userKey = createUser();
    final var resourceType = AuthorizationResourceType.DEPLOYMENT;
    final var permissionType = PermissionType.DELETE;
    final var resourceId1 = UUID.randomUUID().toString();
    final var resourceId2 = UUID.randomUUID().toString();
    addPermission(userKey, resourceType, permissionType, resourceId1, resourceId2);
    final var command = mockCommand(userKey);

    // when
    final var request = new AuthorizationRequest(command, resourceType, permissionType);
    final var resourceIdentifiers =
        authorizationCheckBehavior.getAllAuthorizedResourceIdentifiers(request);

    // then
    assertThat(resourceIdentifiers).containsExactlyInAnyOrder(resourceId1, resourceId2);
  }

  @Test
  public void shouldGetEmptySetWhenUserHasNoPermissions() {
    // given
    final var userKey = createUser();
    final var resourceType = AuthorizationResourceType.DEPLOYMENT;
    final var permissionType = PermissionType.DELETE;
    final var command = mockCommand(userKey);

    // when
    final var request = new AuthorizationRequest(command, resourceType, permissionType);
    final var resourceIdentifiers =
        authorizationCheckBehavior.getAllAuthorizedResourceIdentifiers(request);

    // then
    assertThat(resourceIdentifiers).isEmpty();
  }

  @Test
  public void shouldBeAuthorizedWhenRoleHasPermissions() {
    // given
    final var userKey = createUser();
    final var roleKey = createRole(userKey);
    final var resourceType = AuthorizationResourceType.DEPLOYMENT;
    final var permissionType = PermissionType.DELETE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(roleKey, resourceType, permissionType, resourceId);
    final var command = mockCommand(userKey);

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType).addResourceId(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    assertThat(authorized.isRight()).isTrue();
  }

  @Test
  public void shouldGetResourceIdentifiersWhenRoleHasPermissions() {
    // given
    final var userKey = createUser();
    final var roleKey = createRole(userKey);
    final var resourceType = AuthorizationResourceType.DEPLOYMENT;
    final var permissionType = PermissionType.DELETE;
    final var resourceId1 = UUID.randomUUID().toString();
    final var resourceId2 = UUID.randomUUID().toString();
    addPermission(roleKey, resourceType, permissionType, resourceId1, resourceId2);
    final var command = mockCommand(userKey);

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
    final var userKey = createUser();
    final var groupKey = createGroup(userKey);
    final var resourceType = AuthorizationResourceType.DEPLOYMENT;
    final var permissionType = PermissionType.DELETE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(groupKey, resourceType, permissionType, resourceId);
    final var command = mockCommand(userKey);

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType).addResourceId(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    assertThat(authorized.isRight()).isTrue();
  }

  @Test
  public void shouldGetResourceIdentifiersWhenGroupHasPermissions() {
    // given
    final var userKey = createUser();
    final var groupKey = createGroup(userKey);
    final var resourceType = AuthorizationResourceType.DEPLOYMENT;
    final var permissionType = PermissionType.DELETE;
    final var resourceId1 = UUID.randomUUID().toString();
    final var resourceId2 = UUID.randomUUID().toString();
    addPermission(groupKey, resourceType, permissionType, resourceId1, resourceId2);
    final var command = mockCommand(userKey);

    // when
    final var request = new AuthorizationRequest(command, resourceType, permissionType);
    final var resourceIdentifiers =
        authorizationCheckBehavior.getAllAuthorizedResourceIdentifiers(request);

    // then
    assertThat(resourceIdentifiers).containsExactlyInAnyOrder(resourceId1, resourceId2);
  }

  @Test
  public void shouldBeAuthorizedForTenant() {
    // given
    final var userKey = createUser();
    final var resourceType = AuthorizationResourceType.DEPLOYMENT;
    final var permissionType = PermissionType.DELETE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(userKey, resourceType, permissionType, resourceId);
    final var tenantId = createAndAssignTenant(userKey);
    final var command = mockCommand(userKey);

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType, tenantId)
            .addResourceId(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    assertThat(authorized.isRight()).isTrue();
  }

  @Test
  public void shouldBeUnauthorizedForTenant() {
    // given
    final var userKey = createUser();
    final var resourceType = AuthorizationResourceType.DEPLOYMENT;
    final var permissionType = PermissionType.DELETE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(userKey, resourceType, permissionType, resourceId);
    final var anotherTenantId = "authorizedForAnotherTenant";
    createAndAssignTenant(userKey);
    final var command = mockCommand(userKey);

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType, anotherTenantId)
            .addResourceId(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    assertThat(authorized.isRight()).isFalse();
  }

  @Test
  public void shouldGetAuthorizedTenantIds() {
    // given
    final var userKey = createUser();
    final var tenantId1 = createAndAssignTenant(userKey);
    final var tenantId2 = createAndAssignTenant(userKey);
    final var command = mockCommand(userKey);

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
  public void shouldGetDefaultAuthorizedTenantIdsIfUserKeyIsNotPresent() {
    // given
    final var command = mock(TypedRecord.class);

    // when
    final var authorizedTenantIds =
        authorizationCheckBehavior.getAuthorizedTenantIds(command).getAuthorizedTenantIds();

    // then
    assertThat(authorizedTenantIds).containsOnly(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  public void shouldGetDefaultAuthorizedTenantIdsIfUserIsNotPresent() {
    // given
    final var command = mockCommand(1L);

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
  public void shouldBeAuthorizedWhenAnonymousAuthenticationProvided() {
    // given
    final var userKey = createUser();
    final var resourceType = AuthorizationResourceType.DEPLOYMENT;
    final var permissionType = PermissionType.DELETE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(userKey, resourceType, permissionType, resourceId);
    final var command = mockCommandWithAnonymousUser(userKey);

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType).addResourceId(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    assertThat(authorized.isRight()).isTrue();
  }

  @Test
  public void shouldBeAuthorizedForAnyTenantWhenAnonymousAuthenticationProvided() {
    // given
    final var command = mockCommandWithAnonymousUser(1L);

    // when
    final var authorizedTenantIds = authorizationCheckBehavior.getAuthorizedTenantIds(command);

    // then
    assertThat(authorizedTenantIds).isEqualTo(AuthorizedTenants.ANONYMOUS);
    assertThat(authorizedTenantIds.getAuthorizedTenantIds()).containsOnly("*");
    assertThat(authorizedTenantIds.isAuthorizedForTenantId("foo")).isTrue();
    assertThat(authorizedTenantIds.isAuthorizedForTenantIds(List.of("foo"))).isTrue();
  }

  @Test
  public void shouldBeAuthorizedWhenMappingHasPermission() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mapping =
        engine.mapping().newMapping(claimName).withClaimValue(claimValue).create().getValue();
    final var resourceType = AuthorizationResourceType.DEPLOYMENT;
    final var permissionType = PermissionType.DELETE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(mapping.getMappingKey(), resourceType, permissionType, resourceId);
    final var command = mockCommandWithMapping(claimName, claimValue);

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType).addResourceId(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    EitherAssert.assertThat(authorized).isRight();
  }

  @Test
  public void shouldBeAuthorizedWhenMappingIsAssignedToRequestedTenant() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mapping =
        engine.mapping().newMapping(claimName).withClaimValue(claimValue).create().getValue();
    final var mappingKey = mapping.getMappingKey();
    final var tenantId = "tenant";
    final var tenantKey = engine.tenant().newTenant().withTenantId(tenantId).create().getKey();
    engine
        .tenant()
        .addEntity(tenantKey)
        .withEntityType(EntityType.MAPPING)
        .withEntityKey(mappingKey)
        .add();
    final var resourceType = AuthorizationResourceType.DEPLOYMENT;
    final var permissionType = PermissionType.DELETE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(mappingKey, resourceType, permissionType, resourceId);
    final var command = mockCommandWithMapping(claimName, claimValue);

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType, tenantId)
            .addResourceId(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    EitherAssert.assertThat(authorized).isRight();
  }

  @Test
  public void shouldBeAuthorizedWhenMappingIsNotAssignedToRequestedTenant() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mapping =
        engine.mapping().newMapping(claimName).withClaimValue(claimValue).create().getValue();
    final var mappingKey = mapping.getMappingKey();
    final var tenantId = "tenant";
    final var tenantKey = engine.tenant().newTenant().withTenantId(tenantId).create().getKey();
    engine
        .tenant()
        .addEntity(tenantKey)
        .withEntityType(EntityType.MAPPING)
        .withEntityKey(mappingKey)
        .add();
    final var resourceType = AuthorizationResourceType.DEPLOYMENT;
    final var permissionType = PermissionType.DELETE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(mappingKey, resourceType, permissionType, resourceId);
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
  public void shouldBeAuthorizedWhenMappingIsAuthorizedThroughGroup() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mappingKey =
        engine.mapping().newMapping(claimName).withClaimValue(claimValue).create().getKey();
    final var groupKey = engine.group().newGroup(UUID.randomUUID().toString()).create().getKey();
    engine
        .group()
        .addEntity(groupKey)
        .withEntityType(EntityType.MAPPING)
        .withEntityKey(mappingKey)
        .add();
    final var resourceType = AuthorizationResourceType.DEPLOYMENT;
    final var permissionType = PermissionType.DELETE;
    final var resourceId = UUID.randomUUID().toString();
    engine
        .authorization()
        .permission()
        .withOwnerKey(groupKey)
        .withResourceType(resourceType)
        .withPermission(permissionType, resourceId)
        .add();
    final var command = mockCommandWithMapping(claimName, claimValue);

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType).addResourceId(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    EitherAssert.assertThat(authorized).isRight();
  }

  @Test
  public void shouldBeAuthorizedWhenMappingIsAuthorizedThroughRole() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mappingKey =
        engine.mapping().newMapping(claimName).withClaimValue(claimValue).create().getKey();
    final var roleKey = engine.role().newRole("role").create().getKey();
    engine
        .role()
        .addEntity(roleKey)
        .withEntityType(EntityType.MAPPING)
        .withEntityKey(mappingKey)
        .add();
    final var resourceType = AuthorizationResourceType.DEPLOYMENT;
    final var permissionType = PermissionType.DELETE;
    final var resourceId = UUID.randomUUID().toString();
    engine
        .authorization()
        .permission()
        .withOwnerKey(roleKey)
        .withResourceType(resourceType)
        .withPermission(permissionType, resourceId)
        .add();
    final var command = mockCommandWithMapping(claimName, claimValue);

    // when
    final var request =
        new AuthorizationRequest(command, resourceType, permissionType).addResourceId(resourceId);
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    EitherAssert.assertThat(authorized).isRight();
  }

  @Test
  public void shouldNotBeAuthorizedWhenMappingHasNoPermission() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    engine.mapping().newMapping(claimName).withClaimValue(claimValue).create().getValue();
    final var command = mockCommandWithMapping(claimName, claimValue);

    // when
    final var request =
        new AuthorizationRequest(
                command, AuthorizationResourceType.DEPLOYMENT, PermissionType.DELETE)
            .addResourceId(UUID.randomUUID().toString());
    final var authorized = authorizationCheckBehavior.isAuthorized(request);

    // then
    EitherAssert.assertThat(authorized).isLeft();
  }

  @Test
  public void shouldBeAuthorizedThroughMultipleMappings() {
    // given
    final var firstClaimName = UUID.randomUUID().toString();
    final var firstClaimValue = UUID.randomUUID().toString();
    final var firstMappingKey =
        engine
            .mapping()
            .newMapping(firstClaimName)
            .withClaimValue(firstClaimValue)
            .create()
            .getKey();
    final var secondClaimName = UUID.randomUUID().toString();
    final var secondClaimValue = UUID.randomUUID().toString();
    final var secondMappingKey =
        engine
            .mapping()
            .newMapping(secondClaimName)
            .withClaimValue(secondClaimValue)
            .create()
            .getKey();

    final var resourceType = AuthorizationResourceType.DEPLOYMENT;
    final var permissionType = PermissionType.DELETE;
    final var firstResourceId = UUID.randomUUID().toString();
    final var secondResourceId = UUID.randomUUID().toString();
    engine
        .authorization()
        .permission()
        .withOwnerKey(firstMappingKey)
        .withResourceType(resourceType)
        .withPermission(permissionType, firstResourceId)
        .add();
    engine
        .authorization()
        .permission()
        .withOwnerKey(secondMappingKey)
        .withResourceType(resourceType)
        .withPermission(permissionType, secondResourceId)
        .add();

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
  public void shouldBeAuthorizedThroughMappingWithMultipleClaimValues() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var firstClaimValue = UUID.randomUUID().toString();
    final var firstMappingKey =
        engine.mapping().newMapping(claimName).withClaimValue(firstClaimValue).create().getKey();
    final var secondClaimValue = UUID.randomUUID().toString();
    final var secondMappingKey =
        engine.mapping().newMapping(claimName).withClaimValue(secondClaimValue).create().getKey();

    final var resourceType = AuthorizationResourceType.DEPLOYMENT;
    final var permissionType = PermissionType.DELETE;
    final var firstResourceId = UUID.randomUUID().toString();
    final var secondResourceId = UUID.randomUUID().toString();
    engine
        .authorization()
        .permission()
        .withOwnerKey(firstMappingKey)
        .withResourceType(resourceType)
        .withPermission(permissionType, firstResourceId)
        .add();
    engine
        .authorization()
        .permission()
        .withOwnerKey(secondMappingKey)
        .withResourceType(resourceType)
        .withPermission(permissionType, secondResourceId)
        .add();

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
  public void shouldGetAuthorizationsForMapping() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mapping =
        engine.mapping().newMapping(claimName).withClaimValue(claimValue).create().getValue();
    final var resourceType = AuthorizationResourceType.DEPLOYMENT;
    final var permissionType = PermissionType.DELETE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(mapping.getMappingKey(), resourceType, permissionType, resourceId);
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
  public void shouldGetAuthorizationsForMappingThroughAssignedRole() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mapping =
        engine.mapping().newMapping(claimName).withClaimValue(claimValue).create().getValue();
    final var mappingKey = mapping.getMappingKey();
    final var roleKey = engine.role().newRole(UUID.randomUUID().toString()).create().getKey();
    engine
        .role()
        .addEntity(roleKey)
        .withEntityType(EntityType.MAPPING)
        .withEntityKey(mappingKey)
        .add();
    final var resourceType = AuthorizationResourceType.DEPLOYMENT;
    final var permissionType = PermissionType.DELETE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(roleKey, resourceType, permissionType, resourceId);
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
  public void shouldGetAuthorizationsForMappingThroughAssignedGroup() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mapping =
        engine.mapping().newMapping(claimName).withClaimValue(claimValue).create().getValue();
    final var mappingKey = mapping.getMappingKey();
    final var groupKey = engine.group().newGroup(UUID.randomUUID().toString()).create().getKey();
    engine
        .group()
        .addEntity(groupKey)
        .withEntityType(EntityType.MAPPING)
        .withEntityKey(mappingKey)
        .add();
    final var resourceType = AuthorizationResourceType.DEPLOYMENT;
    final var permissionType = PermissionType.DELETE;
    final var resourceId = UUID.randomUUID().toString();
    addPermission(groupKey, resourceType, permissionType, resourceId);
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
  public void shouldGetAuthorizedTenantsForMapping() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mapping =
        engine.mapping().newMapping(claimName).withClaimValue(claimValue).create().getValue();
    final var mappingKey = mapping.getMappingKey();
    final var tenantId = "tenant";
    final var tenantKey = engine.tenant().newTenant().withTenantId(tenantId).create().getKey();
    final var command = mockCommandWithMapping(claimName, claimValue);

    // when
    engine
        .tenant()
        .addEntity(tenantKey)
        .withEntityType(EntityType.MAPPING)
        .withEntityKey(mappingKey)
        .add();

    // then
    assertThat(authorizationCheckBehavior.getAuthorizedTenantIds(command).getAuthorizedTenantIds())
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
    assertThat(authorizationCheckBehavior.getAuthorizedTenantIds(command).getAuthorizedTenantIds())
        .singleElement()
        .isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  private TypedRecord<?> mockCommandWithMapping(final String claimName, final String claimValue) {
    final var command = mock(TypedRecord.class);
    when(command.getAuthorizations())
        .thenReturn(Map.of(USER_TOKEN_CLAIM_PREFIX + claimName, claimValue));
    when(command.hasRequestMetadata()).thenReturn(true);
    return command;
  }

  private long createUser() {
    return engine
        .user()
        .newUser(UUID.randomUUID().toString())
        .withName(UUID.randomUUID().toString())
        .withEmail(UUID.randomUUID().toString())
        .withPassword(UUID.randomUUID().toString())
        .create()
        .getKey();
  }

  private long createRole(final long userKey) {
    final var roleKey = engine.role().newRole(UUID.randomUUID().toString()).create().getKey();
    engine.role().addEntity(roleKey).withEntityKey(userKey).withEntityType(EntityType.USER).add();
    return roleKey;
  }

  private long createGroup(final long userKey) {
    final var groupKey = engine.group().newGroup(UUID.randomUUID().toString()).create().getKey();
    engine.group().addEntity(groupKey).withEntityKey(userKey).withEntityType(EntityType.USER).add();
    return groupKey;
  }

  private void addPermission(
      final long userKey,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType,
      final String... resourceIds) {
    final var client =
        engine.authorization().permission().withOwnerKey(userKey).withResourceType(resourceType);

    for (final String resourceId : resourceIds) {
      client.withPermission(permissionType, resourceId);
    }

    client.add();
  }

  private String createAndAssignTenant(final long userKey) {
    final var tenantId = UUID.randomUUID().toString();
    final var tenantKey = engine.tenant().newTenant().withTenantId(tenantId).create().getKey();
    engine
        .tenant()
        .addEntity(tenantKey)
        .withEntityKey(userKey)
        .withEntityType(EntityType.USER)
        .add();
    return tenantId;
  }

  private TypedRecord<?> mockCommand(final long userKey) {
    final var command = mock(TypedRecord.class);
    when(command.getAuthorizations()).thenReturn(Map.of(AUTHORIZED_USER_KEY, userKey));
    when(command.hasRequestMetadata()).thenReturn(true);
    return command;
  }

  private TypedRecord<?> mockCommandWithAnonymousUser(final long userKey) {
    final var command = mock(TypedRecord.class);
    when(command.getAuthorizations()).thenReturn(Map.of(AUTHORIZED_ANONYMOUS_USER, true));
    when(command.hasRequestMetadata()).thenReturn(true);
    return command;
  }
}
