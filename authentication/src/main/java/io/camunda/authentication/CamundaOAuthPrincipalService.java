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
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.service.TenantServices.TenantDTO;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
public class CamundaOAuthPrincipalService {
  static final String CLAIM_NOT_STRING =
      "Configured claim for %s (%s) is not a string. Please check your OIDC configuration.";

  private static final Logger LOG = LoggerFactory.getLogger(CamundaOAuthPrincipalService.class);

  private final MappingServices mappingServices;
  private final TenantServices tenantServices;
  private final RoleServices roleServices;
  private final GroupServices groupServices;
  private final AuthorizationServices authorizationServices;
  private final String usernameClaim;
  private final String applicationIdClaim;

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
    applicationIdClaim =
        securityConfiguration.getAuthentication().getOidc().getApplicationIdClaim();
  }

  public OAuthContext loadOAuthContext(final Map<String, Object> claims)
      throws OAuth2AuthenticationException {
    final List<MappingEntity> mappings = mappingServices.getMatchingMappings(claims);
    final Set<String> mappingRuleIds =
        mappings.stream().map(MappingEntity::mappingRuleId).collect(Collectors.toSet());
    if (mappingRuleIds.isEmpty()) {
      LOG.debug("No mappings found for these claims: {}", claims);
    }

    final var assignedRoles = roleServices.getRolesByMemberIds(mappingRuleIds);

    final var authContextBuilder =
        new AuthenticationContextBuilder()
            .withAuthorizedApplications(
                authorizationServices.getAuthorizedApplications(
                    Stream.concat(
                            assignedRoles.stream().map(r -> r.roleKey().toString()),
                            mappingRuleIds.stream())
                        .collect(Collectors.toSet())))
            .withTenants(
                tenantServices.getTenantsByMemberIds(mappingRuleIds).stream()
                    .map(TenantDTO::fromEntity)
                    .toList())
            .withGroups(
                groupServices.getGroupsByMemberKeys(mappingRuleIds).stream()
                    .map(GroupEntity::name)
                    .toList())
            .withRoles(assignedRoles);

    final var username = getUsernameFromClaims(claims);
    final var applicationId = getApplicationIdFromClaims(claims);

    if (username == null && applicationId == null) {
      throw new IllegalArgumentException(
          "Neither username claim (%s) nor applicationId claim (%s) could be found in the claims. Please check your OIDC configuration."
              .formatted(usernameClaim, applicationIdClaim));
    }
    if (username != null) {
      authContextBuilder.withUsername(getUsernameFromClaims(claims));
    }

    if (applicationId != null) {
      authContextBuilder.withApplicationId(getApplicationIdFromClaims(claims));
    }

    return new OAuthContext(mappingRuleIds, authContextBuilder.build());
  }

  private String getUsernameFromClaims(final Map<String, Object> claims) {
    final var maybeUsername = Optional.ofNullable(claims.get(usernameClaim));

    if (maybeUsername.isEmpty()) {
      return null;
    }

    if (maybeUsername.get() instanceof final String username) {
      return username;
    } else {
      throw new IllegalArgumentException(CLAIM_NOT_STRING.formatted("username", usernameClaim));
    }
  }

  private String getApplicationIdFromClaims(final Map<String, Object> claims) {
    final var maybeApplicationId = Optional.ofNullable(claims.get(applicationIdClaim));

    if (maybeApplicationId.isEmpty()) {
      return null;
    }

    if (maybeApplicationId.get() instanceof final String applicationId) {
      return applicationId;
    } else {
      throw new IllegalArgumentException(
          CLAIM_NOT_STRING.formatted("application", applicationIdClaim));
    }
  }
}
