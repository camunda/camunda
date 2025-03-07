/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableGroupState;
import io.camunda.zeebe.engine.state.mutable.MutableMappingState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableTenantState;
import io.camunda.zeebe.engine.state.mutable.MutableUserState;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;

public class TenantEntityAddedApplier implements TypedEventApplier<TenantIntent, TenantRecord> {

  private final MutableTenantState tenantState;
  private final MutableUserState userState;
  private final MutableMappingState mappingState;
  private final MutableGroupState groupState;

  public TenantEntityAddedApplier(final MutableProcessingState state) {
    tenantState = state.getTenantState();
    userState = state.getUserState();
    mappingState = state.getMappingState();
    groupState = state.getGroupState();
  }

  @Override
  public void applyState(final long tenantKey, final TenantRecord tenant) {
    switch (tenant.getEntityType()) {
      case USER -> {
        tenantState.addEntity(tenant);
        userState.addTenantId(tenant.getEntityId(), tenant.getTenantId());
      }
      case MAPPING -> {
        tenantState.addEntity(tenant);
        mappingState.addTenant(tenant.getEntityKey(), tenant.getTenantId());
      }
      case GROUP -> {
        tenantState.addEntity(tenant);
        // todo remove parse long when will be ability to use group ID
        groupState.addTenant(Long.parseLong(tenant.getEntityKey()), tenant.getTenantId());
      }
      default ->
          throw new IllegalStateException(
              String.format(
                  "Expected to add entity '%d' to tenant '%s', but entities of type '%s' cannot be added to tenants",
                  tenant.getEntityKey(), tenant.getTenantId(), tenant.getEntityType()));
    }
  }
}
