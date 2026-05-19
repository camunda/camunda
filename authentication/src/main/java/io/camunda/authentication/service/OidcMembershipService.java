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

import io.camunda.authentication.ConditionalOnAuthenticationMethod;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.TenantEntity;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.core.oidc.OidcGroupsExtractor;
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
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
@ConditionalOnSecondaryStorageEnabled
public class OidcMembershipService implements MembershipService {
  private static final Logger LOG = LoggerFactory.getLogger(OidcMembershipService.class);

  private final MappingRuleServices mappingRuleServices;
  private final TenantServices tenantServices;
  private final RoleServices roleServices;
  private final GroupServices groupServices;
  private final OidcGroupsExtractor oidcGroupsExtractor;
  private final boolean isGroupsClaimConfigured;

  public OidcMembershipService(
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
      final String principalId, final PrincipalType type, final Map<String, Object> claims)
      throws OAuth2AuthenticationException {
    // OIDC groups-claim parsing is in-memory token validation, not a DB call: evaluate it
    // eagerly so malformed claims fail fast at authentication time.
    final List<String> eagerGroupsFromClaims =
        isGroupsClaimConfigured ? List.copyOf(oidcGroupsExtractor.extract(claims)) : null;
    return new Provider(claims, principalId, type, eagerGroupsFromClaims);
  }

  /**
   * Per-authentication provider that memoizes prerequisite lookups so that when multiple membership
   * fields are read on the same authentication, shared upstream queries
   * (mappingRules→groups→roles→tenants) only run once. Synchronized so concurrent reads on
   * different lazy fields don't double-fetch.
   */
  private final class Provider implements MembershipProvider {
    private final Map<String, Object> tokenClaims;
    private final List<String> eagerGroupsFromClaims;
    private final EnumMap<EntityType, Set<String>> ownerTypeToIds = new EnumMap<>(EntityType.class);

    private List<String> mappingRules;
    private List<String> groups;
    private List<String> roles;
    private List<String> tenants;

    Provider(
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
    public synchronized List<String> groups() {
      if (groups == null) {
        // mappingRules must run first so ownerTypeToIds includes MAPPING_RULE before the group
        // lookup uses it.
        mappingRules();

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
    public synchronized List<String> roles() {
      if (roles == null) {
        groups();

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
    public synchronized List<String> tenants() {
      if (tenants == null) {
        roles();

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
