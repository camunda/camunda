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

import io.camunda.authentication.entity.OAuthContext;
import io.camunda.authentication.service.PrincipalExtractionHelper.PrincipalExtractionResult;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.service.TenantServices.TenantDTO;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.util.StringUtils;

public class SecondaryStorageMembershipService implements MembershipService {
  private static final Logger LOG =
      LoggerFactory.getLogger(SecondaryStorageMembershipService.class);

  private final MappingRuleServices mappingRuleServices;
  private final TenantServices tenantServices;
  private final RoleServices roleServices;
  private final GroupServices groupServices;
  private final AuthorizationServices authorizationServices;
  private final String groupsClaim;
  private final io.camunda.security.auth.OidcGroupsLoader oidcGroupsLoader;

  public SecondaryStorageMembershipService(
      final MappingRuleServices mappingRuleServices,
      final TenantServices tenantServices,
      final RoleServices roleServices,
      final GroupServices groupServices,
      final AuthorizationServices authorizationServices,
      final String groupsClaim,
      final io.camunda.security.auth.OidcGroupsLoader oidcGroupsLoader) {
    this.mappingRuleServices = mappingRuleServices;
    this.tenantServices = tenantServices;
    this.roleServices = roleServices;
    this.groupServices = groupServices;
    this.authorizationServices = authorizationServices;
    this.groupsClaim = groupsClaim;
    this.oidcGroupsLoader = oidcGroupsLoader;
  }

  @Override
  public OAuthContext resolveMemberships(
      final Map<String, Object> claims, final PrincipalExtractionResult principalResult)
      throws OAuth2AuthenticationException {
    final var authContextBuilder = principalResult.authContextBuilder;
    final var ownerTypeToIds = principalResult.ownerTypeToIds;

    final var mappingRules =
        mappingRuleServices
            .withAuthentication(CamundaAuthentication.anonymous())
            .getMatchingMappingRules(claims);
    final Set<String> mappingIds =
        mappingRules.map(MappingRuleEntity::mappingRuleId).collect(Collectors.toSet());
    if (mappingIds.isEmpty()) {
      LOG.debug("No mappingRules found for these claims: {}", claims);
    } else {
      ownerTypeToIds.put(MAPPING_RULE, mappingIds);
    }

    final Set<String> groups;
    final boolean groupsClaimPresent = StringUtils.hasText(groupsClaim);
    if (groupsClaimPresent) {
      groups = new HashSet<>(oidcGroupsLoader.load(claims));
    } else {
      groups =
          groupServices
              .withAuthentication(CamundaAuthentication.anonymous())
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
            .withAuthentication(CamundaAuthentication.anonymous())
            .getRolesByMemberTypeAndMemberIds(ownerTypeToIds);
    final var roleIds = roles.stream().map(RoleEntity::roleId).collect(Collectors.toSet());
    if (!roleIds.isEmpty()) {
      ownerTypeToIds.put(EntityType.ROLE, roleIds);
    }

    final var tenants =
        tenantServices
            .withAuthentication(CamundaAuthentication.anonymous())
            .getTenantsByMemberTypeAndMemberIds(ownerTypeToIds)
            .stream()
            .map(TenantDTO::fromEntity)
            .toList();

    final var authorizedApplications =
        authorizationServices
            .withAuthentication(CamundaAuthentication.anonymous())
            .getAuthorizedApplications(ownerTypeToIds);

    authContextBuilder
        .withAuthorizedApplications(authorizedApplications)
        .withTenants(tenants)
        .withGroups(groups.stream().toList())
        .withRoles(roles)
        .withGroupsClaimEnabled(groupsClaimPresent);

    return new OAuthContext(mappingIds, authContextBuilder.build());
  }
}
