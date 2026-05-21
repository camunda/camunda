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
import static io.camunda.security.api.model.authz.EntityType.ROLE;
import static io.camunda.security.api.model.authz.EntityType.USER;

import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.TenantEntity;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.auth.Memberships;
import io.camunda.security.api.model.authz.EntityType;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.core.oidc.OidcGroupsExtractor;
import io.camunda.security.core.port.out.MembershipPort;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageEnabled;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

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
  public Memberships resolveMemberships(
      final Map<String, Object> tokenClaims,
      final String principalId,
      final PrincipalType principalType) {
    final var ownerTypeToIds = new HashMap<EntityType, Set<String>>();

    ownerTypeToIds.put(
        principalType.equals(PrincipalType.USER) ? USER : EntityType.CLIENT,
        Set.of(principalId));

    final var mappingRules =
        mappingRuleServices
            .getMatchingMappingRules(tokenClaims, CamundaAuthentication.anonymous())
            .map(MappingRuleEntity::mappingRuleId)
            .collect(Collectors.toSet());

    if (!mappingRules.isEmpty()) {
      ownerTypeToIds.put(MAPPING_RULE, mappingRules);
    } else {
      LOG.debug("No mappingRules found for these claims: {}", tokenClaims);
    }

    final Set<String> groups;
    if (isGroupsClaimConfigured) {
      groups = new HashSet<>(oidcGroupsExtractor.extract(tokenClaims));
    } else {
      groups =
          groupServices
              .getGroupsByMemberTypeAndMemberIds(ownerTypeToIds, CamundaAuthentication.anonymous())
              .stream()
              .map(GroupEntity::groupId)
              .collect(Collectors.toSet());
    }

    if (!groups.isEmpty()) {
      ownerTypeToIds.put(GROUP, groups);
    }

    final var roles =
        roleServices
            .getRolesByMemberTypeAndMemberIds(ownerTypeToIds, CamundaAuthentication.anonymous())
            .stream()
            .map(RoleEntity::roleId)
            .collect(Collectors.toSet());

    if (!roles.isEmpty()) {
      ownerTypeToIds.put(ROLE, roles);
    }

    final var tenants =
        tenantServices
            .getTenantsByMemberTypeAndMemberIds(ownerTypeToIds, CamundaAuthentication.anonymous())
            .stream()
            .map(TenantEntity::tenantId)
            .toList();

    return new Memberships(
        groups.stream().toList(),
        roles.stream().toList(),
        tenants,
        mappingRules.stream().toList());
  }

  @Override
  public Memberships resolveMembershipsForUser(final String username) {
    final var ownerTypeToIds = new HashMap<EntityType, Set<String>>();
    ownerTypeToIds.put(USER, Set.of(username));

    final var groups =
        groupServices
            .getGroupsByMemberTypeAndMemberIds(ownerTypeToIds, CamundaAuthentication.anonymous())
            .stream()
            .map(GroupEntity::groupId)
            .collect(Collectors.toSet());

    if (!groups.isEmpty()) {
      ownerTypeToIds.put(GROUP, groups);
    }

    final var roles =
        roleServices
            .getRolesByMemberTypeAndMemberIds(ownerTypeToIds, CamundaAuthentication.anonymous())
            .stream()
            .map(RoleEntity::roleId)
            .collect(Collectors.toSet());

    if (!roles.isEmpty()) {
      ownerTypeToIds.put(ROLE, roles);
    }

    final var tenants =
        tenantServices
            .getTenantsByMemberTypeAndMemberIds(ownerTypeToIds, CamundaAuthentication.anonymous())
            .stream()
            .map(TenantEntity::tenantId)
            .toList();

    return new Memberships(
        groups.stream().toList(), roles.stream().toList(), tenants, List.of());
  }
}
