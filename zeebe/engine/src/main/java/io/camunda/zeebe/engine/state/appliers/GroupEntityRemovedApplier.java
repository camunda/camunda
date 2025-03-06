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
    final var entityKey = value.getEntityKey();
    final var entityType = value.getEntityType();

    // get the record key from the GroupRecord, as the key argument
    // may belong to the distribution command
    final var groupKey = value.getGroupKey();
    groupState.removeEntity(groupKey, entityKey);

    switch (entityType) {
      case USER ->
          userState
              .getUser(entityKey)
              .ifPresent(user -> userState.removeGroup(user.getUsername(), key));
      case MAPPING -> mappingState.removeGroup(String.valueOf(entityKey), key);
      default ->
          throw new IllegalStateException(
              String.format(
                  "Expected to remove entity '%d' from group '%d', but entities of type '%s' cannot be removed from groups.",
                  entityKey, groupKey, entityType));
    }
  }
}
