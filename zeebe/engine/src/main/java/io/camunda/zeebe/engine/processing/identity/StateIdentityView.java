/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.record.value.EntityType;
import org.jspecify.annotations.NullMarked;

/** Reads the identity data from the state, as the identity processors see it. */
@NullMarked
public final class StateIdentityView implements IdentityView {

  private final ProcessingState state;

  public StateIdentityView(final ProcessingState state) {
    this.state = state;
  }

  @Override
  public boolean roleExists(final String roleId) {
    return state.getRoleState().getRole(roleId).isPresent();
  }

  @Override
  public boolean groupExists(final String groupId) {
    return state.getGroupState().get(groupId).isPresent();
  }

  @Override
  public boolean userExists(final String username) {
    return state.getUserState().getUser(username).isPresent();
  }

  @Override
  public boolean mappingRuleExists(final String mappingRuleId) {
    return state.getMappingRuleState().get(mappingRuleId).isPresent();
  }

  @Override
  public boolean hasRelation(
      final EntityType entityType,
      final String entityId,
      final RelationType relationType,
      final String relationId) {
    return state.getMembershipState().hasRelation(entityType, entityId, relationType, relationId);
  }
}
