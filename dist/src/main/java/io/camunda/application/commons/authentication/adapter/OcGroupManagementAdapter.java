/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.authentication.adapter;

import io.camunda.auth.domain.model.AuthGroup;
import io.camunda.auth.domain.model.AuthMappingRule;
import io.camunda.auth.domain.model.AuthRole;
import io.camunda.auth.domain.model.AuthUser;
import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.model.MemberType;
import io.camunda.auth.domain.model.search.GroupFilter;
import io.camunda.auth.domain.model.search.MappingRuleFilter;
import io.camunda.auth.domain.model.search.RoleFilter;
import io.camunda.auth.domain.model.search.SearchQuery;
import io.camunda.auth.domain.model.search.SearchResult;
import io.camunda.auth.domain.model.search.UserFilter;
import io.camunda.auth.domain.port.inbound.GroupManagementPort;
import io.camunda.auth.domain.spi.CamundaAuthenticationProvider;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.GroupMemberEntity;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.query.GroupMemberQuery;
import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.MappingRuleQuery;
import io.camunda.service.GroupServices;
import io.camunda.service.GroupServices.GroupDTO;
import io.camunda.service.GroupServices.GroupMemberDTO;
import io.camunda.service.MappingRuleServices;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

/**
 * Bridges the auth library's {@link GroupManagementPort} to the monorepo's {@link GroupServices}.
 * Member search for mapping rules delegates to {@link MappingRuleServices} because mapping rule
 * members are stored as full entities with claim metadata.
 */
public class OcGroupManagementAdapter implements GroupManagementPort {

  private final GroupServices groupServices;
  private final MappingRuleServices mappingRuleServices;
  private final CamundaAuthenticationProvider authProvider;

  public OcGroupManagementAdapter(
      final GroupServices groupServices,
      final MappingRuleServices mappingRuleServices,
      final CamundaAuthenticationProvider authProvider) {
    this.groupServices = groupServices;
    this.mappingRuleServices = mappingRuleServices;
    this.authProvider = authProvider;
  }

  @Override
  public AuthGroup getById(final String groupId) {
    final var entity = groupServices.withAuthentication(auth()).getGroup(groupId);
    return toAuthGroup(entity);
  }

  @Override
  public AuthGroup create(final String groupId, final String name, final String description) {
    try {
      groupServices
          .withAuthentication(auth())
          .createGroup(new GroupDTO(groupId, name, description))
          .join();
      return new AuthGroup(0L, groupId, name, description);
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  @Override
  public AuthGroup update(final String groupId, final String name, final String description) {
    try {
      groupServices.withAuthentication(auth()).updateGroup(groupId, name, description).join();
      return new AuthGroup(0L, groupId, name, description);
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  @Override
  public void delete(final String groupId) {
    try {
      groupServices.withAuthentication(auth()).deleteGroup(groupId).join();
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  @Override
  public void addMember(final String groupId, final String memberId, final MemberType memberType) {
    try {
      groupServices
          .withAuthentication(auth())
          .assignMember(new GroupMemberDTO(groupId, memberId, toEntityType(memberType)))
          .join();
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  @Override
  public void removeMember(
      final String groupId, final String memberId, final MemberType memberType) {
    try {
      groupServices
          .withAuthentication(auth())
          .removeMember(new GroupMemberDTO(groupId, memberId, toEntityType(memberType)))
          .join();
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  @Override
  public SearchResult<AuthGroup> search(final SearchQuery<GroupFilter> query) {
    final var filter = query.filter();
    final var page = query.page();

    final var ocQuery =
        GroupQuery.of(
            q -> {
              q.filter(
                  f -> {
                    if (filter != null) {
                      if (filter.groupId() != null) {
                        f.groupIds(filter.groupId());
                      }
                      if (filter.name() != null) {
                        f.name(filter.name());
                      }
                    }
                    return f;
                  });
              q.page(p -> p.from(page.from()).size(page.size()));
              return q;
            });

    final var result = groupServices.withAuthentication(auth()).search(ocQuery);
    final var items = result.items().stream().map(OcGroupManagementAdapter::toAuthGroup).toList();
    return new SearchResult<>(items, result.total());
  }

  @Override
  public SearchResult<AuthUser> searchUserMembers(
      final String groupId, final SearchQuery<UserFilter> query) {
    return searchMembersByType(groupId, EntityType.USER, query.page())
        .mapItems(m -> new AuthUser(0L, m.id(), null, null, null));
  }

  @Override
  public SearchResult<AuthUser> searchClientMembers(
      final String groupId, final SearchQuery<UserFilter> query) {
    return searchMembersByType(groupId, EntityType.CLIENT, query.page())
        .mapItems(m -> new AuthUser(0L, m.id(), null, null, null));
  }

  @Override
  public SearchResult<AuthRole> searchRoleMembers(
      final String groupId, final SearchQuery<RoleFilter> query) {
    return searchMembersByType(groupId, EntityType.ROLE, query.page())
        .mapItems(m -> new AuthRole(0L, m.id(), null, null));
  }

  @Override
  public SearchResult<AuthMappingRule> searchMappingRuleMembers(
      final String groupId, final SearchQuery<MappingRuleFilter> query) {
    final var page = query.page();

    final var ocQuery =
        MappingRuleQuery.of(
            q -> {
              q.filter(f -> f.groupId(groupId));
              q.page(p -> p.from(page.from()).size(page.size()));
              return q;
            });

    final var result = mappingRuleServices.withAuthentication(auth()).search(ocQuery);
    final var items =
        result.items().stream().map(OcGroupManagementAdapter::toAuthMappingRule).toList();
    return new SearchResult<>(items, result.total());
  }

  private MemberSearchResult searchMembersByType(
      final String groupId,
      final EntityType entityType,
      final io.camunda.auth.domain.model.search.SearchPage page) {

    final var ocQuery =
        GroupMemberQuery.of(
            q -> {
              q.filter(f -> f.groupId(groupId).memberType(entityType));
              q.page(p -> p.from(page.from()).size(page.size()));
              return q;
            });

    final var result = groupServices.withAuthentication(auth()).searchMembers(ocQuery);
    return new MemberSearchResult(result.items(), result.total());
  }

  private CamundaAuthentication auth() {
    return authProvider.getCamundaAuthentication();
  }

  private static AuthGroup toAuthGroup(final GroupEntity entity) {
    return new AuthGroup(
        entity.groupKey() != null ? entity.groupKey() : 0L,
        entity.groupId(),
        entity.name(),
        entity.description());
  }

  private static AuthMappingRule toAuthMappingRule(final MappingRuleEntity entity) {
    return new AuthMappingRule(
        entity.mappingRuleKey() != null ? entity.mappingRuleKey() : 0L,
        entity.mappingRuleId(),
        entity.claimName(),
        entity.claimValue(),
        entity.name());
  }

  private static EntityType toEntityType(final MemberType type) {
    return switch (type) {
      case USER -> EntityType.USER;
      case CLIENT -> EntityType.CLIENT;
      case GROUP -> EntityType.GROUP;
      case ROLE -> EntityType.ROLE;
      case MAPPING_RULE -> EntityType.MAPPING_RULE;
    };
  }

  private static RuntimeException mapException(final Throwable cause) {
    if (cause instanceof RuntimeException re) {
      return re;
    }
    return new RuntimeException(cause);
  }

  private record MemberSearchResult(List<GroupMemberEntity> members, long total) {

    <T> SearchResult<T> mapItems(final Function<GroupMemberEntity, T> mapper) {
      final var items = members.stream().map(mapper).toList();
      return new SearchResult<>(items, total);
    }
  }
}
