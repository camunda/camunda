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
import io.camunda.zeebe.engine.state.mutable.MutableGroupState;
import io.camunda.zeebe.engine.state.mutable.MutableMappingState;
import io.camunda.zeebe.engine.state.mutable.MutableMembershipState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;

public class GroupEntityAddedApplier implements TypedEventApplier<GroupIntent, GroupRecord> {

  private final MutableGroupState groupState;
  private final MutableMappingState mappingState;
  private final MutableMembershipState membershipState;

  public GroupEntityAddedApplier(final MutableProcessingState processingState) {
    groupState = processingState.getGroupState();
    mappingState = processingState.getMappingState();
    membershipState = processingState.getMembershipState();
  }

  @Override
  public void applyState(final long key, final GroupRecord value) {
    // TODO: refactor this with https://github.com/camunda/camunda/issues/30092 and
    // https://github.com/camunda/camunda/issues/30091
    final var groupKey = Long.parseLong(value.getGroupId());

    final var entityId = value.getEntityId();
    final var entityType = value.getEntityType();
    switch (entityType) {
      case USER ->
          membershipState.insertRelation(
              EntityType.USER,
              entityId,
              RelationType.GROUP,
              value.getGroupId());
      case MAPPING -> {
        groupState.addEntity(value);
        mappingState.addGroup(entityId, groupKey);
      }
      default ->
          throw new IllegalStateException(
              String.format(
                  "Expected to add entity '%s' to group '%d', but entities of type '%s' cannot be added to groups.",
                  entityId, groupKey, entityType));
    }
  }
}
