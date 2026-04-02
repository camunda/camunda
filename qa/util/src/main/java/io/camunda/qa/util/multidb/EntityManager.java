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
import io.camunda.client.api.search.page.AnyPage;
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
import io.camunda.client.api.search.response.Tenant;
import io.camunda.client.api.search.response.TenantGroup;
import io.camunda.client.api.search.response.TenantUser;
import io.camunda.client.api.search.response.User;
import io.camunda.qa.util.multidb.TestEntityConfigurer.ConfigurationTestEntities;
import io.camunda.security.configuration.ConfiguredAuthorization;
import io.camunda.security.configuration.ConfiguredGroup;
import io.camunda.security.configuration.ConfiguredRole;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.ObjectUtils;
import org.awaitility.Awaitility;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EntityManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(EntityManager.class);

  private final CamundaClient defaultClient;

  public EntityManager(final CamundaClient defaultClient) {
    this.defaultClient = defaultClient;
  }

  /** Will wait until all entities are created and permissions are assigned. */
  public void await(final @NonNull ConfigurationTestEntities testEntities) {
    LOGGER.debug("Awaiting visibility of all entities and permissions");
    awaitSearchVisibility(
        "users",
        () -> wrap(defaultClient.newUsersSearchRequest()),
        extractField(User::getUsername),
        testEntities.usernames());

    awaitSearchVisibility(
        "permissions",
        () -> wrap(defaultClient.newAuthorizationSearchRequest()),
        this::toPermissionIdentifiers,
        testEntities.authorizations().stream()
            .flatMap(this::toPermissionIdentifiers)
            .collect(Collectors.toSet()));

    awaitSearchVisibility(
        "groups",
        () -> wrap(defaultClient.newGroupsSearchRequest()),
        extractField(Group::getGroupId),
        testEntities.groupIds());

    awaitMembershipsVisibility(
        "group memberships",
        testEntities.groups(),
        ConfiguredGroup::groupId,
        List.of(
            new MembershipSearch<>(
                EntityType.USER,
                ConfiguredGroup::users,
                groupId -> wrap(defaultClient.newUsersByGroupSearchRequest(groupId)),
                GroupUser::getUsername),
            new MembershipSearch<>(
                EntityType.CLIENT,
                ConfiguredGroup::clients,
                groupId -> wrap(defaultClient.newClientsByGroupSearchRequest(groupId)),
                Client::getClientId),
            new MembershipSearch<>(
                EntityType.MAPPING_RULE,
                ConfiguredGroup::mappingRules,
                groupId -> wrap(defaultClient.newMappingRulesByGroupSearchRequest(groupId)),
                MappingRule::getMappingRuleId)));

    awaitSearchVisibility(
        "mapping rules",
        () -> wrap(defaultClient.newMappingRulesSearchRequest()),
        extractField(MappingRule::getMappingRuleId),
        testEntities.mappingRuleIds());

    awaitSearchVisibility(
        "roles",
        () -> wrap(defaultClient.newRolesSearchRequest()),
        extractField(Role::getRoleId),
        testEntities.roleIds());

    awaitMembershipsVisibility(
        "role memberships",
        testEntities.roles(),
        ConfiguredRole::roleId,
        List.of(
            new MembershipSearch<>(
                EntityType.USER,
                ConfiguredRole::users,
                roleId -> wrap(defaultClient.newUsersByRoleSearchRequest(roleId)),
                RoleUser::getUsername),
            new MembershipSearch<>(
                EntityType.GROUP,
                ConfiguredRole::groups,
                roleId -> wrap(defaultClient.newGroupsByRoleSearchRequest(roleId)),
                RoleGroup::getGroupId),
            new MembershipSearch<>(
                EntityType.CLIENT,
                ConfiguredRole::clients,
                roleId -> wrap(defaultClient.newClientsByRoleSearchRequest(roleId)),
                Client::getClientId),
            new MembershipSearch<>(
                EntityType.MAPPING_RULE,
                ConfiguredRole::mappingRules,
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
                    authorization.getOwnerType().name(),
                    authorization.getResourceType().name(),
                    ObjectUtils.firstNonNull(
                        authorization.getResourceId(), authorization.getResourcePropertyName()),
                    permissionType.name()));
  }

  private Stream<String> toPermissionIdentifiers(final ConfiguredAuthorization authorization) {
    return authorization.permissions().stream()
        .map(
            permissionType ->
                permissionIdentifier(
                    authorization.ownerId(),
                    authorization.ownerType().name(),
                    authorization.resourceType().name(),
                    ObjectUtils.firstNonNull(
                        authorization.resourceId(), authorization.resourcePropertyName()),
                    permissionType.name()));
  }

  private String permissionIdentifier(
      final String ownerId,
      final String ownerType,
      final String resourceType,
      final String identifier,
      final String permissionType) {
    return "%s|%s|%s|%s|%s".formatted(ownerId, ownerType, resourceType, identifier, permissionType);
  }

  private <TParent> void awaitMembershipsVisibility(
      final String alias,
      final List<TParent> items,
      final Function<TParent, String> getParentId,
      final List<MembershipSearch<?, TParent>> membershipSearches) {
    // have to run through each group/role separately as each has its own search request
    for (final var item : items) {
      final var parentId = getParentId.apply(item);
      for (final var membershipSearch : membershipSearches) {
        final var expectedMembers = membershipSearch.getExpectedMembers.apply(item);
        awaitMembershipsVisibility(alias, parentId, expectedMembers, membershipSearch);
      }
    }
  }

  private <TMember> void awaitMembershipsVisibility(
      final String alias,
      final String parentId,
      final List<String> members,
      final MembershipSearch<TMember, ?> membershipSearch) {
    final var expectedIds = new HashSet<>(members);
    awaitSearchVisibility(
        alias + " (memberType=" + membershipSearch.memberType + ", id=" + parentId + ")",
        () -> membershipSearch.searchFactory().apply(parentId),
        extractField(membershipSearch.getMemberId()),
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
          T,
          R extends BaseResponse<T>,
          S extends FinalCommandStep<R> & TypedSearchRequest<?, ?, AnyPage, S>>
      SearchWithLimit<T> wrap(final S request) {
    return expected -> {
      final var response = request.page(p -> p.limit(expected)).send().join();
      return response.items();
    };
  }

  record MembershipSearch<TMember, TParent>(
      EntityType memberType,
      Function<TParent, List<String>> getExpectedMembers,
      Function<String, SearchWithLimit<TMember>> searchFactory,
      Function<TMember, String> getMemberId) {}

  interface SearchWithLimit<T> {
    List<T> search(int limit);
  }
}
