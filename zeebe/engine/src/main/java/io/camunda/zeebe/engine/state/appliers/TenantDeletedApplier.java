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
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;

public class TenantDeletedApplier implements TypedEventApplier<TenantIntent, TenantRecord> {
  private final MutableTenantState tenantState;
  private final MutableAuthorizationState authorizationState;

  public TenantDeletedApplier(
      final MutableTenantState tenantState, final MutableAuthorizationState authorizationState) {
    this.tenantState = tenantState;
    this.authorizationState = authorizationState;
  }

  @Override
  public void applyState(final long key, final TenantRecord tenantRecord) {
    final var tenantKey = tenantRecord.getTenantKey();
    final var tenantId = tenantRecord.getTenantId();
    deleteTenantAuthorizations(tenantKey, tenantId);
    tenantState.delete(tenantRecord);
  }

  private void deleteTenantAuthorizations(final long tenantKey, final String tenantId) {
    authorizationState.deleteAuthorizationsByOwnerTypeAndIdPrefix(
        AuthorizationOwnerType.TENANT, tenantId);
    authorizationState.deleteOwnerTypeByKey(tenantKey);
  }
}
