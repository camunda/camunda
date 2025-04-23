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
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;

public class GroupEntityAddedApplier implements TypedEventApplier<GroupIntent, GroupRecord> {
  private final MutableMembershipState membershipState;

  public GroupEntityAddedApplier(final MutableProcessingState processingState) {
    membershipState = processingState.getMembershipState();
  }

  @Override
  public void applyState(final long key, final GroupRecord value) {
    final var entityId = value.getEntityId();
    final var entityType = value.getEntityType();
    final var groupId = value.getGroupId();

    switch (entityType) {
      case USER, MAPPING ->
          membershipState.insertRelation(entityType, entityId, RelationType.GROUP, groupId);
      default ->
          throw new IllegalStateException(
              String.format(
                  "Expected to add entity '%s' to group '%s', but entities of type '%s' cannot be added to groups.",
                  entityId, groupId, entityType));
    }
  }
}
