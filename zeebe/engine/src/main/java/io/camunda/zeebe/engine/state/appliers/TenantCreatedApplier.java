/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.engine.state.immutable.MembershipState;
import io.camunda.zeebe.engine.state.mutable.MutableMembershipState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableTenantState;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.DefaultRole;
import io.camunda.zeebe.protocol.record.value.EntityType;

public class TenantCreatedApplier implements TypedEventApplier<TenantIntent, TenantRecord> {

  private final MutableTenantState tenantState;
  private final MembershipState membershipState;
  private final MutableMembershipState mutableMembershipState;


  public TenantCreatedApplier(final MutableProcessingState state) {
    this.tenantState = state.getTenantState();
    this.membershipState = state.getMembershipState();
    this.mutableMembershipState = state.getMembershipState();
  }

  @Override
  public void applyState(final long key, final TenantRecord value) {
   tenantState.createTenant(value);
   membershipState.forEachMember(
        RelationType.ROLE,
        DefaultRole.ADMIN.getId(),
        (entityType, entityId) -> {
          if (entityType == EntityType.USER) {
            mutableMembershipState.insertRelation(
                entityType, entityId, RelationType.TENANT, value.getTenantId());
          }
        });
  }
}
