/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.authentication;

import static io.camunda.zeebe.protocol.record.value.EntityType.GROUP;
import static io.camunda.zeebe.protocol.record.value.EntityType.MAPPING_RULE;

import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.model.PrincipalType;
import io.camunda.auth.domain.spi.MembershipResolver;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.TenantEntity;
import io.camunda.security.auth.OidcGroupsLoader;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves group, role, tenant, and mapping rule memberships from token claims using the Camunda
 * services layer. This mirrors the logic of {@code DefaultMembershipService} but targets the auth
 * SDK's {@link MembershipResolver} SPI.
 */
public class CamundaMembershipResolver implements MembershipResolver {

  private static final Logger LOG = LoggerFactory.getLogger(CamundaMembershipResolver.class);

  private final MappingRuleServices mappingRuleServices;
  private final TenantServices tenantServices;
  private final RoleServices roleServices;
  private final GroupServices groupServices;
  private final OidcGroupsLoader oidcGroupsLoader;
  private final boolean isGroupsClaimConfigured;

  public CamundaMembershipResolver(
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
  public CamundaAuthentication resolveMemberships(
      final Map<String, Object> claims,
      final String principalId,
      final PrincipalType principalType) {
    final var ownerTypeToIds = new HashMap<EntityType, Set<String>>();

    ownerTypeToIds.put(
        principalType == PrincipalType.USER ? EntityType.USER : EntityType.CLIENT,
        Set.of(principalId));

    final var mappingRules =
        mappingRuleServices
            .withAuthentication(io.camunda.auth.domain.model.CamundaAuthentication.anonymous())
            .getMatchingMappingRules(claims)
            .map(MappingRuleEntity::mappingRuleId)
            .collect(Collectors.toSet());

    if (!mappingRules.isEmpty()) {
      ownerTypeToIds.put(MAPPING_RULE, mappingRules);
    } else {
      LOG.debug("No mappingRules found for these claims: {}", claims);
    }

    final Set<String> groups;
    if (isGroupsClaimConfigured) {
      groups = new HashSet<>(oidcGroupsLoader.load(claims));
    } else {
      groups =
          groupServices
              .withAuthentication(io.camunda.auth.domain.model.CamundaAuthentication.anonymous())
              .getGroupsByMemberTypeAndMemberIds(ownerTypeToIds)
              .stream()
              .map(GroupEntity::groupId)
              .collect(Collectors.toSet());
    }

    if (!groups.isEmpty()) {
      ownerTypeToIds.put(GROUP, groups);
    }

    final var roles =
        roleServices
            .withAuthentication(io.camunda.auth.domain.model.CamundaAuthentication.anonymous())
            .getRolesByMemberTypeAndMemberIds(ownerTypeToIds)
            .stream()
            .map(RoleEntity::roleId)
            .collect(Collectors.toSet());

    if (!roles.isEmpty()) {
      ownerTypeToIds.put(EntityType.ROLE, roles);
    }

    final var tenants =
        tenantServices
            .withAuthentication(io.camunda.auth.domain.model.CamundaAuthentication.anonymous())
            .getTenantsByMemberTypeAndMemberIds(ownerTypeToIds)
            .stream()
            .map(TenantEntity::tenantId)
            .toList();

    return CamundaAuthentication.of(
        a -> {
          if (principalType == PrincipalType.CLIENT) {
            a.clientId(principalId);
          } else {
            a.user(principalId);
          }
          return a.roleIds(roles.stream().toList())
              .groupIds(groups.stream().toList())
              .mappingRule(mappingRules.stream().toList())
              .tenants(tenants)
              .claims(claims);
        });
  }
}
