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
import io.camunda.zeebe.engine.state.mutable.MutableMembershipState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;

public class RoleEntityRemovedApplier implements TypedEventApplier<RoleIntent, RoleRecord> {

  private final MutableMembershipState membershipState;

  public RoleEntityRemovedApplier(final MutableProcessingState state) {
    membershipState = state.getMembershipState();
  }

  @Override
  public void applyState(final long key, final RoleRecord value) {
    membershipState.deleteRelation(
        value.getEntityType(), value.getEntityId(), RelationType.ROLE, value.getRoleId());
  }
}
