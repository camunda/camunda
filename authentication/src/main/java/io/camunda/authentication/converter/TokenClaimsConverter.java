/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.converter;

import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_CLIENT_ID;
import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_USERNAME;
import static io.camunda.zeebe.auth.Authorization.USER_GROUPS_CLAIMS;
import static io.camunda.zeebe.auth.Authorization.USER_TOKEN_CLAIMS;
import static io.camunda.zeebe.protocol.record.value.EntityType.CLIENT;
import static io.camunda.zeebe.protocol.record.value.EntityType.GROUP;
import static io.camunda.zeebe.protocol.record.value.EntityType.MAPPING_RULE;
import static io.camunda.zeebe.protocol.record.value.EntityType.USER;

import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.TenantEntity;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.OidcGroupsLoader;
import io.camunda.security.auth.OidcPrincipalLoader;
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
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.util.StringUtils;

public class TokenClaimsConverter {

  private static final Logger LOGGER = LoggerFactory.getLogger(TokenClaimsConverter.class);

  private final MappingRuleServices mappingRuleServices;
  private final TenantServices tenantServices;
  private final RoleServices roleServices;
  private final GroupServices groupServices;
  private final OidcPrincipalLoader oidcPrincipalLoader;
  private final OidcGroupsLoader oidcGroupsLoader;
  private final String usernameClaim;
  private final String clientIdClaim;
  private final String groupsClaim;

  public TokenClaimsConverter(
      final MappingRuleServices mappingRuleServices,
      final TenantServices tenantServices,
      final RoleServices roleServices,
      final GroupServices groupServices,
      final SecurityConfiguration securityConfiguration) {
    this.mappingRuleServices = mappingRuleServices;
    this.tenantServices = tenantServices;
    this.roleServices = roleServices;
    this.groupServices = groupServices;
    usernameClaim = securityConfiguration.getAuthentication().getOidc().getUsernameClaim();
    clientIdClaim = securityConfiguration.getAuthentication().getOidc().getClientIdClaim();
    groupsClaim = securityConfiguration.getAuthentication().getOidc().getGroupsClaim();
    oidcPrincipalLoader = new OidcPrincipalLoader(usernameClaim, clientIdClaim);
    oidcGroupsLoader = new OidcGroupsLoader(groupsClaim);
  }

  public CamundaAuthentication convert(final Map<String, Object> tokenClaims) {
    final var principals = oidcPrincipalLoader.load(tokenClaims);
    final var username = principals.username();
    final var clientId = principals.clientId();

    // will be used when sending a broker request
    final var authenticatedClaims = new HashMap<String, Object>();
    authenticatedClaims.put(USER_TOKEN_CLAIMS, tokenClaims);

    final var ownerTypeToIds = new HashMap<EntityType, Set<String>>();

    if (username == null && clientId == null) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error(OAuth2ErrorCodes.INVALID_CLIENT),
          "Neither username claim (%s) nor clientId claim (%s) could be found in the claims. Please check your OIDC configuration."
              .formatted(usernameClaim, clientIdClaim));
    }
    if (username != null) {
      authenticatedClaims.put(AUTHORIZED_USERNAME, username);
      ownerTypeToIds.put(USER, Set.of(username));
    }

    if (clientId != null) {
      authenticatedClaims.put(AUTHORIZED_CLIENT_ID, clientId);
      ownerTypeToIds.put(CLIENT, Set.of(clientId));
    }

    final var mappingRules =
        mappingRuleServices
            .withAuthentication(CamundaAuthentication.anonymous())
            .getMatchingMappingRules(tokenClaims)
            .map(MappingRuleEntity::mappingRuleId)
            .collect(Collectors.toSet());

    if (!mappingRules.isEmpty()) {
      ownerTypeToIds.put(MAPPING_RULE, mappingRules);
    } else {
      LOGGER.debug("No mappingRules found for these claims: {}", tokenClaims);
    }

    final Set<String> groups;
    final boolean groupsClaimPresent = StringUtils.hasText(groupsClaim);
    if (groupsClaimPresent) {
      groups = new HashSet<>(oidcGroupsLoader.load(tokenClaims));
      authenticatedClaims.put(USER_GROUPS_CLAIMS, groups.stream().toList());
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
            .getRolesByMemberTypeAndMemberIds(ownerTypeToIds)
            .stream()
            .map(RoleEntity::roleId)
            .collect(Collectors.toSet());

    if (!roles.isEmpty()) {
      ownerTypeToIds.put(EntityType.ROLE, roles);
    }

    final var tenants =
        tenantServices
            .withAuthentication(CamundaAuthentication.anonymous())
            .getTenantsByMemberTypeAndMemberIds(ownerTypeToIds)
            .stream()
            .map(TenantEntity::tenantId)
            .toList();

    return CamundaAuthentication.of(
        a ->
            a.user(username)
                .clientId(clientId)
                .roleIds(roles.stream().toList())
                .groupIds(groups.stream().toList())
                .mappingRule(mappingRules.stream().toList())
                .tenants(tenants)
                .claims(authenticatedClaims));
  }
}
