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
    tenantState.removeEntity(tenant.getEntityKey(), tenant.getEntityKey());
    switch (tenant.getEntityType()) {
      case USER -> userState.removeTenant(tenant.getEntityKey(), tenant.getTenantId());
      case MAPPING ->
          throw new UnsupportedOperationException("MAPPING entity type is not implemented yet.");
      default ->
          throw new IllegalStateException(
              "Unknown or unsupported entity type: '"
                  + tenant.getEntityType()
                  + "'. Please contact support for clarification.");
    }
  }
}
