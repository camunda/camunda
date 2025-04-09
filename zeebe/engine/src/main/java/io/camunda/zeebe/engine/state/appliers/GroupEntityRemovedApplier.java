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
import io.camunda.zeebe.engine.state.mutable.MutableUserState;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;

public class GroupEntityRemovedApplier implements TypedEventApplier<GroupIntent, GroupRecord> {

  private final MutableGroupState groupState;
  private final MutableUserState userState;
  private final MutableMappingState mappingState;

  public GroupEntityRemovedApplier(final MutableProcessingState processingState) {
    groupState = processingState.getGroupState();
    userState = processingState.getUserState();
    mappingState = processingState.getMappingState();
  }

  @Override
  public void applyState(final long key, final GroupRecord value) {
    final var groupId = value.getGroupId();
    final var entityId = value.getEntityId();
    final var entityType = value.getEntityType();
    groupState.removeEntity(groupId, entityId);

    switch (entityType) {
      case USER -> userState.removeGroup(entityId, groupId);
      case MAPPING -> mappingState.removeGroup(entityId, groupId);
      default ->
          throw new IllegalStateException(
              String.format(
                  "Expected to remove entity '%s' from group with ID '%s', but entities of type '%s' cannot be removed from groups.",
                  entityId, groupId, entityType));
    }
  }
}
