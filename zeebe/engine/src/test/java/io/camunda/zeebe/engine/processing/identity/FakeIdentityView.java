/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.HashSet;
import java.util.Set;

/** Identity data for validator tests, without a state. */
final class FakeIdentityView implements IdentityView {

  private final Set<String> tenants = new HashSet<>();
  private final Set<String> roles = new HashSet<>();
  private final Set<String> groups = new HashSet<>();
  private final Set<String> users = new HashSet<>();
  private final Set<String> mappingRules = new HashSet<>();
  private final Set<String> relations = new HashSet<>();

  String addTenant(final String tenantId) {
    tenants.add(tenantId);
    return tenantId;
  }

  String addRole(final String roleId) {
    roles.add(roleId);
    return roleId;
  }

  String addGroup(final String groupId) {
    groups.add(groupId);
    return groupId;
  }

  String addUser(final String username) {
    users.add(username);
    return username;
  }

  String addMappingRule(final String mappingRuleId) {
    mappingRules.add(mappingRuleId);
    return mappingRuleId;
  }

  void addRelation(
      final RelationType relationType,
      final String relationId,
      final EntityType entityType,
      final String entityId) {
    relations.add(relationKey(entityType, entityId, relationType, relationId));
  }

  @Override
  public boolean tenantExists(final String tenantId) {
    return tenants.contains(tenantId);
  }

  @Override
  public boolean roleExists(final String roleId) {
    return roles.contains(roleId);
  }

  @Override
  public boolean groupExists(final String groupId) {
    return groups.contains(groupId);
  }

  @Override
  public boolean userExists(final String username) {
    return users.contains(username);
  }

  @Override
  public boolean mappingRuleExists(final String mappingRuleId) {
    return mappingRules.contains(mappingRuleId);
  }

  @Override
  public boolean hasRelation(
      final EntityType entityType,
      final String entityId,
      final RelationType relationType,
      final String relationId) {
    return relations.contains(relationKey(entityType, entityId, relationType, relationId));
  }

  private static String relationKey(
      final EntityType entityType,
      final String entityId,
      final RelationType relationType,
      final String relationId) {
    return relationType + "/" + relationId + "/" + entityType + "/" + entityId;
  }
}
