/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import io.camunda.authentication.entity.CamundaOAuthPrincipal;
import io.camunda.authentication.entity.CamundaPrincipal;
import io.camunda.search.entities.RoleEntity;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthentication.Builder;
import io.camunda.security.auth.CamundaAuthenticationConverter;
import io.camunda.service.TenantServices.TenantDTO;
import io.camunda.zeebe.auth.Authorization;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.core.Authentication;

public class DefaultCamundaAuthenticationConverter
    implements CamundaAuthenticationConverter<Authentication> {

  @Override
  public CamundaAuthentication convert(final Authentication springBasedAuthentication) {
    return Optional.ofNullable(springBasedAuthentication)
        .map(Authentication::getPrincipal)
        .filter(CamundaPrincipal.class::isInstance)
        .map(CamundaPrincipal.class::cast)
        .map(this::convertCamundaPrincipal)
        .orElseGet(CamundaAuthentication::none);
  }

  private CamundaAuthentication convertCamundaPrincipal(final CamundaPrincipal camundaPrincipal) {
    final Map<String, Object> claims = new HashMap<>();
    final var authenticationBuilder = new Builder();
    final var authenticationContext = camundaPrincipal.getAuthenticationContext();

    authenticationBuilder.roleIds(
        authenticationContext.roles().stream().map(RoleEntity::roleId).toList());

    authenticationBuilder.tenants(
        authenticationContext.tenants().stream().map(TenantDTO::tenantId).toList());

    authenticationBuilder.groupIds(authenticationContext.groups());

    if (authenticationContext.groupsClaimEnabled()) {
      claims.put(Authorization.USER_GROUPS_CLAIMS, authenticationContext.groups());
    }

    if (authenticationContext.username() != null) {
      final var authenticatedUsername = authenticationContext.username();
      claims.put(Authorization.AUTHORIZED_USERNAME, authenticatedUsername);
      authenticationBuilder.user(authenticatedUsername);
    } else {
      final var authenticatedClientId = authenticationContext.clientId();
      claims.put(Authorization.AUTHORIZED_CLIENT_ID, authenticatedClientId);
      authenticationBuilder.clientId(authenticatedClientId);
    }

    if (camundaPrincipal instanceof final CamundaOAuthPrincipal principal) {
      claims.put(Authorization.USER_TOKEN_CLAIMS, principal.getClaims());
      authenticationBuilder.mapping(principal.getOAuthContext().mappingIds().stream().toList());
    }

    return authenticationBuilder.claims(claims).build();
  }
}
