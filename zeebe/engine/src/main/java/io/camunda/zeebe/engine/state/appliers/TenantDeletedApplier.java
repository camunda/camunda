/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableAuthorizationState;
import io.camunda.zeebe.engine.state.mutable.MutableTenantState;
import io.camunda.zeebe.engine.state.mutable.MutableUserState;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TenantDeletedApplier implements TypedEventApplier<TenantIntent, TenantRecord> {
  private final MutableTenantState tenantState;
  private final MutableUserState userState;
  private final MutableAuthorizationState authorizationState;

  public TenantDeletedApplier(
      final MutableTenantState tenantState,
      final MutableUserState userState,
      final MutableAuthorizationState authorizationState) {
    this.tenantState = tenantState;
    this.userState = userState;
    this.authorizationState = authorizationState;
  }

  @Override
  public void applyState(final long tenantKey, final TenantRecord value) {
    final Optional<TenantRecord> tenantOptional = tenantState.getTenantByKey(tenantKey);
    final var tenant =
        tenantOptional.orElseThrow(
            () ->
                new IllegalStateException(
                    "Failed to delete tenant: Tenant with key " + tenantKey + " does not exist."));
    final var entities = tenantState.getEntitiesByType(tenantKey);

    handleUserEntities(entities, tenant.getTenantId());
    handleMappingEntities(entities, tenantKey);
    deleteTenantAuthorizations(tenantKey);
    tenantState.delete(tenant);
  }

  private void handleUserEntities(
      final Map<EntityType, List<Long>> entities, final String tenantId) {
    final List<Long> userEntities = entities.get(EntityType.USER);
    if (userEntities != null) {
      userEntities.forEach(
          userKey -> {
            userState.removeTenant(userKey, tenantId);
          });
    }
  }

  private void handleMappingEntities(
      final Map<EntityType, List<Long>> entities, final long tenantId) {
    final List<Long> mappingEntities = entities.get(EntityType.MAPPING);
    if (mappingEntities != null) {
      mappingEntities.forEach(
          mappingKey -> {
            // todo  Uncomment when the mapping state is implemented
            // mappingState.removeTenant(mappingKey, tenantKey);
          });
    }
  }

  private void deleteTenantAuthorizations(final long tenantKey) {
    authorizationState.deleteAuthorizationsByOwnerKeyPrefix(tenantKey);
    authorizationState.deleteOwnerTypeByKey(tenantKey);
  }
}
