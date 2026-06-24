/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.adapter;

import io.camunda.security.core.auth.MappingRuleMatcher;
import io.camunda.security.core.port.out.MembershipPort;
import io.camunda.security.core.port.out.MembershipQuery;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.engine.state.authorization.PersistedMappingRule;
import io.camunda.zeebe.engine.state.immutable.MappingRuleState;
import io.camunda.zeebe.engine.state.immutable.MembershipState;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class MembershipStateAdapter implements MembershipPort {

  private final MappingRuleState mappingRuleState;
  private final MembershipState membershipState;

  public MembershipStateAdapter(
      final MappingRuleState mappingRuleState, final MembershipState membershipState) {
    this.mappingRuleState = mappingRuleState;
    this.membershipState = membershipState;
  }

  @Override
  public List<String> mappingRuleIds(final MembershipQuery query) {
    @SuppressWarnings("unchecked")
    final var tokenClaims =
        (Map<String, Object>)
            query.tokenClaims().getOrDefault(Authorization.USER_TOKEN_CLAIMS, Map.of());
    return MappingRuleMatcher.matchingRules(mappingRuleState.getAll().stream(), tokenClaims)
        .map(PersistedMappingRule::getMappingRuleId)
        .toList();
  }

  @Override
  public List<String> groupIds(final MembershipQuery query) {
    @SuppressWarnings("unchecked")
    final var groupsClaims =
        (List<String>) query.tokenClaims().get(Authorization.USER_GROUPS_CLAIMS);
    if (groupsClaims != null) {
      return groupsClaims;
    }
    return membershipState.getMemberships(
        toEntityType(query.principalType()), query.principalId(), RelationType.GROUP);
  }

  @Override
  public List<String> roleIds(final MembershipQuery query) {
    final var result = new LinkedHashSet<String>();
    membershipState
        .getMemberships(toEntityType(query.principalType()), query.principalId(), RelationType.ROLE)
        .forEach(result::add);
    query
        .resolvedGroupIds()
        .forEach(
            groupId ->
                membershipState
                    .getMemberships(EntityType.GROUP, groupId, RelationType.ROLE)
                    .forEach(result::add));
    return new ArrayList<>(result);
  }

  @Override
  public List<String> tenantIds(final MembershipQuery query) {
    final var result = new LinkedHashSet<String>();
    final var entityType = toEntityType(query.principalType());
    membershipState
        .getMemberships(entityType, query.principalId(), RelationType.TENANT)
        .forEach(result::add);
    query
        .resolvedRoleIds()
        .forEach(
            roleId ->
                membershipState
                    .getMemberships(EntityType.ROLE, roleId, RelationType.TENANT)
                    .forEach(result::add));
    query
        .resolvedGroupIds()
        .forEach(
            groupId -> {
              membershipState
                  .getMemberships(EntityType.GROUP, groupId, RelationType.TENANT)
                  .forEach(result::add);
              membershipState.getMemberships(EntityType.GROUP, groupId, RelationType.ROLE).stream()
                  .flatMap(
                      roleId ->
                          membershipState
                              .getMemberships(EntityType.ROLE, roleId, RelationType.TENANT)
                              .stream())
                  .forEach(result::add);
            });
    return new ArrayList<>(result);
  }

  private EntityType toEntityType(final PrincipalType principalType) {
    return switch (principalType) {
      case USER -> EntityType.USER;
      case CLIENT -> EntityType.CLIENT;
    };
  }
}
