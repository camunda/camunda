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
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
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
    final long entityKey = 1L;
    userState.create(
        new UserRecord()
            .setUserKey(entityKey)
            .setUsername("username")
            .setName("Foo")
            .setEmail("foo@bar.com")
            .setPassword("password"));
    final long tenantKey = 11L;
    final String tenantId = "tenant-id-11";
    final var tenantRecord =
        new TenantRecord().setTenantKey(tenantKey).setTenantId(tenantId).setName("foo");
    tenantState.createTenant(tenantRecord);
    tenantRecord.setEntityKey(entityKey).setEntityType(EntityType.USER);

    // when
    tenantEntityAddedApplier.applyState(tenantKey, tenantRecord);

    // then
    assertThat(tenantState.getEntitiesByType(tenantKey).get(EntityType.USER))
        .containsExactly(entityKey);
    final var persistedUser = userState.getUser(entityKey).get();
    assertThat(persistedUser.getTenantIdsList()).containsExactly(tenantId);
  }

  @Test
  void shouldDeleteTenant() {
    // given
    userState.create(
        new UserRecord()
            .setUserKey(1L)
            .setUsername("username")
            .setName("Foo")
            .setEmail("foo@bar.com")
            .setPassword("password"));
    final long tenantKey = 11L;
    final String tenantId = "tenant-id-11";
    final var tenantRecord =
        new TenantRecord().setTenantKey(tenantKey).setTenantId(tenantId).setName("foo");
    tenantState.createTenant(tenantRecord);
    tenantRecord.setEntityKey(1L).setEntityType(EntityType.USER);
    tenantEntityAddedApplier.applyState(tenantKey, tenantRecord);
    authorizationState.insertOwnerTypeByKey(tenantKey, AuthorizationOwnerType.ROLE);
    authorizationState.createOrAddPermission(
        tenantKey,
        AuthorizationResourceType.ROLE,
        PermissionType.DELETE,
        List.of("tenant1", "tenant2"));

    // when
    tenantDeletedApplier.applyState(tenantKey, tenantRecord);

    // then
    assertThat(tenantState.getTenantByKey(tenantKey)).isEmpty();
    final var persistedUser = userState.getUser(1L).get();
    assertThat(persistedUser.getTenantIdsList()).isEmpty();
    final var ownerType = authorizationState.getOwnerType(tenantKey);
    assertThat(ownerType).isEmpty();
    final var resourceIdentifiers =
        authorizationState.getResourceIdentifiers(
            tenantKey, AuthorizationResourceType.ROLE, PermissionType.DELETE);
    assertThat(resourceIdentifiers).isEmpty();
  }
}
