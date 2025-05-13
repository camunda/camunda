/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
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
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
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
  private final JwtDecoder jwtDecoder;

  public CamundaOAuthPrincipalService(
      final MappingServices mappingServices,
      final TenantServices tenantServices,
      final RoleServices roleServices,
      final GroupServices groupServices,
      final AuthorizationServices authorizationServices,
      final SecurityConfiguration securityConfiguration,
      final JwtDecoder jwtDecoder) {
    this.mappingServices = mappingServices;
    this.tenantServices = tenantServices;
    this.roleServices = roleServices;
    this.groupServices = groupServices;
    this.authorizationServices = authorizationServices;
    usernameClaim = securityConfiguration.getAuthentication().getOidc().getUsernameClaim();
    applicationIdClaim =
        securityConfiguration.getAuthentication().getOidc().getApplicationIdClaim();
    this.jwtDecoder = jwtDecoder;
  }

  public OAuthContext loadOAuthContext(final String tokenValue)
      throws OAuth2AuthenticationException {
    final var jwtClaims = jwtDecoder.decode(tokenValue).getClaims();
    final List<MappingEntity> mappings = mappingServices.getMatchingMappings(jwtClaims);
    final Set<String> mappingIds =
        mappings.stream().map(MappingEntity::mappingId).collect(Collectors.toSet());
    if (mappingIds.isEmpty()) {
      LOG.debug("No mappings found for these claims: {}", jwtClaims);
    }

    final var assignedRoles = roleServices.getRolesByMemberIds(mappingIds, EntityType.MAPPING);

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

    final var username = getUsernameFromToken(tokenValue);
    final var applicationId = getApplicationIdFromClaims(jwtClaims);

    if (username == null && applicationId == null) {
      throw new IllegalArgumentException(
          "Neither username claim (%s) nor applicationId claim (%s) could be found in the claims. Please check your OIDC configuration."
              .formatted(usernameClaim, applicationIdClaim));
    }
    if (username != null) {
      authContextBuilder.withUsername(getUsernameFromToken(tokenValue));
    }

    if (applicationId != null) {
      authContextBuilder.withApplicationId(getApplicationIdFromClaims(jwtClaims));
    }

    return new OAuthContext(mappingIds, authContextBuilder.build());
  }

  private String getUsernameFromToken(final String token) {
    final var jsonContext = JsonPath.parse(token);
    final String username;

    try {
      username = jsonContext.read(usernameClaim);
    } catch (final ClassCastException e) {
      throw new IllegalArgumentException(CLAIM_NOT_STRING.formatted("username", usernameClaim));
    } catch (final PathNotFoundException e) {
      return null;
    }

    return username;
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
