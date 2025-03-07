/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableMappingState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableRoleState;
import io.camunda.zeebe.engine.state.mutable.MutableUserState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;

public class RoleEntityRemovedApplier implements TypedEventApplier<RoleIntent, RoleRecord> {

  private final MutableRoleState roleState;
  private final MutableUserState userState;
  private final MutableMappingState mappingState;

  public RoleEntityRemovedApplier(final MutableProcessingState state) {
    roleState = state.getRoleState();
    userState = state.getUserState();
    mappingState = state.getMappingState();
  }

  @Override
  public void applyState(final long key, final RoleRecord value) {
    roleState.removeEntity(value.getRoleKey(), value.getEntityKey());
    switch (value.getEntityType()) {
      case USER ->
          userState
              .getUser(value.getEntityKey())
              .ifPresent(user -> userState.removeRole(user.getUsername(), value.getRoleKey()));
      case MAPPING ->
          mappingState.removeRole(String.valueOf(value.getEntityKey()), value.getRoleKey());
      default ->
          throw new IllegalStateException(
              String.format(
                  "Expected to remove entity '%d' from role '%d', but entities of type '%s' cannot be removed from roles",
                  value.getEntityKey(), value.getRoleKey(), value.getEntityType()));
    }
  }
}
