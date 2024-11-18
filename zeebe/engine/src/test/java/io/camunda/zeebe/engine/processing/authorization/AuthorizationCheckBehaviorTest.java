/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.authorization;

import static io.camunda.zeebe.auth.impl.Authorization.AUTHORIZED_USER_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
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
    authorizationCheckBehavior =
        new AuthorizationCheckBehavior(
            processingState.getAuthorizationState(),
            processingState.getUserState(),
            new EngineConfiguration().setEnableAuthorization(true));
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
    assertThat(authorized).isTrue();
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
    assertThat(authorized).isFalse();
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
        authorizationCheckBehavior.getAuthorizedResourceIdentifiers(request);

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
        authorizationCheckBehavior.getAuthorizedResourceIdentifiers(request);

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
    assertThat(authorized).isTrue();
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
        authorizationCheckBehavior.getAuthorizedResourceIdentifiers(request);

    // then
    assertThat(resourceIdentifiers).containsExactlyInAnyOrder(resourceId1, resourceId2);
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

  private TypedRecord<?> mockCommand(final long userKey) {
    final var command = mock(TypedRecord.class);
    when(command.getAuthorizations()).thenReturn(Map.of(AUTHORIZED_USER_KEY, userKey));
    when(command.hasRequestMetadata()).thenReturn(true);
    return command;
  }
}
