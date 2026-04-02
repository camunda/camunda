/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.multidb;

import io.camunda.qa.util.auth.Membership;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestClient;
import io.camunda.qa.util.auth.TestGroup;
import io.camunda.qa.util.auth.TestMappingRule;
import io.camunda.qa.util.auth.TestRole;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.multidb.TestEntityCollector.TestEntityCollection;
import io.camunda.security.configuration.ConfiguredAuthorization;
import io.camunda.security.configuration.ConfiguredGroup;
import io.camunda.security.configuration.ConfiguredMappingRule;
import io.camunda.security.configuration.ConfiguredRole;
import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.NonNull;

public class TestEntityConfigurer {

  public ConfigurationTestEntities configure(final TestEntityCollection entities) {
    return new ConfigurationTestEntities(
        configuredUsers(entities.users()),
        configuredGroups(entities.groups()),
        configuredRoles(entities.roles()),
        configuredMappingRules(entities.mappingRules()),
        configuredAuthorizations(
            entities.users(),
            entities.mappingRules(),
            entities.clients(),
            entities.groups(),
            entities.roles()));
  }

  private List<ConfiguredUser> configuredUsers(final List<TestUser> users) {
    return users.stream()
        .map(
            u ->
                new ConfiguredUser(
                    u.username(),
                    u.password(),
                    u.username(),
                    "%s@example.com".formatted(u.username())))
        .toList();
  }

  private List<ConfiguredMappingRule> configuredMappingRules(
      final List<TestMappingRule> testMappingRules) {
    return testMappingRules.stream()
        .map(m -> new ConfiguredMappingRule(m.id(), m.claimName(), m.claimValue()))
        .toList();
  }

  private List<ConfiguredGroup> configuredGroups(final List<TestGroup> testGroups) {
    return testGroups.stream().map(TestEntityConfigurer::configuredGroup).toList();
  }

  private static @NonNull ConfiguredGroup configuredGroup(final TestGroup g) {
    final List<String> users = typedMembers(g.memberships(), EntityType.USER);
    final List<String> clients = typedMembers(g.memberships(), EntityType.CLIENT);
    final List<String> mappingRules = typedMembers(g.memberships(), EntityType.MAPPING_RULE);
    final List<String> roles = typedMembers(g.memberships(), EntityType.ROLE);
    return new ConfiguredGroup(g.id(), g.name(), null, users, roles, mappingRules, clients);
  }

  private List<ConfiguredRole> configuredRoles(final List<TestRole> testRoles) {
    return testRoles.stream().map(TestEntityConfigurer::configuredRole).toList();
  }

  private static @NonNull ConfiguredRole configuredRole(final TestRole r) {
    final List<String> users = typedMembers(r.memberships(), EntityType.USER);
    final List<String> clients = typedMembers(r.memberships(), EntityType.CLIENT);
    final List<String> mappingRules = typedMembers(r.memberships(), EntityType.MAPPING_RULE);
    final List<String> groups = typedMembers(r.memberships(), EntityType.GROUP);
    return new ConfiguredRole(r.id(), r.name(), null, users, clients, mappingRules, groups);
  }

  private static @NonNull List<String> typedMembers(
      final List<Membership> memberships, final EntityType memberType) {
    return memberships.stream()
        .filter(m -> m.entityType() == memberType)
        .map(Membership::memberId)
        .toList();
  }

  private List<ConfiguredAuthorization> configuredAuthorizations(
      final List<TestUser> users,
      final List<TestMappingRule> mappingRules,
      final List<TestClient> clients,
      final List<TestGroup> groups,
      final List<TestRole> roles) {
    final var userAuths =
        configurePermissions(
            users, TestUser::username, TestUser::permissions, AuthorizationOwnerType.USER);
    final var clientAuths =
        configurePermissions(
            clients, TestClient::clientId, TestClient::permissions, AuthorizationOwnerType.CLIENT);
    final var groupAuths =
        configurePermissions(
            groups, TestGroup::id, TestGroup::permissions, AuthorizationOwnerType.GROUP);
    final var roleAuths =
        configurePermissions(
            roles, TestRole::id, TestRole::permissions, AuthorizationOwnerType.ROLE);
    final var mappingRuleAuths =
        configurePermissions(
            mappingRules,
            TestMappingRule::id,
            TestMappingRule::permissions,
            AuthorizationOwnerType.MAPPING_RULE);

    return Stream.of(userAuths, mappingRuleAuths, groupAuths, roleAuths, clientAuths)
        .flatMap(s -> s)
        .toList();
  }

  private <T> Stream<ConfiguredAuthorization> configurePermissions(
      final List<T> entities,
      final Function<T, String> getOwnerId,
      final Function<T, List<Permissions>> getPermissions,
      final AuthorizationOwnerType ownerType) {
    return entities.stream()
        .flatMap(
            obj ->
                getPermissions.apply(obj).stream()
                    .flatMap(p -> configurePermission(p, ownerType, getOwnerId.apply(obj))));
  }

  private Stream<ConfiguredAuthorization> configurePermission(
      final Permissions p, final AuthorizationOwnerType ownerType, final String ownerId) {
    final var idBasedAuthorizations =
        p.resourceIds().stream()
            .map(
                resourceId ->
                    new ConfiguredAuthorization(
                        ownerType,
                        ownerId,
                        configuredResourceType(p),
                        resourceId,
                        null,
                        configuredPermissions(p)));
    final var propertyBasedAuthorizations =
        p.resourcePropertyNames().stream()
            .map(
                resourcePropertyName ->
                    new ConfiguredAuthorization(
                        ownerType,
                        ownerId,
                        configuredResourceType(p),
                        null,
                        resourcePropertyName,
                        configuredPermissions(p)));
    return Stream.concat(idBasedAuthorizations, propertyBasedAuthorizations);
  }

  private static @NonNull AuthorizationResourceType configuredResourceType(final Permissions p) {
    return AuthorizationResourceType.valueOf(p.resourceType().name());
  }

  private static @NonNull Set<PermissionType> configuredPermissions(final Permissions p) {
    return Set.of(PermissionType.valueOf(p.permissionType().name()));
  }

  public record ConfigurationTestEntities(
      List<ConfiguredUser> users,
      List<ConfiguredGroup> groups,
      List<ConfiguredRole> roles,
      List<ConfiguredMappingRule> mappingRules,
      List<ConfiguredAuthorization> authorizations) {

    public Set<String> usernames() {
      return users.stream().map(ConfiguredUser::getUsername).collect(Collectors.toSet());
    }

    public Set<String> groupIds() {
      return groups.stream().map(ConfiguredGroup::groupId).collect(Collectors.toSet());
    }

    public Set<String> roleIds() {
      return roles.stream().map(ConfiguredRole::roleId).collect(Collectors.toSet());
    }

    public Set<String> mappingRuleIds() {
      return mappingRules.stream()
          .map(ConfiguredMappingRule::getMappingRuleId)
          .collect(Collectors.toSet());
    }
  }
}
