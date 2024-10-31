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
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;

public class GroupCreatedApplier implements TypedEventApplier<GroupIntent, GroupRecord> {

  private final MutableGroupState groupState;
  private final MutableAuthorizationState authorizationState;

  public GroupCreatedApplier(
      final MutableGroupState groupState, final MutableAuthorizationState authorizationState) {
    this.groupState = groupState;
    this.authorizationState = authorizationState;
  }

  @Override
  public void applyState(final long key, final GroupRecord value) {
    groupState.createGroup(key, value);
    authorizationState.insertOwnerTypeByKey(key, AuthorizationOwnerType.GROUP);
  }
}
