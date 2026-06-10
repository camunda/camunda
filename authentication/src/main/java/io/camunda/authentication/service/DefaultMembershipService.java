/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import static io.camunda.zeebe.protocol.record.value.EntityType.GROUP;
import static io.camunda.zeebe.protocol.record.value.EntityType.MAPPING_RULE;

import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.TenantEntity;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.OidcGroupsLoader;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageEnabled;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.stereotype.Service;

@Service
@Primary
@ConditionalOnSecondaryStorageEnabled
public class DefaultMembershipService implements MembershipService {
  private static final Logger LOG = LoggerFactory.getLogger(DefaultMembershipService.class);

  private final MappingRuleServices mappingRuleServices;
  private final TenantServices tenantServices;
  private final RoleServices roleServices;
  private final GroupServices groupServices;
  private final OidcGroupsLoader oidcGroupsLoader;
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
    oidcGroupsLoader =
        new OidcGroupsLoader(securityConfiguration.getAuthentication().getOidc().getGroupsClaim());
    isGroupsClaimConfigured =
        securityConfiguration.getAuthentication().getOidc().isGroupsClaimConfigured();
  }

  @Override
  public MembershipResolver newResolver(
      final Map<String, Object> tokenClaims,
      final String principalId,
      final PrincipalType principalType)
      throws OAuth2AuthenticationException {
    // OIDC groups-claim parsing is in-memory token validation, not a DB call: evaluate it
    // eagerly so malformed claims fail fast at authentication time.
    final List<String> eagerGroupsFromClaims =
        isGroupsClaimConfigured ? List.copyOf(oidcGroupsLoader.load(tokenClaims)) : null;
    return new Resolver(tokenClaims, principalId, principalType, eagerGroupsFromClaims);
  }

  /**
   * Per-authentication resolver that memoizes prerequisite lookups so that when multiple membership
   * fields are read on the same authentication, shared upstream queries
   * (mappingRules→groups→roles→tenants) only run once. Synchronized so concurrent reads on
   * different lazy fields don't double-fetch.
   */
  private final class Resolver implements MembershipResolver {
    private final Map<String, Object> tokenClaims;
    private final List<String> eagerGroupsFromClaims;
    private final EnumMap<EntityType, Set<String>> ownerTypeToIds = new EnumMap<>(EntityType.class);

    private List<String> mappingRules;
    private List<String> groups;
    private List<String> roles;
    private List<String> tenants;

    Resolver(
        final Map<String, Object> tokenClaims,
        final String principalId,
        final PrincipalType principalType,
        final List<String> eagerGroupsFromClaims) {
      this.tokenClaims = tokenClaims;
      this.eagerGroupsFromClaims = eagerGroupsFromClaims;
      ownerTypeToIds.put(
          principalType.equals(PrincipalType.USER) ? EntityType.USER : EntityType.CLIENT,
          Set.of(principalId));
    }

    @Override
    public synchronized List<String> mappingRules() {
      if (mappingRules == null) {
        if (tokenClaims.isEmpty()) {
          mappingRules = List.of();
          return mappingRules;
        }

        final var ids =
            mappingRuleServices
                .withAuthentication(CamundaAuthentication.anonymous())
                .getMatchingMappingRules(tokenClaims)
                .map(MappingRuleEntity::mappingRuleId)
                .collect(Collectors.toSet());
        if (!ids.isEmpty()) {
          ownerTypeToIds.put(MAPPING_RULE, ids);
        } else {
          LOG.debug("No mappingRules found for claim keys: {}", tokenClaims.keySet());
        }
        mappingRules = List.copyOf(ids);
      }
      return mappingRules;
    }

    @Override
    public synchronized List<String> groups() {
      if (groups == null) {
        final Set<String> ids;
        if (eagerGroupsFromClaims != null) {
          // Claims path: the group ids come straight from the JWT, in memory. No DB lookup, so
          // we don't need MAPPING_RULE seeded into ownerTypeToIds — skip mappingRules() so a
          // groups-only read on the broker hot path stays free of DB queries.
          ids = new HashSet<>(eagerGroupsFromClaims);
        } else {
          // DB path: the group lookup keys off ownerTypeToIds, which must include MAPPING_RULE
          // when any mapping rules matched the claims.
          mappingRules();
          ids =
              groupServices
                  .withAuthentication(CamundaAuthentication.anonymous())
                  .getGroupsByMemberTypeAndMemberIds(ownerTypeToIds)
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
    public synchronized List<String> roles() {
      if (roles == null) {
        // Roles look up against ownerTypeToIds and need MAPPING_RULE seeded too. groups() on the
        // claims path deliberately skips that, so re-establish the dependency here explicitly.
        mappingRules();
        groups();

        final var ids =
            roleServices
                .withAuthentication(CamundaAuthentication.anonymous())
                .getRolesByMemberTypeAndMemberIds(ownerTypeToIds)
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
    public synchronized List<String> tenants() {
      if (tenants == null) {
        roles();

        tenants =
            tenantServices
                .withAuthentication(CamundaAuthentication.anonymous())
                .getTenantsByMemberTypeAndMemberIds(ownerTypeToIds)
                .stream()
                .map(TenantEntity::tenantId)
                .toList();
      }
      return tenants;
    }
  }
}
