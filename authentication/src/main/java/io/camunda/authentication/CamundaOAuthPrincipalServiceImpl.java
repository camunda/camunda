/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import static io.camunda.zeebe.protocol.record.value.EntityType.CLIENT;
import static io.camunda.zeebe.protocol.record.value.EntityType.GROUP;
import static io.camunda.zeebe.protocol.record.value.EntityType.MAPPING_RULE;
import static io.camunda.zeebe.protocol.record.value.EntityType.USER;

import io.camunda.authentication.entity.AuthenticationContext.AuthenticationContextBuilder;
import io.camunda.authentication.entity.OAuthContext;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.OidcGroupsLoader;
import io.camunda.security.auth.OidcPrincipalLoader;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.service.TenantServices.TenantDTO;
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
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
public class CamundaOAuthPrincipalServiceImpl implements CamundaOAuthPrincipalService {

  private static final Logger LOG = LoggerFactory.getLogger(CamundaOAuthPrincipalServiceImpl.class);

  private final MappingRuleServices mappingRuleServices;
  private final TenantServices tenantServices;
  private final RoleServices roleServices;
  private final GroupServices groupServices;
  private final OidcPrincipalLoader oidcPrincipalLoader;
  private final OidcGroupsLoader oidcGroupsLoader;
  private final String usernameClaim;
  private final String clientIdClaim;
  private final String groupsClaim;

  public CamundaOAuthPrincipalServiceImpl(
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

  @Override
  public OAuthContext loadOAuthContext(final Map<String, Object> claims)
      throws OAuth2AuthenticationException {
    final var authContextBuilder = new AuthenticationContextBuilder();
    final var principals = oidcPrincipalLoader.load(claims);
    final var username = principals.username();
    final var clientId = principals.clientId();

    final var ownerTypeToIds = new HashMap<EntityType, Set<String>>();

    if (username == null && clientId == null) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error(OAuth2ErrorCodes.INVALID_CLIENT),
          "Neither username claim (%s) nor clientId claim (%s) could be found in the claims. Please check your OIDC configuration."
              .formatted(usernameClaim, clientIdClaim));
    }
    if (username != null) {
      authContextBuilder.withUsername(username);
      ownerTypeToIds.put(USER, Set.of(username));
    }

    if (clientId != null) {
      authContextBuilder.withClientId(clientId);
      ownerTypeToIds.put(CLIENT, Set.of(clientId));
    }

    final var mappingRules =
        mappingRuleServices
            .withAuthentication(CamundaAuthentication.anonymous())
            .getMatchingMappingRules(claims);
    final Set<String> mappingRuleIds =
        mappingRules.map(MappingRuleEntity::mappingRuleId).collect(Collectors.toSet());
    if (mappingRuleIds.isEmpty()) {
      LOG.debug("No mappingRules found for these claims: {}", claims);
    } else {
      ownerTypeToIds.put(MAPPING_RULE, mappingRuleIds);
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
            .map(TenantDTO::fromEntity)
            .toList();

    authContextBuilder
        .withTenants(tenants)
        .withGroups(groups.stream().toList())
        .withRoles(roles.stream().toList())
        .withGroupsClaimEnabled(groupsClaimPresent);

    return new OAuthContext(mappingRuleIds, authContextBuilder.build());
  }
}
