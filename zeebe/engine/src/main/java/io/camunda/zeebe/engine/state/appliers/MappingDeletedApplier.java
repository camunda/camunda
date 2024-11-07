/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.authorization.PersistedMapping;
import io.camunda.zeebe.engine.state.mutable.MutableAuthorizationState;
import io.camunda.zeebe.engine.state.mutable.MutableGroupState;
import io.camunda.zeebe.engine.state.mutable.MutableMappingState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableRoleState;
import io.camunda.zeebe.engine.state.mutable.MutableTenantState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRecord;
import io.camunda.zeebe.protocol.record.intent.MappingIntent;

public class MappingDeletedApplier implements TypedEventApplier<MappingIntent, MappingRecord> {

  private final MutableMappingState mappingState;
  private final MutableAuthorizationState authorizationState;
  private final MutableRoleState roleState;
  private final MutableTenantState tenantState;
  private final MutableGroupState groupState;

  public MappingDeletedApplier(final MutableProcessingState state) {
    mappingState = state.getMappingState();
    authorizationState = state.getAuthorizationState();
    roleState = state.getRoleState();
    tenantState = state.getTenantState();
    groupState = state.getGroupState();
  }

  @Override
  public void applyState(final long key, final MappingRecord value) {
    // retrieve mapping from the state
    final var mappingKey = value.getMappingKey();
    final var mapping = mappingState.get(mappingKey);
    if (mapping.isEmpty()) {
      throw new IllegalStateException(
          String.format(
              "Expected to delete mapping with key '%s', but a mapping with this key does not exist.",
              value.getMappingKey()));
    }
    final var persistedMapping = mapping.get();
    removeMappingFromRoleState(persistedMapping);
    removeMappingFromGroupState(persistedMapping);
    removeMappingFromTenantState(persistedMapping);
    // remove mapping from authorization state
    authorizationState.deleteOwnerTypeByKey(mappingKey);
    mappingState.delete(mappingKey);
  }

  private void removeMappingFromRoleState(final PersistedMapping mapping) {
    mapping
        .getRoleKeysList()
        .forEach(roleKey -> roleState.removeEntity(roleKey, mapping.getMappingKey()));
  }

  private void removeMappingFromTenantState(final PersistedMapping persistedMapping) {
    persistedMapping
        .getTenantKeysList()
        .forEach(
            tenantKey -> tenantState.removeEntity(tenantKey, persistedMapping.getMappingKey()));
  }

  private void removeMappingFromGroupState(final PersistedMapping persistedMapping) {
    persistedMapping
        .getGroupKeysList()
        .forEach(groupKey -> groupState.removeEntity(groupKey, persistedMapping.getMappingKey()));
  }
}
