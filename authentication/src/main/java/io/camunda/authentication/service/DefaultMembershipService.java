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
import io.camunda.security.api.model.authz.EntityType;
import io.camunda.security.core.oidc.OidcGroupsExtractor;
import io.camunda.security.core.port.out.MembershipPort;
import io.camunda.security.core.port.out.MembershipQuery;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageEnabled;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Host-side {@link MembershipPort} backed by the secondary-storage-driven {@code *Services}. Each
 * per-entity method is a pure function: given the query context (token claims, principal identity,
 * and the IDs resolved by prior chain steps), it runs the corresponding DB lookup and returns the
 * IDs. The lazy evaluation and chain wiring are owned by the CSL converters.
 */
@Service
@Primary
@ConditionalOnSecondaryStorageEnabled
public class DefaultMembershipService implements MembershipPort {
  private static final Logger LOG = LoggerFactory.getLogger(DefaultMembershipService.class);

  private final ServiceRegistry serviceRegistry;
  private final OidcGroupsExtractor oidcGroupsExtractor;
  private final boolean isGroupsClaimConfigured;

  public DefaultMembershipService(
      final ServiceRegistry serviceRegistry, final CamundaSecurityLibraryProperties cslProperties) {
    this.serviceRegistry = serviceRegistry;
    oidcGroupsExtractor =
        new OidcGroupsExtractor(cslProperties.getAuthentication().getOidc().getGroupsClaim());
    isGroupsClaimConfigured = cslProperties.getAuthentication().getOidc().isGroupsClaimConfigured();
  }

  @Override
  public List<String> mappingRuleIds(final MembershipQuery query) {
    if (query.tokenClaims().isEmpty()) {
      // BASIC auth has no claims; nothing can match.
      return List.of();
    }
    final var ids =
        serviceRegistry
            .mappingRuleServices("default") // TODO replace with contextual physicalTenantId
            .getMatchingMappingRules(query.tokenClaims(), CamundaAuthentication.anonymous())
            .map(MappingRuleEntity::mappingRuleId)
            .collect(Collectors.toSet());
    if (ids.isEmpty()) {
      // Log only claim keys — values may contain PII (sub, email, scopes, …) and DEBUG can still
      // reach log aggregators.
      LOG.debug("No mappingRules found for claim keys: {}", query.tokenClaims().keySet());
    }
    return List.copyOf(ids);
  }

  @Override
  public List<String> groupIds(final MembershipQuery query) {
    // OIDC groups-claim path: in-memory extraction. distinct() matches NoDBMembershipService and
    // the previous Set-based semantics.
    if (isGroupsClaimConfigured) {
      final var extracted = oidcGroupsExtractor.extract(query.tokenClaims());
      return extracted != null ? extracted.stream().distinct().toList() : List.of();
    }
    final var owners = buildOwners(query);
    final var ids =
        serviceRegistry
            .groupServices("default") // TODO replace with contextual physicalTenantId
            .getGroupsByMemberTypeAndMemberIds(owners, CamundaAuthentication.anonymous())
            .stream()
            .map(GroupEntity::groupId)
            .collect(Collectors.toSet());
    return List.copyOf(ids);
  }

  @Override
  public List<String> roleIds(final MembershipQuery query) {
    final var owners = buildOwners(query);
    if (!query.resolvedGroupIds().isEmpty()) {
      owners.put(GROUP, new HashSet<>(query.resolvedGroupIds()));
    }
    final var ids =
        serviceRegistry
            .roleServices("default") // TODO replace with contextual physicalTenantId
            .getRolesByMemberTypeAndMemberIds(owners, CamundaAuthentication.anonymous())
            .stream()
            .map(RoleEntity::roleId)
            .collect(Collectors.toSet());
    return List.copyOf(ids);
  }

  @Override
  public List<String> tenantIds(final MembershipQuery query) {
    final var owners = buildOwners(query);
    if (!query.resolvedGroupIds().isEmpty()) {
      owners.put(GROUP, new HashSet<>(query.resolvedGroupIds()));
    }
    if (!query.resolvedRoleIds().isEmpty()) {
      owners.put(EntityType.ROLE, new HashSet<>(query.resolvedRoleIds()));
    }
    return serviceRegistry
        .tenantServices("default") // TODO replace with contextual physicalTenantId
        .getTenantsByMemberTypeAndMemberIds(owners, CamundaAuthentication.anonymous())
        .stream()
        .map(TenantEntity::tenantId)
        .toList();
  }

  private EnumMap<EntityType, Set<String>> buildOwners(final MembershipQuery query) {
    final var owners = new EnumMap<EntityType, Set<String>>(EntityType.class);
    owners.put(
        query.principalType() == PrincipalType.USER ? EntityType.USER : EntityType.CLIENT,
        Set.of(query.principalId()));
    if (!query.resolvedMappingRuleIds().isEmpty()) {
      owners.put(MAPPING_RULE, new HashSet<>(query.resolvedMappingRuleIds()));
    }
    return owners;
  }
}
