/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import io.camunda.authentication.entity.AuthenticationContext.AuthenticationContextBuilder;
import io.camunda.authentication.entity.OAuthContext;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.MappingEntity;
import io.camunda.search.entities.RoleEntity;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
public class CamundaOAuthPrincipalService {
  static final String CLAIM_NOT_STRING_OR_STRING_ARRAY =
      "Configured claim for %s (%s) is not a string or string array. Please check your OIDC configuration.";

  private static final Logger LOG = LoggerFactory.getLogger(CamundaOAuthPrincipalService.class);

  private final MappingServices mappingServices;
  private final TenantServices tenantServices;
  private final RoleServices roleServices;
  private final GroupServices groupServices;
  private final AuthorizationServices authorizationServices;
  private final OidcPrincipalLoader oidcPrincipalLoader;
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
    oidcPrincipalLoader =
        new OidcPrincipalLoader(
            securityConfiguration.getAuthentication().getOidc().getUsernameClaim(),
            securityConfiguration.getAuthentication().getOidc().getClientIdClaim());
    usernameClaim = securityConfiguration.getAuthentication().getOidc().getUsernameClaim();
    clientIdClaim = securityConfiguration.getAuthentication().getOidc().getClientIdClaim();
    groupsClaim = securityConfiguration.getAuthentication().getOidc().getGroupsClaim();
  }

  public OAuthContext loadOAuthContext(final Map<String, Object> claims)
      throws OAuth2AuthenticationException {
    final var mappings = mappingServices.getMatchingMappings(claims);
    final Set<String> mappingIds =
        mappings.map(MappingEntity::mappingId).collect(Collectors.toSet());
    if (mappingIds.isEmpty()) {
      LOG.debug("No mappings found for these claims: {}", claims);
    }

    final Set<String> groups;
    if (StringUtils.isNoneEmpty(groupsClaim)) {
      groups = getGroupsFromClaims(claims);
    } else {
      groups =
          groupServices.getGroupsByMemberIds(mappingIds, EntityType.MAPPING).stream()
              .map(GroupEntity::groupId)
              .collect(Collectors.toSet());
    }

    final var roles = roleServices.getRolesByMappingsAndGroups(mappingIds, groups);
    final var roleIds = roles.stream().map(RoleEntity::roleId).collect(Collectors.toSet());

    final var tenants =
        tenantServices.getTenantsByMappingsAndGroupsAndRoles(mappingIds, groups, roleIds).stream()
            .map(TenantDTO::fromEntity)
            .toList();

    final var authContextBuilder =
        new AuthenticationContextBuilder()
            .withAuthorizedApplications(
                authorizationServices.getAuthorizedApplications(
                    Stream.concat(roles.stream().map(RoleEntity::roleId), mappingIds.stream())
                        .collect(Collectors.toSet())))
            .withTenants(tenants)
            .withGroups(groups.stream().toList())
            .withRoles(roles);

    final var principals = oidcPrincipalLoader.load(claims);
    final var username = principals.username();
    final var clientId = principals.clientId();

    if (username == null && clientId == null) {
      throw new IllegalArgumentException(
          "Neither username claim (%s) nor clientId claim (%s) could be found in the claims. Please check your OIDC configuration."
              .formatted(usernameClaim, clientIdClaim));
    }
    if (username != null) {
      authContextBuilder.withUsername(username);
    }

    if (clientId != null) {
      authContextBuilder.withClientId(clientId);
    }

    return new OAuthContext(mappingIds, authContextBuilder.build());
  }

  private Set<String> getGroupsFromClaims(final Map<String, Object> claims) {
    final var claimGroups = claims.get(groupsClaim);
    final var groups = new HashSet<String>();
    if (claimGroups != null) {
      if (claimGroups instanceof String) {
        groups.add((String) claimGroups);
      } else if (claimGroups instanceof final List<?> list) {
        for (final Object o : list) {
          if (o instanceof String) {
            groups.add((String) o);
          } else {
            throw new IllegalArgumentException(
                String.format(CLAIM_NOT_STRING_OR_STRING_ARRAY, "groups", groupsClaim));
          }
        }
      } else {
        throw new IllegalArgumentException(
            String.format(CLAIM_NOT_STRING_OR_STRING_ARRAY, "groups", groupsClaim));
      }
    }
    return groups;
  }
}
