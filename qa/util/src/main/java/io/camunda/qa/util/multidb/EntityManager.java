/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.multidb;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.qa.util.auth.Membership;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestGroup;
import io.camunda.qa.util.auth.TestMappingRule;
import io.camunda.qa.util.auth.TestRole;
import io.camunda.qa.util.auth.TestUser;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class EntityManager {

  final Map<String, TestUser> users = new ConcurrentHashMap<>();
  final Map<String, TestGroup> groups = new ConcurrentHashMap<>();
  final Map<String, TestRole> roles = new ConcurrentHashMap<>();
  final Map<String, TestMappingRule> mappingRules = new ConcurrentHashMap<>();
  private final CamundaClient defaultClient;

  public EntityManager(final CamundaClient defaultClient) {
    this.defaultClient = defaultClient;
  }

  public EntityManager withUser(final List<TestUser> users) {
    users.stream()
        .filter(user -> !this.users.containsKey(user.username()))
        .forEach(
            user -> {
              this.users.put(user.username(), user);
              defaultClient
                  .newCreateUserCommand()
                  .username(user.username())
                  .password(user.password())
                  .name(user.username())
                  .email("%s@example.com".formatted(user.username()))
                  .send()
                  .join();
              addPermissions(user.username(), OwnerType.USER, user.permissions());
            });
    return this;
  }

  public EntityManager withGroups(final List<TestGroup> groups) {
    groups.stream()
        .filter(group -> !this.groups.containsKey(group.id()))
        .forEach(
            group -> {
              this.groups.put(group.id(), group);
              defaultClient
                  .newCreateGroupCommand()
                  .groupId(group.id())
                  .name(group.name())
                  .description(group.id())
                  .send()
                  .join();
              addPermissions(group.id(), OwnerType.GROUP, group.permissions());
              addGroupMemberships(group.id(), group.memberships());
            });
    return this;
  }

  public EntityManager withRoles(final List<TestRole> roles) {
    roles.stream()
        .filter(role -> !this.roles.containsKey(role.id()))
        .forEach(
            role -> {
              this.roles.put(role.id(), role);
              defaultClient
                  .newCreateRoleCommand()
                  .roleId(role.id())
                  .name(role.id())
                  .description(role.id())
                  .send()
                  .join();
              addPermissions(role.id(), OwnerType.ROLE, role.permissions());
              addRoleMemberships(role.id(), role.memberships());
            });
    return this;
  }

  public EntityManager withMappingRules(final List<TestMappingRule> mappingRules) {
    mappingRules.stream()
        .filter(mappingRule -> !this.mappingRules.containsKey(mappingRule.id()))
        .forEach(
            mappingRule -> {
              this.mappingRules.put(mappingRule.id(), mappingRule);
              defaultClient
                  .newCreateMappingRuleCommand()
                  .mappingRuleId(mappingRule.id())
                  .claimName(mappingRule.claimName())
                  .claimValue(mappingRule.claimValue())
                  .name(mappingRule.id())
                  .send()
                  .join();
            });
    return this;
  }

  private void addPermissions(
      final String ownerId, final OwnerType ownerType, final List<Permissions> permissions) {
    permissions.forEach(
        permission ->
            permission
                .resourceIds()
                .forEach(
                    resourceId ->
                        defaultClient
                            .newCreateAuthorizationCommand()
                            .ownerId(ownerId)
                            .ownerType(ownerType)
                            .resourceId(resourceId)
                            .resourceType(permission.resourceType())
                            .permissionTypes(permission.permissionType())
                            .send()
                            .join()));
  }

  private void addGroupMemberships(final String groupId, final List<Membership> memberships) {
    memberships.forEach(
        membership -> {
          final var entityType = membership.entityType();
          switch (entityType) {
            case USER:
              defaultClient
                  .newAssignUserToGroupCommand()
                  .username(membership.memberId())
                  .groupId(groupId)
                  .send()
                  .join();
              break;
            case MAPPING_RULE:
              defaultClient
                  .newAssignMappingRuleToGroupCommand()
                  .mappingRuleId(membership.memberId())
                  .groupId(groupId)
                  .send()
                  .join();
              break;
            default:
              throw new IllegalArgumentException("Unsupported entity type: " + entityType);
          }
        });
  }

  private void addRoleMemberships(final String roleId, final List<Membership> memberships) {
    memberships.forEach(
        membership -> {
          final var entityType = membership.entityType();
          switch (entityType) {
            case USER:
              defaultClient
                  .newAssignRoleToUserCommand()
                  .roleId(roleId)
                  .username(membership.memberId())
                  .send()
                  .join();
              break;
            case GROUP:
              defaultClient
                  .newAssignRoleToGroupCommand()
                  .roleId(roleId)
                  .groupId(membership.memberId())
                  .send()
                  .join();
              break;
            case MAPPING_RULE:
              defaultClient
                  .newAssignRoleToMappingRuleCommand()
                  .roleId(roleId)
                  .mappingRuleId(membership.memberId())
                  .send()
                  .join();
              break;
            default:
              throw new IllegalArgumentException("Unsupported entity type: " + entityType);
          }
        });
  }

  /** Will wait until all entities are created and permissions are assigned. */
  public void await() {
    // TODO replace with proper search queries when they are implemented
    try {
      Thread.sleep(5000);
    } catch (final InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
