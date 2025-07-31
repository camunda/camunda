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
import io.camunda.authentication.exception.CamundaAuthenticationException;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthentication.Builder;
import io.camunda.security.auth.CamundaAuthenticationConverter;
import io.camunda.zeebe.auth.Authorization;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;

public class DefaultCamundaAuthenticationConverter
    implements CamundaAuthenticationConverter<Authentication> {

  private static final Logger LOG =
      LoggerFactory.getLogger(DefaultCamundaAuthenticationConverter.class);

  @Override
  public boolean supports(final Authentication authentication) {
    return Optional.ofNullable(authentication)
        .map(Authentication::getPrincipal)
        .filter(CamundaPrincipal.class::isInstance)
        .isPresent();
  }

  @Override
  public CamundaAuthentication convert(final Authentication authentication) {
    return Optional.of(authentication)
        .map(Authentication::getPrincipal)
        .map(CamundaPrincipal.class::cast)
        .map(this::convertCamundaPrincipal)
        .orElseThrow(
            () -> {
              final var message =
                  "Failed to convert Spring Authentication '%s' authentication to a Camunda Authentication"
                      .formatted(authentication.getClass().getSimpleName());
              LOG.error(message);
              return new CamundaAuthenticationException(message);
            });
  }

  private CamundaAuthentication convertCamundaPrincipal(final CamundaPrincipal camundaPrincipal) {
    final Map<String, Object> claims = new HashMap<>();
    final var authenticationBuilder = new Builder();
    final var authenticationContext = camundaPrincipal.getAuthenticationContext();

    authenticationBuilder.roleIds(authenticationContext.roles());
    authenticationBuilder.tenants(authenticationContext.tenants());
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
      authenticationBuilder.mappingRule(
          principal.getOAuthContext().mappingRuleIds().stream().toList());
    }

    return authenticationBuilder.claims(claims).build();
  }
}
