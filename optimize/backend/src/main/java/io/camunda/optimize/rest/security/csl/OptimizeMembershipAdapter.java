/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.camunda.optimize.dto.optimize.query.AccessMappingRuleDto;
import io.camunda.optimize.dto.optimize.query.AccessRoleDto;
import io.camunda.optimize.dto.optimize.query.TenantMembershipDto.MemberType;
import io.camunda.optimize.service.db.repository.AccessRepository;
import io.camunda.optimize.service.db.repository.TenantMembershipRepository;
import io.camunda.security.core.auth.MappingRuleMatcher;
import io.camunda.security.core.port.out.MembershipPort;
import io.camunda.security.core.port.out.MembershipQuery;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link MembershipPort} for Optimize's CSL wiring. CSL's lazy claim converters require a {@code
 * MembershipPort} bean to build their chains, and CSL ships no default.
 *
 * <p>POC: with an {@link AccessRepository} the port resolves mapping rules from the token claims
 * and roles from the matched rules, the way the Hub POC does; with a {@link
 * TenantMembershipRepository} it resolves the tenants assigned to the principal, its groups, roles
 * and matched rules. Either one {@code null} keeps the previous behaviour of returning nothing, so
 * that Management Identity can stay in charge while the POC is switched off.
 *
 * <p>Rules and roles are read through a short-lived cache, because CSL asks on every request.
 */
public final class OptimizeMembershipAdapter implements MembershipPort {

  private final TenantMembershipRepository tenantMembershipRepository;
  private final LoadingCache<String, List<AccessMappingRuleDto>> rules;
  private final LoadingCache<String, List<AccessRoleDto>> roles;

  public OptimizeMembershipAdapter(
      final TenantMembershipRepository tenantMembershipRepository,
      final AccessRepository accessRepository,
      final Duration accessCacheTtl) {
    this.tenantMembershipRepository = tenantMembershipRepository;
    if (accessRepository == null) {
      rules = null;
      roles = null;
    } else {
      rules =
          Caffeine.newBuilder()
              .expireAfterWrite(accessCacheTtl)
              .build(key -> accessRepository.findAllMappingRules());
      roles =
          Caffeine.newBuilder()
              .expireAfterWrite(accessCacheTtl)
              .build(key -> accessRepository.findAllRoles());
    }
  }

  @Override
  public List<String> mappingRuleIds(final MembershipQuery query) {
    if (rules == null || query.tokenClaims() == null) {
      return List.of();
    }
    return MappingRuleMatcher.matchingRules(
            rules.get("all").stream().map(StoredRule::new), query.tokenClaims())
        .map(StoredRule::mappingRuleId)
        .toList();
  }

  @Override
  public List<String> groupIds(final MembershipQuery query) {
    return List.of();
  }

  @Override
  public List<String> roleIds(final MembershipQuery query) {
    if (roles == null) {
      return List.of();
    }
    final boolean client = query.principalType() == PrincipalType.CLIENT;
    return roles.get("all").stream()
        .filter(
            role ->
                intersects(role.getMappingRuleIds(), query.resolvedMappingRuleIds())
                    || intersects(role.getGroupIds(), query.resolvedGroupIds())
                    || (query.principalId() != null
                        && (client ? role.getClientIds() : role.getUserIds())
                            .contains(query.principalId())))
        .map(AccessRoleDto::getId)
        .toList();
  }

  @Override
  public List<String> tenantIds(final MembershipQuery query) {
    if (tenantMembershipRepository == null) {
      return List.of();
    }
    final Set<String> tenantIds = new HashSet<>();
    final MemberType principalType =
        query.principalType() == PrincipalType.CLIENT ? MemberType.CLIENT : MemberType.USER;
    if (query.principalId() != null) {
      tenantIds.addAll(
          tenantMembershipRepository.findTenantIds(principalType, Set.of(query.principalId())));
    }
    addTenants(tenantIds, MemberType.GROUP, query.resolvedGroupIds());
    addTenants(tenantIds, MemberType.ROLE, query.resolvedRoleIds());
    addTenants(tenantIds, MemberType.MAPPING_RULE, query.resolvedMappingRuleIds());
    return List.copyOf(tenantIds);
  }

  private void addTenants(
      final Set<String> target, final MemberType type, final List<String> memberIds) {
    if (memberIds != null && !memberIds.isEmpty()) {
      target.addAll(tenantMembershipRepository.findTenantIds(type, Set.copyOf(memberIds)));
    }
  }

  private static boolean intersects(final List<String> left, final List<String> right) {
    return left != null && right != null && left.stream().anyMatch(right::contains);
  }

  private record StoredRule(AccessMappingRuleDto rule) implements MappingRuleMatcher.MappingRule {

    @Override
    public String mappingRuleId() {
      return rule.getId();
    }

    @Override
    public String claimName() {
      return rule.getClaimName();
    }

    @Override
    public String claimValue() {
      return rule.getClaimValue();
    }
  }
}
