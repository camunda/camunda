/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

import io.camunda.zeebe.engine.processing.identity.IdentityView;
import io.camunda.zeebe.engine.processing.policy.PolicyUpdateCommandMapper.IdentityCommand;
import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The identity data as it will be after the commands of a policy update that were applied so far:
 * the state, plus the changes of these commands. Validating the commands one by one against this
 * view accepts a command that depends on an earlier command of the same update, e.g. a member added
 * to a role that the update creates.
 *
 * <p>A delete also removes the memberships of the deleted entity, as the delete processors do: its
 * members, and its own memberships in other entities. The entity deletes come last within a chunk,
 * but a later chunk can still refer to these memberships. The authorizations of a deleted entity
 * are not tracked, since the view has none.
 */
@NullMarked
final class PolicyUpdateIdentityView implements IdentityView {

  private final IdentityView state;
  private final Map<String, Boolean> roles = new HashMap<>();
  private final Map<String, Boolean> groups = new HashMap<>();
  private final Map<String, Boolean> mappingRules = new HashMap<>();
  private final Map<Relation, Boolean> relations = new HashMap<>();
  private final Set<Owner> deletedOwners = new HashSet<>();
  private final Set<Member> deletedMembers = new HashSet<>();

  PolicyUpdateIdentityView(final IdentityView state) {
    this.state = state;
  }

  /** Records the changes of a command that was validated, so the following commands see them. */
  void apply(final IdentityCommand command) {
    switch (command.value()) {
      case final RoleRecord role -> {
        final var relation =
            new Relation(
                role.getEntityType(), role.getEntityId(), RelationType.ROLE, role.getRoleId());
        switch ((RoleIntent) command.intent()) {
          case CREATE -> roles.put(role.getRoleId(), true);
          case DELETE -> {
            roles.put(role.getRoleId(), false);
            removeMemberships(RelationType.ROLE, EntityType.ROLE, role.getRoleId());
          }
          case ADD_ENTITY -> relations.put(relation, true);
          case REMOVE_ENTITY -> relations.put(relation, false);
          default -> {}
        }
      }
      case final GroupRecord group -> {
        final var relation =
            new Relation(
                group.getEntityType(), group.getEntityId(), RelationType.GROUP, group.getGroupId());
        switch ((GroupIntent) command.intent()) {
          case CREATE -> groups.put(group.getGroupId(), true);
          case DELETE -> {
            groups.put(group.getGroupId(), false);
            removeMemberships(RelationType.GROUP, EntityType.GROUP, group.getGroupId());
          }
          case ADD_ENTITY -> relations.put(relation, true);
          case REMOVE_ENTITY -> relations.put(relation, false);
          default -> {}
        }
      }
      case final TenantRecord tenant -> {
        final var relation =
            new Relation(
                tenant.getEntityType(),
                tenant.getEntityId(),
                RelationType.TENANT,
                tenant.getTenantId());
        switch ((TenantIntent) command.intent()) {
          case DELETE -> removeMemberships(RelationType.TENANT, null, tenant.getTenantId());
          case ADD_ENTITY -> relations.put(relation, true);
          case REMOVE_ENTITY -> relations.put(relation, false);
          default -> {}
        }
      }
      case final MappingRuleRecord rule -> {
        switch ((MappingRuleIntent) command.intent()) {
          case CREATE -> mappingRules.put(rule.getMappingRuleId(), true);
          case DELETE -> {
            mappingRules.put(rule.getMappingRuleId(), false);
            removeMemberships(null, EntityType.MAPPING_RULE, rule.getMappingRuleId());
          }
          default -> {}
        }
      }
      // authorizations are not part of the view
      default -> {}
    }
  }

  @Override
  public boolean roleExists(final String roleId) {
    final var changed = roles.get(roleId);
    return changed != null ? changed : state.roleExists(roleId);
  }

  @Override
  public boolean groupExists(final String groupId) {
    final var changed = groups.get(groupId);
    return changed != null ? changed : state.groupExists(groupId);
  }

  @Override
  public boolean userExists(final String username) {
    return state.userExists(username);
  }

  @Override
  public boolean mappingRuleExists(final String mappingRuleId) {
    final var changed = mappingRules.get(mappingRuleId);
    return changed != null ? changed : state.mappingRuleExists(mappingRuleId);
  }

  @Override
  public boolean hasRelation(
      final EntityType entityType,
      final String entityId,
      final RelationType relationType,
      final String relationId) {
    final var changed = relations.get(new Relation(entityType, entityId, relationType, relationId));
    if (changed != null) {
      return changed;
    }
    if (deletedOwners.contains(new Owner(relationType, relationId))
        || deletedMembers.contains(new Member(entityType, entityId))) {
      return false;
    }
    return state.hasRelation(entityType, entityId, relationType, relationId);
  }

  /**
   * Removes the memberships of a deleted entity: its members, if it can have members, and its own
   * memberships, if it can be a member. An entity that is created again after the delete starts
   * without memberships, until a later command adds them.
   */
  private void removeMemberships(
      final @Nullable RelationType asOwner,
      final @Nullable EntityType asMember,
      final String entityId) {
    if (asOwner != null) {
      deletedOwners.add(new Owner(asOwner, entityId));
      relations
          .keySet()
          .removeIf(
              relation ->
                  relation.relationType() == asOwner && relation.relationId().equals(entityId));
    }
    if (asMember != null) {
      deletedMembers.add(new Member(asMember, entityId));
      relations
          .keySet()
          .removeIf(
              relation ->
                  relation.entityType() == asMember && relation.entityId().equals(entityId));
    }
  }

  private record Relation(
      EntityType entityType, String entityId, RelationType relationType, String relationId) {}

  /** An entity that has members, e.g. the role of a role membership. */
  private record Owner(RelationType relationType, String relationId) {}

  /** An entity that is a member, e.g. the group of a role membership. */
  private record Member(EntityType entityType, String entityId) {}
}
