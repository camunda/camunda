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
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;

public class GroupDeletedApplier implements TypedEventApplier<GroupIntent, GroupRecord> {

  private final MutableGroupState groupState;
  private final MutableAuthorizationState authorizationState;

  public GroupDeletedApplier(final MutableProcessingState processingState) {
    groupState = processingState.getGroupState();
    authorizationState = processingState.getAuthorizationState();
  }

  @Override
  public void applyState(final long key, final GroupRecord value) {
    // get the record key from the GroupRecord, as the key argument
    // may belong to the distribution command
    final var groupKey = value.getGroupKey();

    // delete group from authorization state
    authorizationState.deleteAuthorizationsByOwnerKeyPrefix(groupKey);
    authorizationState.deleteOwnerTypeByKey(groupKey);

    // delete group from group state
    groupState.delete(groupKey);
  }
}
