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
import org.springframework.security.core.Authentication;

public class DefaultCamundaAuthenticationConverter
    implements CamundaAuthenticationConverter<Authentication> {

  @Override
  public CamundaAuthentication convert(final Authentication springBasedAuthentication) {
    final Map<String, Object> claims = new HashMap<>();
    final var authenticationBuilder = new Builder();

    if (springBasedAuthentication != null) {
      if (springBasedAuthentication.getPrincipal()
          instanceof final CamundaPrincipal authenticatedPrincipal) {
        final var authenticationContext = authenticatedPrincipal.getAuthenticationContext();

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

        if (authenticatedPrincipal instanceof final CamundaOAuthPrincipal principal) {
          claims.put(Authorization.USER_TOKEN_CLAIMS, principal.getClaims());
          authenticationBuilder.mapping(principal.getOAuthContext().mappingIds().stream().toList());
        }

        authenticationBuilder.claims(claims);
      }
    }

    return authenticationBuilder.build();
  }
}
