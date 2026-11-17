/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.engine.state.immutable.MembershipState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.RoleState;
import io.camunda.zeebe.engine.state.immutable.TenantState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import org.jspecify.annotations.NullMarked;

/**
 * Removes an entity's own memberships in roles and tenants, with the events that the unassign
 * commands write, so that the exporters remove them from secondary storage too.
 *
 * <p>Memberships are keyed by the entity ID. A group or role that is deleted, or that is created
 * with the ID of an entity whose memberships a deletion left behind, must not keep them.
 */
@NullMarked
final class EntityMembershipsRemover {

  private final MembershipState membershipState;
  private final RoleState roleState;
  private final TenantState tenantState;
  private final StateWriter stateWriter;

  EntityMembershipsRemover(final ProcessingState processingState, final StateWriter stateWriter) {
    membershipState = processingState.getMembershipState();
    roleState = processingState.getRoleState();
    tenantState = processingState.getTenantState();
    this.stateWriter = stateWriter;
  }

  /**
   * Removes the entity from every role and tenant it is a member of. A role or tenant that a
   * membership points to always exists, because deleting a role or tenant removes its members.
   *
   * @return whether a membership was removed
   */
  boolean removeRoleAndTenantMemberships(final EntityType entityType, final String entityId) {
    boolean removed = false;
    for (final var roleId :
        membershipState.getMemberships(entityType, entityId, RelationType.ROLE)) {
      final var role = roleState.getRole(roleId).orElseThrow();
      stateWriter.appendFollowUpEvent(
          role.getRoleKey(),
          RoleIntent.ENTITY_REMOVED,
          new RoleRecord()
              .setRoleKey(role.getRoleKey())
              .setRoleId(roleId)
              .setEntityId(entityId)
              .setEntityType(entityType));
      removed = true;
    }

    for (final var tenantId :
        membershipState.getMemberships(entityType, entityId, RelationType.TENANT)) {
      final var tenant = tenantState.getTenantById(tenantId).orElseThrow();
      stateWriter.appendFollowUpEvent(
          tenant.getTenantKey(),
          TenantIntent.ENTITY_REMOVED,
          new TenantRecord().setTenantId(tenantId).setEntityId(entityId).setEntityType(entityType));
      removed = true;
    }
    return removed;
  }
}
