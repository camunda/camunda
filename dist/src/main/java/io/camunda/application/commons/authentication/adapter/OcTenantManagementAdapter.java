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
import io.camunda.auth.domain.model.AuthTenant;
import io.camunda.auth.domain.model.AuthUser;
import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.model.MemberType;
import io.camunda.auth.domain.model.search.GroupFilter;
import io.camunda.auth.domain.model.search.MappingRuleFilter;
import io.camunda.auth.domain.model.search.RoleFilter;
import io.camunda.auth.domain.model.search.SearchQuery;
import io.camunda.auth.domain.model.search.SearchResult;
import io.camunda.auth.domain.model.search.TenantFilter;
import io.camunda.auth.domain.model.search.UserFilter;
import io.camunda.auth.domain.port.inbound.TenantManagementPort;
import io.camunda.auth.domain.spi.CamundaAuthenticationProvider;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.entities.TenantEntity;
import io.camunda.search.entities.TenantMemberEntity;
import io.camunda.search.query.MappingRuleQuery;
import io.camunda.search.query.TenantMemberQuery;
import io.camunda.search.query.TenantQuery;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.TenantServices;
import io.camunda.service.TenantServices.TenantMemberRequest;
import io.camunda.service.TenantServices.TenantRequest;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

/**
 * Bridges the auth library's {@link TenantManagementPort} to the monorepo's {@link TenantServices}.
 * Member search for mapping rules delegates to {@link MappingRuleServices} because mapping rule
 * members are stored as full entities with claim metadata.
 */
public class OcTenantManagementAdapter implements TenantManagementPort {

  private final TenantServices tenantServices;
  private final MappingRuleServices mappingRuleServices;
  private final CamundaAuthenticationProvider authProvider;

  public OcTenantManagementAdapter(
      final TenantServices tenantServices,
      final MappingRuleServices mappingRuleServices,
      final CamundaAuthenticationProvider authProvider) {
    this.tenantServices = tenantServices;
    this.mappingRuleServices = mappingRuleServices;
    this.authProvider = authProvider;
  }

  @Override
  public AuthTenant getById(final String tenantId) {
    final var entity = tenantServices.withAuthentication(auth()).getById(tenantId);
    return toAuthTenant(entity);
  }

  @Override
  public AuthTenant create(final String tenantId, final String name, final String description) {
    try {
      tenantServices
          .withAuthentication(auth())
          .createTenant(new TenantRequest(null, tenantId, name, description))
          .join();
      return new AuthTenant(0L, tenantId, name, description);
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  @Override
  public AuthTenant update(final String tenantId, final String name, final String description) {
    try {
      tenantServices
          .withAuthentication(auth())
          .updateTenant(new TenantRequest(null, tenantId, name, description))
          .join();
      return new AuthTenant(0L, tenantId, name, description);
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  @Override
  public void delete(final String tenantId) {
    try {
      tenantServices.withAuthentication(auth()).deleteTenant(tenantId).join();
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  @Override
  public void addMember(final String tenantId, final String memberId, final MemberType memberType) {
    try {
      tenantServices
          .withAuthentication(auth())
          .addMember(new TenantMemberRequest(tenantId, memberId, toEntityType(memberType)))
          .join();
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  @Override
  public void removeMember(
      final String tenantId, final String memberId, final MemberType memberType) {
    try {
      tenantServices
          .withAuthentication(auth())
          .removeMember(new TenantMemberRequest(tenantId, memberId, toEntityType(memberType)))
          .join();
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  @Override
  public SearchResult<AuthTenant> search(final SearchQuery<TenantFilter> query) {
    final var filter = query.filter();
    final var page = query.page();

    final var ocQuery =
        TenantQuery.of(
            q -> {
              q.filter(
                  f -> {
                    if (filter != null) {
                      if (filter.tenantId() != null) {
                        f.tenantId(filter.tenantId());
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

    final var result = tenantServices.withAuthentication(auth()).search(ocQuery);
    final var items = result.items().stream().map(OcTenantManagementAdapter::toAuthTenant).toList();
    return new SearchResult<>(items, result.total());
  }

  @Override
  public SearchResult<AuthUser> searchUserMembers(
      final String tenantId, final SearchQuery<UserFilter> query) {
    return searchMembersByType(tenantId, EntityType.USER, query.page())
        .mapItems(m -> new AuthUser(0L, m.id(), null, null, null));
  }

  @Override
  public SearchResult<AuthGroup> searchGroupMembers(
      final String tenantId, final SearchQuery<GroupFilter> query) {
    return searchMembersByType(tenantId, EntityType.GROUP, query.page())
        .mapItems(m -> new AuthGroup(0L, m.id(), null, null));
  }

  @Override
  public SearchResult<AuthUser> searchClientMembers(
      final String tenantId, final SearchQuery<UserFilter> query) {
    return searchMembersByType(tenantId, EntityType.CLIENT, query.page())
        .mapItems(m -> new AuthUser(0L, m.id(), null, null, null));
  }

  @Override
  public SearchResult<AuthRole> searchRoleMembers(
      final String tenantId, final SearchQuery<RoleFilter> query) {
    return searchMembersByType(tenantId, EntityType.ROLE, query.page())
        .mapItems(m -> new AuthRole(0L, m.id(), null, null));
  }

  @Override
  public SearchResult<AuthMappingRule> searchMappingRuleMembers(
      final String tenantId, final SearchQuery<MappingRuleFilter> query) {
    final var page = query.page();

    final var ocQuery =
        MappingRuleQuery.of(
            q -> {
              q.filter(f -> f.tenantId(tenantId));
              q.page(p -> p.from(page.from()).size(page.size()));
              return q;
            });

    final var result = mappingRuleServices.withAuthentication(auth()).search(ocQuery);
    final var items =
        result.items().stream().map(OcTenantManagementAdapter::toAuthMappingRule).toList();
    return new SearchResult<>(items, result.total());
  }

  private MemberSearchResult searchMembersByType(
      final String tenantId,
      final EntityType entityType,
      final io.camunda.auth.domain.model.search.SearchPage page) {

    final var ocQuery =
        TenantMemberQuery.of(
            q -> {
              q.filter(f -> f.tenantId(tenantId).memberType(entityType));
              q.page(p -> p.from(page.from()).size(page.size()));
              return q;
            });

    final var result = tenantServices.withAuthentication(auth()).searchMembers(ocQuery);
    return new MemberSearchResult(result.items(), result.total());
  }

  private CamundaAuthentication auth() {
    return authProvider.getCamundaAuthentication();
  }

  private static AuthTenant toAuthTenant(final TenantEntity entity) {
    return new AuthTenant(
        entity.key() != null ? entity.key() : 0L,
        entity.tenantId(),
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

  private record MemberSearchResult(List<TenantMemberEntity> members, long total) {

    <T> SearchResult<T> mapItems(final Function<TenantMemberEntity, T> mapper) {
      final var items = members.stream().map(mapper).toList();
      return new SearchResult<>(items, total);
    }
  }
}
