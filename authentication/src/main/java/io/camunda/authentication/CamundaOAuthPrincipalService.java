/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import io.camunda.authentication.entity.AuthenticationContext;
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

  private static final Logger LOG = LoggerFactory.getLogger(CamundaOAuthPrincipalService.class);

  private final MappingServices mappingServices;
  private final TenantServices tenantServices;
  private final RoleServices roleServices;
  private final GroupServices groupServices;
  private final AuthorizationServices authorizationServices;
  private final String usernameClaim;

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
  }

  public OAuthContext loadOAuthContext(final Map<String, Object> claims)
      throws OAuth2AuthenticationException {
    final List<MappingEntity> mappings = mappingServices.getMatchingMappings(claims);
    final Set<Long> mappingKeys =
        mappings.stream().map(MappingEntity::mappingKey).collect(Collectors.toSet());
    final Set<String> mappingIds =
        mappings.stream().map(MappingEntity::mappingId).collect(Collectors.toSet());
    if (mappingKeys.isEmpty() && mappingIds.isEmpty()) {
      LOG.debug("No mappings found for these claims: {}", claims);
    }

    final var assignedRoles = roleServices.getRolesByMemberKeys(mappingKeys);

    return new OAuthContext(
        mappingKeys,
        mappingIds,
        new AuthenticationContext(
            getUsernameFromClaims(claims),
            assignedRoles,
            authorizationServices.getAuthorizedApplications(
                Stream.concat(
                        assignedRoles.stream().map(r -> r.roleKey().toString()),
                        mappingIds.stream())
                    .collect(Collectors.toSet())),
            tenantServices.getTenantsByMemberIds(mappingIds).stream()
                .map(TenantDTO::fromEntity)
                .toList(),
            groupServices.getGroupsByMemberKeys(mappingIds).stream()
                .map(GroupEntity::name)
                .toList()));
  }

  private String getUsernameFromClaims(final Map<String, Object> claims) {
    return Optional.ofNullable(claims.get(usernameClaim))
        .map(Object::toString)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Configured username claim %s not found in claims. Please check your OIDC configuration."
                        .formatted(usernameClaim)));
  }
}
