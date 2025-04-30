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
  static final String USERNAME_CLAIM_NOT_FOUND =
      "Configured username claim %s not found in claims. Please check your OIDC configuration.";
  static final String USERNAME_CLAIM_NOT_STRING =
      "Configured username claim %s is not a string. Please check your OIDC configuration.";
  static final String APPLICATION_ID_CLAIM_NOT_FOUND =
      "Configured applicationId claim %s not found in claims. Please check your OIDC configuration.";
  static final String APPLICATION_ID_CLAIM_NOT_STRING =
      "Configured applicationId claim %s is not a string. Please check your OIDC configuration.";

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
    final Set<String> mappingIds =
        mappings.stream().map(MappingEntity::mappingId).collect(Collectors.toSet());
    if (mappingIds.isEmpty()) {
      LOG.debug("No mappings found for these claims: {}", claims);
    }

    final var assignedRoles = roleServices.getRolesByMemberIds(mappingIds);

    final var authContextBuilder =
        new AuthenticationContextBuilder()
            .withAuthorizedApplications(
                authorizationServices.getAuthorizedApplications(
                    Stream.concat(
                            assignedRoles.stream().map(r -> r.roleKey().toString()),
                            mappingIds.stream())
                        .collect(Collectors.toSet())))
            .withTenants(
                tenantServices.getTenantsByMemberIds(mappingIds).stream()
                    .map(TenantDTO::fromEntity)
                    .toList())
            .withGroups(
                groupServices.getGroupsByMemberKeys(mappingIds).stream()
                    .map(GroupEntity::name)
                    .toList())
            .withRoles(assignedRoles);

    if (usernameClaim != null) {
      authContextBuilder.withUsername(getUsernameFromClaims(claims));
    } else if (applicationIdClaim != null) {
      authContextBuilder.withApplicationId(getApplicationIdFromClaims(claims));
    }

    return new OAuthContext(mappingIds, authContextBuilder.build());
  }

  private String getUsernameFromClaims(final Map<String, Object> claims) {
    final var maybeUsername = Optional.ofNullable(claims.get(usernameClaim));

    if (maybeUsername.isEmpty()) {
      throw new IllegalArgumentException(USERNAME_CLAIM_NOT_FOUND.formatted(usernameClaim));
    }

    if (maybeUsername.get() instanceof final String username) {
      return username;
    } else {
      throw new IllegalArgumentException(USERNAME_CLAIM_NOT_STRING.formatted(usernameClaim));
    }
  }

  private String getApplicationIdFromClaims(final Map<String, Object> claims) {
    final var maybeApplicationId = Optional.ofNullable(claims.get(applicationIdClaim));

    if (maybeApplicationId.isEmpty()) {
      throw new IllegalArgumentException(
          APPLICATION_ID_CLAIM_NOT_FOUND.formatted(applicationIdClaim));
    }

    if (maybeApplicationId.get() instanceof final String applicationId) {
      return applicationId;
    } else {
      throw new IllegalArgumentException(
          APPLICATION_ID_CLAIM_NOT_STRING.formatted(applicationIdClaim));
    }
  }
}
