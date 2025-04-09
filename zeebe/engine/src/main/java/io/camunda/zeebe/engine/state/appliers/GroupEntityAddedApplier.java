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

public class GroupEntityAddedApplier implements TypedEventApplier<GroupIntent, GroupRecord> {

  private final MutableGroupState groupState;
  private final MutableUserState userState;
  private final MutableMappingState mappingState;

  public GroupEntityAddedApplier(final MutableProcessingState processingState) {
    groupState = processingState.getGroupState();
    userState = processingState.getUserState();
    mappingState = processingState.getMappingState();
  }

  @Override
  public void applyState(final long key, final GroupRecord value) {
    // TODO: refactor this with https://github.com/camunda/camunda/issues/30092 and
    // https://github.com/camunda/camunda/issues/30091
    final var groupKey = Long.parseLong(value.getGroupId());
    groupState.addEntity(value);

    final var entityId = value.getEntityId();
    final var entityType = value.getEntityType();
    switch (entityType) {
      case USER ->
          userState
              .getUser(entityId)
              .ifPresent(user -> userState.addGroup(user.getUsername(), groupKey));
      case MAPPING -> mappingState.addGroup(Long.parseLong(entityId), groupKey);
      default ->
          throw new IllegalStateException(
              String.format(
                  "Expected to add entity '%d' to group '%d', but entities of type '%s' cannot be added to groups.",
                  entityId, groupKey, entityType));
    }
  }
}
