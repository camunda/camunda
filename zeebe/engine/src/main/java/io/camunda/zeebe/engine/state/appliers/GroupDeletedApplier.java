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
import io.camunda.zeebe.engine.state.mutable.MutableGroupState;
import io.camunda.zeebe.engine.state.mutable.MutableMappingState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableUserState;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.function.BiConsumer;

public class GroupDeletedApplier implements TypedEventApplier<GroupIntent, GroupRecord> {

  private final MutableGroupState groupState;
  private final MutableUserState userState;
  private final MutableMappingState mappingState;
  private final MutableAuthorizationState authorizationState;

  public GroupDeletedApplier(final MutableProcessingState processingState) {
    groupState = processingState.getGroupState();
    userState = processingState.getUserState();
    mappingState = processingState.getMappingState();
    authorizationState = processingState.getAuthorizationState();
  }

  @Override
  public void applyState(final long key, final GroupRecord value) {

    // delete group key from entity states (user, mapping)
    final var entitiesByTypeMap = groupState.getEntitiesByType(key);
    entitiesByTypeMap.forEach(
        (entityType, entityKeys) -> {
          final var removalConsumer = getRemovalFunction(entityType, key, value);
          entityKeys.forEach(entityKey -> removalConsumer.accept(entityKey, key));
        });

    // delete group from authorization state
    authorizationState.deleteAuthorizationsByOwnerKeyPrefix(key);
    authorizationState.deleteOwnerTypeByKey(key);

    // delete group from group state
    groupState.delete(key);
  }

  private BiConsumer<Long, Long> getRemovalFunction(
      final EntityType entityType, final long groupKey, final GroupRecord groupRecord) {
    return switch (entityType) {
      case EntityType.USER -> userState::removeGroup;
      case EntityType.MAPPING -> mappingState::removeGroup;
      default ->
          throw new IllegalStateException(
              String.format(
                  "Expected to remove entity '%d' for group '%d', but entities of type '%s' are not supported by groups.",
                  groupRecord.getEntityKey(), groupKey, groupRecord.getEntityType()));
    };
  }
}
