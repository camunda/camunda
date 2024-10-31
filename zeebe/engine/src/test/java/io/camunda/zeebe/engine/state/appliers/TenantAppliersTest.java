/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableAuthorizationState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableTenantState;
import io.camunda.zeebe.engine.state.mutable.MutableUserState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class TenantAppliersTest {

  private MutableProcessingState processingState;

  private MutableTenantState tenantState;
  private MutableUserState userState;
  private MutableAuthorizationState authorizationState;
  private TenantDeletedApplier tenantDeletedApplier;
  private TenantEntityAddedApplier tenantEntityAddedApplier;

  @BeforeEach
  public void setup() {
    tenantState = processingState.getTenantState();
    userState = processingState.getUserState();
    authorizationState = processingState.getAuthorizationState();
    tenantDeletedApplier =
        new TenantDeletedApplier(
            processingState.getTenantState(),
            processingState.getUserState(),
            processingState.getAuthorizationState());
    tenantEntityAddedApplier =
        new TenantEntityAddedApplier(
            processingState.getTenantState(), processingState.getUserState());
  }

  @Test
  void shouldAddEntityToTenant() {
    // given
    final long entityKey = UUID.randomUUID().hashCode();
    final long tenantKey = UUID.randomUUID().hashCode();
    final var tenantId = UUID.randomUUID().toString();
    createTenant(tenantKey, tenantId);
    createUser(entityKey, "username");
    // when
    associateUserWithTenant(tenantKey, tenantId, entityKey);
    // then
    assertThat(tenantState.getEntitiesByType(tenantKey).get(EntityType.USER))
        .containsExactly(entityKey);
    final var persistedUser = userState.getUser(entityKey).get();
    assertThat(persistedUser.getTenantIdsList()).containsExactly(tenantId);
  }

  @Test
  void shouldDeleteTenantWithMultipleUsers() {
    // given
    final long tenantKey = UUID.randomUUID().hashCode();
    final var tenantId = UUID.randomUUID().toString();

    final var tenantRecord = createTenant(tenantKey, tenantId);
    final long[] userKeys = {1L, 2L, 3L};
    for (final long userKey : userKeys) {
      createUser(userKey, "user" + userKey);
      associateUserWithTenant(tenantKey, tenantId, userKey);
    }
    // Ensure the tenant and associated users exist before deletion
    assertThat(tenantState.getTenantByKey(tenantKey)).isPresent();
    assertUsersAreAssociatedWithTenant(userKeys, tenantId);
    // when
    tenantDeletedApplier.applyState(tenantKey, tenantRecord);
    // then verify tenant is deleted
    assertThat(tenantState.getTenantByKey(tenantKey)).isEmpty();
    // Verify all associated users are no longer linked to the tenant
    assertUsersAreNotAssociatedWithAnyTenant(userKeys);
    // Verify owner type and permissions are removed
    assertThat(authorizationState.getOwnerType(tenantKey)).isEmpty();
    final var resourceIdentifiers =
        authorizationState.getResourceIdentifiers(
            tenantKey, AuthorizationResourceType.TENANT, PermissionType.DELETE);
    assertThat(resourceIdentifiers).isEmpty();
  }

  @Test
  void shouldDeleteTenantWithoutEntities() {
    // given
    final long tenantKey = UUID.randomUUID().hashCode();
    final var tenantId = UUID.randomUUID().toString();
    // Create tenant without any entities
    final TenantRecord tenantRecord = createTenant(tenantKey, tenantId);
    // Ensure the tenant exists before deletion
    assertThat(tenantState.getTenantByKey(tenantKey)).isPresent();
    // when
    tenantDeletedApplier.applyState(tenantKey, tenantRecord);
    // then
    assertThat(tenantState.getTenantByKey(tenantKey)).isEmpty();
    final var ownerType = authorizationState.getOwnerType(tenantKey);
    assertThat(ownerType).isEmpty();
    final var resourceIdentifiers =
        authorizationState.getResourceIdentifiers(
            tenantKey, AuthorizationResourceType.TENANT, PermissionType.DELETE);
    assertThat(resourceIdentifiers).isEmpty();
  }

  private TenantRecord createTenant(final long tenantKey, final String tenantId) {
    final var tenantRecord =
        new TenantRecord()
            .setTenantKey(tenantKey)
            .setTenantId(tenantId)
            .setName("Tenant-" + tenantId);
    new TenantCreatedApplier(tenantState, authorizationState).applyState(tenantKey, tenantRecord);
    return tenantRecord;
  }

  private void createUser(final long userKey, final String username) {
    final var userRecord =
        new UserRecord()
            .setUserKey(userKey)
            .setUsername(username)
            .setName("User-" + username)
            .setEmail(username + "@test.com")
            .setPassword("password");
    new UserCreatedApplier(processingState).applyState(userKey, userRecord);
  }

  private void associateUserWithTenant(
      final long tenantKey, final String tenantId, final long userKey) {
    final var tenantRecord =
        new TenantRecord()
            .setTenantKey(tenantKey)
            .setTenantId(tenantId)
            .setEntityKey(userKey)
            .setEntityType(EntityType.USER);
    tenantEntityAddedApplier.applyState(tenantKey, tenantRecord);
  }

  private void assertUsersAreAssociatedWithTenant(final long[] userKeys, final String tenantId) {
    for (final long userKey : userKeys) {
      final var persistedUser = userState.getUser(userKey).get();
      assertThat(persistedUser.getTenantIdsList()).contains(tenantId);
    }
  }

  private void assertUsersAreNotAssociatedWithAnyTenant(final long[] userKeys) {
    for (final long userKey : userKeys) {
      final var persistedUser = userState.getUser(userKey).get();
      assertThat(persistedUser.getTenantIdsList()).isEmpty();
    }
  }
}
