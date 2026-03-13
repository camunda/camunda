/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.adapter;

import static io.camunda.zeebe.protocol.record.value.EntityType.GROUP;
import static io.camunda.zeebe.protocol.record.value.EntityType.MAPPING_RULE;

import io.camunda.gatekeeper.auth.OidcGroupsLoader;
import io.camunda.gatekeeper.config.AuthenticationConfig;
import io.camunda.gatekeeper.model.identity.CamundaAuthentication;
import io.camunda.gatekeeper.model.identity.PrincipalType;
import io.camunda.gatekeeper.spi.MembershipResolver;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.TenantEntity;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageEnabled;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnSecondaryStorageEnabled
public final class DefaultMembershipResolverAdapter implements MembershipResolver {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultMembershipResolverAdapter.class);

  private final MappingRuleServices mappingRuleServices;
  private final TenantServices tenantServices;
  private final RoleServices roleServices;
  private final GroupServices groupServices;
  private final OidcGroupsLoader oidcGroupsLoader;
  private final boolean isGroupsClaimConfigured;

  public DefaultMembershipResolverAdapter(
      final MappingRuleServices mappingRuleServices,
      final TenantServices tenantServices,
      final RoleServices roleServices,
      final GroupServices groupServices,
      final AuthenticationConfig authenticationConfig) {
    this.mappingRuleServices = mappingRuleServices;
    this.tenantServices = tenantServices;
    this.roleServices = roleServices;
    this.groupServices = groupServices;
    if (authenticationConfig.oidc() != null) {
      oidcGroupsLoader = new OidcGroupsLoader(authenticationConfig.oidc().groupsClaim());
      isGroupsClaimConfigured = authenticationConfig.oidc().isGroupsClaimConfigured();
    } else {
      oidcGroupsLoader = new OidcGroupsLoader(null);
      isGroupsClaimConfigured = false;
    }
  }

  @Override
  public CamundaAuthentication resolveMemberships(
      final Map<String, Object> tokenClaims,
      final String principalId,
      final PrincipalType principalType) {
    final var ownerTypeToIds = new HashMap<EntityType, Set<String>>();

    ownerTypeToIds.put(
        principalType == PrincipalType.USER ? EntityType.USER : EntityType.CLIENT,
        Set.of(principalId));

    final var anonymousAuth = CamundaAuthentication.anonymous();

    final var mappingRules =
        mappingRuleServices
            .getMatchingMappingRules(tokenClaims, anonymousAuth)
            .map(MappingRuleEntity::mappingRuleId)
            .collect(Collectors.toSet());

    if (!mappingRules.isEmpty()) {
      ownerTypeToIds.put(MAPPING_RULE, mappingRules);
    } else {
      LOG.debug("No mappingRules found for these claims: {}", tokenClaims);
    }

    final Set<String> groups;
    if (isGroupsClaimConfigured) {
      groups = new HashSet<>(oidcGroupsLoader.load(tokenClaims));
    } else {
      groups =
          groupServices.getGroupsByMemberTypeAndMemberIds(ownerTypeToIds, anonymousAuth).stream()
              .map(GroupEntity::groupId)
              .collect(Collectors.toSet());
    }

    if (!groups.isEmpty()) {
      ownerTypeToIds.put(GROUP, groups);
    }

    final var roles =
        roleServices.getRolesByMemberTypeAndMemberIds(ownerTypeToIds, anonymousAuth).stream()
            .map(RoleEntity::roleId)
            .collect(Collectors.toSet());

    if (!roles.isEmpty()) {
      ownerTypeToIds.put(EntityType.ROLE, roles);
    }

    final var tenants =
        tenantServices.getTenantsByMemberTypeAndMemberIds(ownerTypeToIds, anonymousAuth).stream()
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
              .claims(tokenClaims);
        });
  }
}
