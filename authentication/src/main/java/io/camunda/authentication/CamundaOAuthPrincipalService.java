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
import static io.camunda.zeebe.protocol.record.value.EntityType.MAPPING;
import static io.camunda.zeebe.protocol.record.value.EntityType.USER;

import io.camunda.authentication.entity.AuthenticationContext.AuthenticationContextBuilder;
import io.camunda.authentication.entity.OAuthContext;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.MappingEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.query.RoleQuery;
import io.camunda.security.auth.OidcGroupsLoader;
import io.camunda.security.auth.OidcPrincipalLoader;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingServices;
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
public class CamundaOAuthPrincipalService {

  private static final Logger LOG = LoggerFactory.getLogger(CamundaOAuthPrincipalService.class);

  private final MappingServices mappingServices;
  private final TenantServices tenantServices;
  private final RoleServices roleServices;
  private final GroupServices groupServices;
  private final AuthorizationServices authorizationServices;
  private final OidcPrincipalLoader oidcPrincipalLoader;
  private final OidcGroupsLoader oidcGroupsLoader;
  private final String usernameClaim;
  private final String clientIdClaim;
  private final String groupsClaim;

  public CamundaOAuthPrincipalService(
      final MappingServices mappingServices,
      final TenantServices tenantServices,
      final RoleServices roleServices,
      final GroupServices groupServices,
      final AuthorizationServices authorizationServices,
      final SecurityConfiguration securityConfiguration) {
    this.mappingServices = mappingServices;
    this.tenantServices = tenantServices;
    this.roleServices = roleServices;
    this.groupServices = groupServices;
    this.authorizationServices = authorizationServices;
    usernameClaim = securityConfiguration.getAuthentication().getOidc().getUsernameClaim();
    clientIdClaim = securityConfiguration.getAuthentication().getOidc().getClientIdClaim();
    groupsClaim = securityConfiguration.getAuthentication().getOidc().getGroupsClaim();
    oidcPrincipalLoader = new OidcPrincipalLoader(usernameClaim, clientIdClaim);
    oidcGroupsLoader = new OidcGroupsLoader(groupsClaim);
  }

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

    final var mappings = mappingServices.getMatchingMappings(claims);
    final Set<String> mappingIds =
        mappings.map(MappingEntity::mappingId).collect(Collectors.toSet());
    if (mappingIds.isEmpty()) {
      LOG.debug("No mappings found for these claims: {}", claims);
    } else {
      ownerTypeToIds.put(MAPPING, mappingIds);
    }

    final Set<String> groups;
    if (StringUtils.hasText(groupsClaim)) {
      groups = new HashSet<>(oidcGroupsLoader.load(claims));
    } else {
      // TODO: Get groups for username and clientId https://github.com/camunda/camunda/issues/26572
      groups =
          groupServices.getGroupsByMemberIds(mappingIds, MAPPING).stream()
              .map(GroupEntity::groupId)
              .collect(Collectors.toSet());
      if (!groups.isEmpty()) {
        ownerTypeToIds.put(GROUP, groups);
      }
    }

    final var roles =
        roleServices.findAll(
            RoleQuery.of(
                roleQuery ->
                    roleQuery.filter(roleFilter -> roleFilter.memberIdsByType(ownerTypeToIds))));
    final var roleIds = roles.stream().map(RoleEntity::roleId).collect(Collectors.toSet());
    if (!roleIds.isEmpty()) {
      ownerTypeToIds.put(EntityType.ROLE, roleIds);
    }

    // TODO: Get tenants for username and clientId https://github.com/camunda/camunda/issues/26572
    final var tenants =
        tenantServices.getTenantsByMappingsAndGroupsAndRoles(mappingIds, groups, roleIds).stream()
            .map(TenantDTO::fromEntity)
            .toList();

    final var ownerIds =
        ownerTypeToIds.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
    final var authorizedApplications = authorizationServices.getAuthorizedApplications(ownerIds);

    authContextBuilder
        .withAuthorizedApplications(authorizedApplications)
        .withTenants(tenants)
        .withGroups(groups.stream().toList())
        .withRoles(roles);

    return new OAuthContext(mappingIds, authContextBuilder.build());
  }
}
