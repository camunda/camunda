/*
 * Copyright Camunda Services GmbH and/or licensed to
 * Camunda Services GmbH under one or more contributor
 * license agreements. See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership. Licensed under the Camunda License 1.0.
 * You may not use this file except in compliance with the Camunda License 1.0.
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
import io.camunda.zeebe.protocol.record.value.EntityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class TenantAppliersTest {

  private MutableProcessingState processingState;
  private MutableTenantState tenantState;
  private MutableUserState userState;
  private MutableAuthorizationState authorizationState;

  private TenantCreatedApplier tenantCreatedApplier;
  private TenantDeletedApplier tenantDeletedApplier;
  private TenantEntityAddedApplier tenantEntityAddedApplier;
  private TenantEntityRemovedApplier tenantEntityRemovedApplier;

  @BeforeEach
  public void setup() {
    tenantState = processingState.getTenantState();
    userState = processingState.getUserState();
    authorizationState = processingState.getAuthorizationState();

    tenantCreatedApplier = new TenantCreatedApplier(tenantState);
    tenantDeletedApplier = new TenantDeletedApplier(tenantState, userState, authorizationState);
    tenantEntityAddedApplier = new TenantEntityAddedApplier(tenantState, userState);
    tenantEntityRemovedApplier = new TenantEntityRemovedApplier(tenantState, userState);
  }

  @Test
  void shouldCreateTenant() {
    // given
    final long tenantKey = 1L;
    final String tenantId = "tenant-1";
    final var tenantRecord =
        new TenantRecord().setTenantKey(tenantKey).setTenantId(tenantId).setName("Tenant One");

    // when
    tenantCreatedApplier.applyState(tenantKey, tenantRecord);

    // then
    final var persistedTenant = tenantState.getTenantByKey(tenantKey);
    assertThat(persistedTenant).isPresent();
    assertThat(persistedTenant.get().getTenantId()).isEqualTo(tenantId);
  }

  @Test
  void shouldAddEntityToTenant() {
    // given
    final long tenantKey = 1L;
    final String tenantId = "tenant-1";
    final long userKey = 100L;

    // Create a user and link it to the tenant
    userState.create(
        new UserRecord()
            .setUserKey(userKey)
            .setUsername("username")
            .setName("Foo")
            .setEmail("foo@bar.com")
            .setPassword("password"));

    final var tenantRecord =
        new TenantRecord()
            .setTenantKey(tenantKey)
            .setTenantId(tenantId)
            .setEntityKey(userKey)
            .setEntityType(EntityType.USER);

    // when
    tenantEntityAddedApplier.applyState(tenantKey, tenantRecord);

    // then
    assertThat(tenantState.getEntitiesByType(tenantKey).get(EntityType.USER))
        .containsExactly(userKey);
    final var persistedUser = userState.getUser(userKey).get();
    assertThat(persistedUser.getTenantIdsList()).containsExactly(tenantId);
  }

  @Test
  void shouldRemoveEntityFromTenant() {
    // given
    final long tenantKey = 1L;
    final long userKey = 100L;
    final String tenantId = "tenant-1";

    // Create a tenant and add a user entity to it
    tenantState.createTenant(new TenantRecord().setTenantKey(tenantKey).setTenantId(tenantId));
    tenantState.addEntity(
        new TenantRecord()
            .setTenantKey(tenantKey)
            .setEntityKey(userKey)
            .setEntityType(EntityType.USER));
    userState.addTenantId(userKey, tenantId);

    // when
    tenantEntityRemovedApplier.applyState(
        tenantKey,
        new TenantRecord()
            .setTenantKey(tenantKey)
            .setEntityKey(userKey)
            .setEntityType(EntityType.USER));

    // then
    assertThat(tenantState.getEntityType(tenantKey, userKey)).isEmpty();
    assertThat(userState.getUser(userKey).get().getTenantIdsList()).isEmpty();
  }

  @Test
  void shouldDeleteTenant() {
    // given
    final long tenantKey = 1L;
    final String tenantId = "tenant-1";
    tenantState.createTenant(new TenantRecord().setTenantKey(tenantKey).setTenantId(tenantId));
    tenantState.addEntity(
        new TenantRecord()
            .setTenantKey(tenantKey)
            .setEntityKey(100L)
            .setEntityType(EntityType.USER));

    authorizationState.insertOwnerTypeByKey(tenantKey, AuthorizationOwnerType.UNSPECIFIED);

    // when
    tenantDeletedApplier.applyState(tenantKey, new TenantRecord().setTenantKey(tenantKey));

    // then
    assertThat(tenantState.getTenantByKey(tenantKey)).isEmpty();
    assertThat(tenantState.getEntityType(tenantKey, 100L)).isEmpty();
    assertThat(authorizationState.getOwnerType(tenantKey)).isEmpty();
  }
}
