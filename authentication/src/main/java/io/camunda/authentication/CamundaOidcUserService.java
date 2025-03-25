/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import io.camunda.authentication.entity.AuthenticationContext;
import io.camunda.authentication.entity.CamundaOidcUser;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.MappingEntity;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.service.TenantServices.TenantDTO;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
public class CamundaOidcUserService extends OidcUserService {
  private static final Logger LOG = LoggerFactory.getLogger(CamundaOidcUserService.class);
  private final MappingServices mappingServices;
  private final TenantServices tenantServices;
  private final RoleServices roleServices;
  private final GroupServices groupServices;
  private final AuthorizationServices authorizationServices;

  public CamundaOidcUserService(
      final MappingServices mappingServices,
      final TenantServices tenantServices,
      final RoleServices roleServices,
      final GroupServices groupServices,
      final AuthorizationServices authorizationServices) {
    this.mappingServices = mappingServices;
    this.tenantServices = tenantServices;
    this.roleServices = roleServices;
    this.groupServices = groupServices;
    this.authorizationServices = authorizationServices;
  }

  @Override
  public OidcUser loadUser(final OidcUserRequest userRequest) throws OAuth2AuthenticationException {
    final OidcUser oidcUser = super.loadUser(userRequest);

    final Map<String, Object> claims = userRequest.getIdToken().getClaims();
    final List<MappingEntity> mappings = mappingServices.getMatchingMappings(claims);
    final Set<Long> mappingKeys =
        mappings.stream().map(MappingEntity::mappingKey).collect(Collectors.toSet());
    // TODO remove mapping when refactoring to IDs
    final Set<String> mappingKeysAsString =
        mappingKeys.stream().map(String::valueOf).collect(Collectors.toSet());
    final Set<String> mappingIds =
        mappings.stream().map(MappingEntity::id).collect(Collectors.toSet());
    if (mappingKeys.isEmpty()) {
      LOG.debug("No mappings found for these claims: {}", claims);
    }

    final var assignedRoles = roleServices.getRolesByMemberKeys(mappingKeys);

    return new CamundaOidcUser(
        oidcUser,
        mappingKeys,
        mappingIds,
        new AuthenticationContext(
            assignedRoles,
            authorizationServices.getAuthorizedApplications(
                Stream.concat(
                        assignedRoles.stream().map(r -> r.roleKey().toString()),
                        mappingKeys.stream()
                            .map(String::valueOf)) // TODO remove mapping when refactoring to IDs
                    .collect(Collectors.toSet())),
            tenantServices.getTenantsByMemberIds(mappingKeysAsString).stream()
                .map(TenantDTO::fromEntity)
                .toList(),
            groupServices.getGroupsByMemberKeys(mappingKeys).stream()
                .map(GroupEntity::name)
                .toList()));
  }
}
