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
import io.camunda.zeebe.engine.state.mutable.MutableRoleState;
import io.camunda.zeebe.engine.state.mutable.MutableUserState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;

public class RoleDeletedApplier implements TypedEventApplier<RoleIntent, RoleRecord> {

  private final MutableRoleState roleState;
  private final MutableUserState userState;
  private final MutableAuthorizationState authorizationState;

  public RoleDeletedApplier(
      final MutableRoleState roleState,
      final MutableUserState userState,
      final MutableAuthorizationState authorizationState) {
    this.roleState = roleState;
    this.userState = userState;
    this.authorizationState = authorizationState;
  }

  @Override
  public void applyState(final long key, final RoleRecord value) {
    final var roleKey = value.getRoleKey();
    final var entities = roleState.getEntitiesByType(roleKey);
    // Remove roles from users if EntityType.USER exists
    final var userEntities = entities.get(EntityType.USER);
    if (userEntities != null) {
      userEntities.forEach(userKey -> userState.removeRole(userKey, roleKey));
    }
    // todo remove entity from mapping state
    // delete role from authorization state
    authorizationState.deleteAuthorizationsByOwnerKeyPrefix(roleKey);
    authorizationState.deleteOwnerTypeByKey(roleKey);
    roleState.delete(value);
  }
}
