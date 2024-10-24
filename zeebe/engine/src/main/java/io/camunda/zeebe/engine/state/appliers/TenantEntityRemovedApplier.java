/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableTenantState;
import io.camunda.zeebe.engine.state.mutable.MutableUserState;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;

public class TenantEntityRemovedApplier implements TypedEventApplier<TenantIntent, TenantRecord> {

  private final MutableTenantState tenantState;
  private final MutableUserState userState;

  public TenantEntityRemovedApplier(
      final MutableTenantState tenantState, final MutableUserState userState) {
    this.tenantState = tenantState;
    this.userState = userState;
  }

  @Override
  public void applyState(final long key, final TenantRecord tenant) {
    tenantState.removeEntity(tenant.getTenantKey(), tenant.getEntityKey());
    switch (tenant.getEntityType()) {
      case USER -> userState.removeTenant(tenant.getEntityKey(), tenant.getTenantId());
      case MAPPING ->
          throw new UnsupportedOperationException(
              String.format(
                  "Expected to remove entity with key %d and type %s from tenant %s, but type %s is not supported.",
                  tenant.getEntityKey(),
                  tenant.getEntityType(),
                  tenant.getTenantId(),
                  tenant.getEntityType()));
      default ->
          throw new IllegalStateException(
              String.format(
                  "Unknown or unsupported entity type: '%s' for tenant '%s'. Please contact support for clarification.",
                  tenant.getEntityType(), tenant.getTenantId()));
    }
  }
}
