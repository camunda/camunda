/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableTenantState;
import io.camunda.zeebe.engine.state.mutable.MutableUserState;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;

public class TenantEntityRemovedApplier implements TypedEventApplier<TenantIntent, TenantRecord> {

  private final MutableTenantState tenantState;
  private final MutableUserState userState;

  public TenantEntityRemovedApplier(final MutableProcessingState state) {
    tenantState = state.getTenantState();
    userState = state.getUserState();
  }

  @Override
  public void applyState(final long tenantKey, final TenantRecord tenant) {
    switch (tenant.getEntityType()) {
      case USER -> {
        tenantState.removeEntity(tenant);
        userState.removeTenant(tenant.getEntityId(), tenant.getTenantId());
      }
      default ->
          throw new UnsupportedOperationException(
              String.format(
                  "Expected to remove entity with id %s and type %s from tenant %s, but type %s is not supported.",
                  tenant.getEntityId(),
                  tenant.getEntityType(),
                  tenant.getTenantId(),
                  tenant.getEntityType()));
    }
  }
}
