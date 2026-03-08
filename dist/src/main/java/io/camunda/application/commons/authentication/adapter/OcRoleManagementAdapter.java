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
import io.camunda.auth.domain.port.inbound.RoleManagementPort;
import io.camunda.auth.domain.spi.CamundaAuthenticationProvider;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.RoleMemberEntity;
import io.camunda.search.query.MappingRuleQuery;
import io.camunda.search.query.RoleMemberQuery;
import io.camunda.search.query.RoleQuery;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.service.RoleServices.CreateRoleRequest;
import io.camunda.service.RoleServices.RoleMemberRequest;
import io.camunda.service.RoleServices.UpdateRoleRequest;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.concurrent.CompletionException;

/**
 * Bridges the auth library's {@link RoleManagementPort} to the monorepo's {@link RoleServices}.
 * Member search for mapping rules delegates to {@link MappingRuleServices} because mapping rule
 * members are stored as full entities with claim metadata.
 */
public class OcRoleManagementAdapter implements RoleManagementPort {

  private final RoleServices roleServices;
  private final MappingRuleServices mappingRuleServices;
  private final CamundaAuthenticationProvider authProvider;

  public OcRoleManagementAdapter(
      final RoleServices roleServices,
      final MappingRuleServices mappingRuleServices,
      final CamundaAuthenticationProvider authProvider) {
    this.roleServices = roleServices;
    this.mappingRuleServices = mappingRuleServices;
    this.authProvider = authProvider;
  }

  @Override
  public AuthRole getById(final String roleId) {
    final var entity = roleServices.withAuthentication(auth()).getRole(roleId);
    return toAuthRole(entity);
  }

  @Override
  public AuthRole create(final String roleId, final String name, final String description) {
    try {
      roleServices
          .withAuthentication(auth())
          .createRole(new CreateRoleRequest(roleId, name, description))
          .join();
      return new AuthRole(0L, roleId, name, description);
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  @Override
  public AuthRole update(final String roleId, final String name, final String description) {
    try {
      roleServices
          .withAuthentication(auth())
          .updateRole(new UpdateRoleRequest(roleId, name, description))
          .join();
      return new AuthRole(0L, roleId, name, description);
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  @Override
  public void delete(final String roleId) {
    try {
      roleServices.withAuthentication(auth()).deleteRole(roleId).join();
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  @Override
  public void addMember(final String roleId, final String memberId, final MemberType memberType) {
    try {
      roleServices
          .withAuthentication(auth())
          .addMember(new RoleMemberRequest(roleId, memberId, toEntityType(memberType)))
          .join();
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  @Override
  public void removeMember(
      final String roleId, final String memberId, final MemberType memberType) {
    try {
      roleServices
          .withAuthentication(auth())
          .removeMember(new RoleMemberRequest(roleId, memberId, toEntityType(memberType)))
          .join();
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  @Override
  public SearchResult<AuthRole> search(final SearchQuery<RoleFilter> query) {
    final var filter = query.filter();
    final var page = query.page();

    final var ocQuery =
        RoleQuery.of(
            q -> {
              q.filter(
                  f -> {
                    if (filter != null) {
                      if (filter.roleId() != null) {
                        f.roleId(filter.roleId());
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

    final var result = roleServices.withAuthentication(auth()).search(ocQuery);
    final var items = result.items().stream().map(OcRoleManagementAdapter::toAuthRole).toList();
    return new SearchResult<>(items, result.total());
  }

  @Override
  public SearchResult<AuthUser> searchUserMembers(
      final String roleId, final SearchQuery<UserFilter> query) {
    return searchMembersByType(roleId, EntityType.USER, query.page())
        .mapItems(m -> new AuthUser(0L, m.id(), null, null, null));
  }

  @Override
  public SearchResult<AuthGroup> searchGroupMembers(
      final String roleId, final SearchQuery<GroupFilter> query) {
    return searchMembersByType(roleId, EntityType.GROUP, query.page())
        .mapItems(m -> new AuthGroup(0L, m.id(), null, null));
  }

  @Override
  public SearchResult<AuthUser> searchClientMembers(
      final String roleId, final SearchQuery<UserFilter> query) {
    return searchMembersByType(roleId, EntityType.CLIENT, query.page())
        .mapItems(m -> new AuthUser(0L, m.id(), null, null, null));
  }

  @Override
  public SearchResult<AuthMappingRule> searchMappingRuleMembers(
      final String roleId, final SearchQuery<MappingRuleFilter> query) {
    final var page = query.page();

    final var ocQuery =
        MappingRuleQuery.of(
            q -> {
              q.filter(f -> f.roleId(roleId));
              q.page(p -> p.from(page.from()).size(page.size()));
              return q;
            });

    final var result = mappingRuleServices.withAuthentication(auth()).search(ocQuery);
    final var items =
        result.items().stream().map(OcRoleManagementAdapter::toAuthMappingRule).toList();
    return new SearchResult<>(items, result.total());
  }

  private MemberSearchResult searchMembersByType(
      final String roleId,
      final EntityType entityType,
      final io.camunda.auth.domain.model.search.SearchPage page) {

    final var ocQuery =
        RoleMemberQuery.of(
            q -> {
              q.filter(f -> f.roleId(roleId).memberType(entityType));
              q.page(p -> p.from(page.from()).size(page.size()));
              return q;
            });

    final var result = roleServices.withAuthentication(auth()).searchMembers(ocQuery);
    return new MemberSearchResult(result.items(), result.total());
  }

  private CamundaAuthentication auth() {
    return authProvider.getCamundaAuthentication();
  }

  private static AuthRole toAuthRole(final RoleEntity entity) {
    return new AuthRole(
        entity.roleKey() != null ? entity.roleKey() : 0L,
        entity.roleId(),
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

  private record MemberSearchResult(java.util.List<RoleMemberEntity> members, long total) {

    <T> SearchResult<T> mapItems(final java.util.function.Function<RoleMemberEntity, T> mapper) {
      final var items = members.stream().map(mapper).toList();
      return new SearchResult<>(items, total);
    }
  }
}
