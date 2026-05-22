/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import static io.camunda.security.api.model.authz.EntityType.GROUP;
import static io.camunda.security.api.model.authz.EntityType.MAPPING_RULE;

import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.TenantEntity;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.auth.MembershipProvider;
import io.camunda.security.api.model.authz.EntityType;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.core.oidc.OidcGroupsExtractor;
import io.camunda.security.core.port.out.MembershipPort;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageEnabled;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Host-side {@link MembershipPort} backed by the secondary-storage-driven {@code *Services}.
 * Returns a {@link LazyProvider} per principal that memoises a {@code mappingRules → groups → roles
 * → tenants} chain so each membership type is resolved on first read (and only that step's queries
 * run — subsequent reads share the chain seeds).
 */
@Service
@Primary
@ConditionalOnSecondaryStorageEnabled
public class DefaultMembershipService implements MembershipPort {
  private static final Logger LOG = LoggerFactory.getLogger(DefaultMembershipService.class);

  private final MappingRuleServices mappingRuleServices;
  private final TenantServices tenantServices;
  private final RoleServices roleServices;
  private final GroupServices groupServices;
  private final OidcGroupsExtractor oidcGroupsExtractor;
  private final boolean isGroupsClaimConfigured;

  public DefaultMembershipService(
      final MappingRuleServices mappingRuleServices,
      final TenantServices tenantServices,
      final RoleServices roleServices,
      final GroupServices groupServices,
      final SecurityConfiguration securityConfiguration) {
    this.mappingRuleServices = mappingRuleServices;
    this.tenantServices = tenantServices;
    this.roleServices = roleServices;
    this.groupServices = groupServices;
    oidcGroupsExtractor =
        new OidcGroupsExtractor(
            securityConfiguration.getAuthentication().getOidc().getGroupsClaim());
    isGroupsClaimConfigured =
        securityConfiguration.getAuthentication().getOidc().isGroupsClaimConfigured();
  }

  @Override
  public MembershipProvider createProvider(
      final Map<String, Object> tokenClaims,
      final String principalId,
      final PrincipalType principalType) {
    // OIDC groups-claim parsing is an in-memory token validation, not a DB call: evaluate it
    // eagerly so malformed claims fail fast at authentication time. DB-backed lookups stay
    // deferred to first read inside the LazyProvider.
    final List<String> eagerGroupsFromClaims =
        isGroupsClaimConfigured ? List.copyOf(oidcGroupsExtractor.extract(tokenClaims)) : null;
    return new LazyProvider(tokenClaims, principalId, principalType, eagerGroupsFromClaims);
  }

  @Override
  public MembershipProvider createProviderForUser(final String username) {
    return new LazyProvider(Map.of(), username, PrincipalType.USER, null);
  }

  /**
   * Per-authentication provider that memoises prerequisite lookups so that when multiple membership
   * fields are read on the same authentication, the shared mappingRules→groups→roles→tenants chain
   * runs at most once per step. Synchronised so concurrent reads on different lazy fields don't
   * double-fetch.
   */
  private final class LazyProvider implements MembershipProvider {
    private final Map<String, Object> tokenClaims;
    private final List<String> eagerGroupsFromClaims;
    private final EnumMap<EntityType, Set<String>> ownerTypeToIds = new EnumMap<>(EntityType.class);

    private List<String> mappingRules;
    private List<String> groups;
    private List<String> roles;
    private List<String> tenants;

    LazyProvider(
        final Map<String, Object> tokenClaims,
        final String principalId,
        final PrincipalType principalType,
        final List<String> eagerGroupsFromClaims) {
      this.tokenClaims = tokenClaims;
      this.eagerGroupsFromClaims = eagerGroupsFromClaims;
      ownerTypeToIds.put(
          principalType == PrincipalType.USER ? EntityType.USER : EntityType.CLIENT,
          Set.of(principalId));
    }

    @Override
    public synchronized List<String> mappingRuleIds() {
      if (mappingRules == null) {
        // BASIC auth passes no claims; nothing can match, so skip the mappingRules query.
        if (tokenClaims.isEmpty()) {
          mappingRules = List.of();
          return mappingRules;
        }
        final var ids =
            mappingRuleServices
                .getMatchingMappingRules(tokenClaims, CamundaAuthentication.anonymous())
                .map(MappingRuleEntity::mappingRuleId)
                .collect(Collectors.toSet());
        if (!ids.isEmpty()) {
          ownerTypeToIds.put(MAPPING_RULE, ids);
        } else {
          LOG.debug("No mappingRules found for these claims: {}", tokenClaims);
        }
        mappingRules = List.copyOf(ids);
      }
      return mappingRules;
    }

    @Override
    public synchronized List<String> groupIds() {
      if (groups == null) {
        // mappingRuleIds() must run first so ownerTypeToIds includes MAPPING_RULE before the
        // group lookup uses it.
        mappingRuleIds();

        final Set<String> ids;
        if (eagerGroupsFromClaims != null) {
          ids = new HashSet<>(eagerGroupsFromClaims);
        } else {
          ids =
              groupServices
                  .getGroupsByMemberTypeAndMemberIds(
                      ownerTypeToIds, CamundaAuthentication.anonymous())
                  .stream()
                  .map(GroupEntity::groupId)
                  .collect(Collectors.toSet());
        }

        if (!ids.isEmpty()) {
          ownerTypeToIds.put(GROUP, ids);
        }
        groups = List.copyOf(ids);
      }
      return groups;
    }

    @Override
    public synchronized List<String> roleIds() {
      if (roles == null) {
        groupIds();

        final var ids =
            roleServices
                .getRolesByMemberTypeAndMemberIds(ownerTypeToIds, CamundaAuthentication.anonymous())
                .stream()
                .map(RoleEntity::roleId)
                .collect(Collectors.toSet());

        if (!ids.isEmpty()) {
          ownerTypeToIds.put(EntityType.ROLE, ids);
        }
        roles = List.copyOf(ids);
      }
      return roles;
    }

    @Override
    public synchronized List<String> tenantIds() {
      if (tenants == null) {
        roleIds();

        tenants =
            tenantServices
                .getTenantsByMemberTypeAndMemberIds(
                    ownerTypeToIds, CamundaAuthentication.anonymous())
                .stream()
                .map(TenantEntity::tenantId)
                .toList();
      }
      return tenants;
    }
  }
}
