/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.multidb;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.api.search.request.TypedSearchRequest;
import io.camunda.client.api.search.response.Authorization;
import io.camunda.client.api.search.response.BaseResponse;
import io.camunda.client.api.search.response.Client;
import io.camunda.client.api.search.response.Group;
import io.camunda.client.api.search.response.GroupUser;
import io.camunda.client.api.search.response.MappingRule;
import io.camunda.client.api.search.response.Role;
import io.camunda.client.api.search.response.RoleGroup;
import io.camunda.client.api.search.response.RoleUser;
import io.camunda.client.api.search.response.User;
import io.camunda.qa.util.auth.Membership;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestClient;
import io.camunda.qa.util.auth.TestGroup;
import io.camunda.qa.util.auth.TestMappingRule;
import io.camunda.qa.util.auth.TestRole;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.ObjectUtils;
import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EntityManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(EntityManager.class);

  final Map<String, TestUser> users = new ConcurrentHashMap<>();
  final Map<String, TestClient> clients = new ConcurrentHashMap<>();
  final Map<String, TestGroup> groups = new ConcurrentHashMap<>();
  final Map<String, TestRole> roles = new ConcurrentHashMap<>();
  final Map<String, TestMappingRule> mappingRules = new ConcurrentHashMap<>();

  private final Set<String> createdPermissionIdentifiers = ConcurrentHashMap.newKeySet();

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

  public EntityManager withClients(final List<TestClient> clients) {
    clients.stream()
        .filter(client -> !this.clients.containsKey(client.clientId()))
        .forEach(
            client -> {
              this.clients.put(client.clientId(), client);
              addPermissions(client.clientId(), OwnerType.CLIENT, client.permissions());
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
        permission -> {
          // Create permissions for resourceIds
          permission
              .resourceIds()
              .forEach(
                  resourceId -> {
                    defaultClient
                        .newCreateAuthorizationCommand()
                        .ownerId(ownerId)
                        .ownerType(ownerType)
                        .resourceId(resourceId)
                        .resourceType(permission.resourceType())
                        .permissionTypes(permission.permissionType())
                        .send()
                        .join();
                    createdPermissionIdentifiers.add(
                        permissionIdentifier(
                            ownerId,
                            ownerType,
                            permission.resourceType(),
                            resourceId,
                            permission.permissionType()));
                  });

          // Create permissions for resourcePropertyNames
          permission
              .resourcePropertyNames()
              .forEach(
                  propertyName -> {
                    defaultClient
                        .newCreateAuthorizationCommand()
                        .ownerId(ownerId)
                        .ownerType(ownerType)
                        .resourcePropertyName(propertyName)
                        .resourceType(permission.resourceType())
                        .permissionTypes(permission.permissionType())
                        .send()
                        .join();
                    createdPermissionIdentifiers.add(
                        permissionIdentifier(
                            ownerId,
                            ownerType,
                            permission.resourceType(),
                            propertyName,
                            permission.permissionType()));
                  });
        });
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
            case CLIENT:
              defaultClient
                  .newAssignClientToGroupCommand()
                  .clientId(membership.memberId())
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
            case CLIENT:
              defaultClient
                  .newAssignRoleToClientCommand()
                  .roleId(roleId)
                  .clientId(membership.memberId())
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
    LOGGER.debug("Awaiting visibility of all entities and permissions");
    awaitSearchVisibility(
        "users",
        () -> wrap(defaultClient.newUsersSearchRequest()),
        extractField(User::getUsername),
        users.keySet());

    awaitSearchVisibility(
        "permissions",
        () -> wrap(defaultClient.newAuthorizationSearchRequest()),
        this::toPermissionIdentifiers,
        createdPermissionIdentifiers);

    awaitSearchVisibility(
        "groups",
        () -> wrap(defaultClient.newGroupsSearchRequest()),
        extractField(Group::getGroupId),
        groups.keySet());

    awaitMembershipsVisibility(
        "group memberships",
        groups,
        TestGroup::id,
        TestGroup::memberships,
        Map.of(
            EntityType.USER,
            new MembershipSearch<>(
                groupId -> wrap(defaultClient.newUsersByGroupSearchRequest(groupId)),
                GroupUser::getUsername),
            EntityType.CLIENT,
            new MembershipSearch<>(
                groupId -> wrap(defaultClient.newClientsByGroupSearchRequest(groupId)),
                Client::getClientId),
            EntityType.MAPPING_RULE,
            new MembershipSearch<>(
                groupId -> wrap(defaultClient.newMappingRulesByGroupSearchRequest(groupId)),
                MappingRule::getMappingRuleId)));

    awaitSearchVisibility(
        "mapping rules",
        () -> wrap(defaultClient.newMappingRulesSearchRequest()),
        extractField(MappingRule::getMappingRuleId),
        mappingRules.keySet());

    awaitSearchVisibility(
        "roles",
        () -> wrap(defaultClient.newRolesSearchRequest()),
        extractField(Role::getRoleId),
        roles.keySet());

    awaitMembershipsVisibility(
        "role memberships",
        roles,
        TestRole::id,
        TestRole::memberships,
        Map.of(
            EntityType.USER,
            new MembershipSearch<>(
                roleId -> wrap(defaultClient.newUsersByRoleSearchRequest(roleId)),
                RoleUser::getUsername),
            EntityType.GROUP,
            new MembershipSearch<>(
                roleId -> wrap(defaultClient.newGroupsByRoleSearchRequest(roleId)),
                RoleGroup::getGroupId),
            EntityType.CLIENT,
            new MembershipSearch<>(
                roleId -> wrap(defaultClient.newClientsByRoleSearchRequest(roleId)),
                Client::getClientId),
            EntityType.MAPPING_RULE,
            new MembershipSearch<>(
                roleId -> wrap(defaultClient.newMappingRulesByRoleSearchRequest(roleId)),
                MappingRule::getMappingRuleId)));
    LOGGER.debug("Finished waiting for visibility of all entities and permissions.");
  }

  private Stream<String> toPermissionIdentifiers(final Authorization authorization) {
    return authorization.getPermissionTypes().stream()
        .map(
            permissionType ->
                permissionIdentifier(
                    authorization.getOwnerId(),
                    authorization.getOwnerType(),
                    authorization.getResourceType(),
                    ObjectUtils.firstNonNull(
                        authorization.getResourceId(), authorization.getResourcePropertyName()),
                    permissionType));
  }

  private String permissionIdentifier(
      final String ownerId,
      final OwnerType ownerType,
      final ResourceType resourceType,
      final String identifier,
      final PermissionType permissionType) {
    return "%s|%s|%s|%s|%s".formatted(ownerId, ownerType, resourceType, identifier, permissionType);
  }

  private <T> void awaitMembershipsVisibility(
      final String alias,
      final Map<String, T> items,
      final Function<T, String> membershipIdExtractor,
      final Function<T, List<Membership>> membershipExtractor,
      final Map<EntityType, MembershipSearch<?>> membershipSearchesByType) {
    final Map<String, List<Membership>> membershipsById =
        items.values().stream()
            .collect(Collectors.toMap(membershipIdExtractor, membershipExtractor));
    // have to run through each group/role separately as each has its own search request
    for (final var entry : membershipsById.entrySet()) {
      final var membershipId = entry.getKey();
      final var memberships = entry.getValue();
      final Map<EntityType, List<Membership>> membershipByType =
          memberships.stream().collect(Collectors.groupingBy(Membership::entityType));
      // and we also have to search by type of entity separately
      for (final var membership : membershipByType.entrySet()) {
        final var entityType = membership.getKey();
        final var membershipSearch =
            Objects.requireNonNull(membershipSearchesByType.get(entityType));
        final var membershipList = membership.getValue();
        awaitMembershipsVisibility(
            alias, membershipList, membershipSearch, membershipId, entityType);
      }
    }
  }

  private <T> void awaitMembershipsVisibility(
      final String alias,
      final List<Membership> membershipList,
      final MembershipSearch<T> membershipSearch,
      final String membershipId,
      final EntityType entityType) {
    final var expectedIds =
        membershipList.stream().map(Membership::memberId).collect(Collectors.toSet());
    final var idExtractor = membershipSearch.idExtractor();
    awaitSearchVisibility(
        alias + " (entityType=" + entityType + ", id=" + membershipId + ")",
        () -> membershipSearch.searchFactory().apply(membershipId),
        extractField(idExtractor),
        expectedIds);
  }

  private <T> Function<T, Stream<String>> extractField(final Function<T, String> fieldExtractor) {
    return obj -> Stream.of(fieldExtractor.apply(obj));
  }

  private <T> void awaitSearchVisibility(
      final String alias,
      final Supplier<SearchWithLimit<T>> searchSupplier,
      final Function<T, Stream<String>> idsExtractor,
      final Set<String> expected) {
    if (expected.isEmpty()) {
      return;
    }
    final var search = searchSupplier.get();
    Awaitility.await(alias)
        .timeout(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var results = search.search(expected.size() + 100);
              LOGGER.debug(
                  "Search {} found {} items - looking for {}",
                  alias,
                  results.size(),
                  expected.size());
              final var actual = results.stream().flatMap(idsExtractor).collect(Collectors.toSet());
              assertThat(actual).containsAll(expected);
            });
  }

  // wraps a search request into a function that returns the number of found items
  // so we can avoid repeating this horrible generics mess multiple times
  private <
          T, R extends BaseResponse<T>, S extends FinalCommandStep<R> & TypedSearchRequest<?, ?, S>>
      SearchWithLimit<T> wrap(final S request) {
    return expected -> {
      final var response = request.page(p -> p.limit(expected)).send().join();
      return response.items();
    };
  }

  record MembershipSearch<T>(
      Function<String, SearchWithLimit<T>> searchFactory, Function<T, String> idExtractor) {}

  interface SearchWithLimit<T> {
    List<T> search(int limit);
  }
}
