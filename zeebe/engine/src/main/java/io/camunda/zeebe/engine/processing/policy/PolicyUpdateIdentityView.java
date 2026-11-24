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
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.NullMarked;

/**
 * The identity data as it will be after the commands of a policy update that were applied so far:
 * the state, plus the roles, groups and mapping rules that these commands create or delete.
 * Validating the commands one by one against this view accepts a command that depends on an earlier
 * command of the same update, e.g. an authorization for a role that the update creates.
 */
@NullMarked
final class PolicyUpdateIdentityView implements IdentityView {

  private final IdentityView state;
  private final Map<String, Boolean> roles = new HashMap<>();
  private final Map<String, Boolean> groups = new HashMap<>();
  private final Map<String, Boolean> mappingRules = new HashMap<>();

  PolicyUpdateIdentityView(final IdentityView state) {
    this.state = state;
  }

  /** Records the changes of a command that was validated, so the following commands see them. */
  void apply(final IdentityCommand command) {
    switch (command.value()) {
      case final RoleRecord role -> {
        switch ((RoleIntent) command.intent()) {
          case CREATE -> roles.put(role.getRoleId(), true);
          case DELETE -> roles.put(role.getRoleId(), false);
          default -> {}
        }
      }
      case final GroupRecord group -> {
        switch ((GroupIntent) command.intent()) {
          case CREATE -> groups.put(group.getGroupId(), true);
          case DELETE -> groups.put(group.getGroupId(), false);
          default -> {}
        }
      }
      case final MappingRuleRecord rule -> {
        switch ((MappingRuleIntent) command.intent()) {
          case CREATE -> mappingRules.put(rule.getMappingRuleId(), true);
          case DELETE -> mappingRules.put(rule.getMappingRuleId(), false);
          default -> {}
        }
      }
      // the other commands change nothing that the view covers
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
    return state.hasRelation(entityType, entityId, relationType, relationId);
  }
}
