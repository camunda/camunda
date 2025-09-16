/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth;

import static io.camunda.security.entity.AuthenticationMethod.OIDC;
import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_ANONYMOUS_USER;
import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_CLIENT_ID;
import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_USERNAME;
import static io.camunda.zeebe.auth.Authorization.USER_GROUPS_CLAIMS;
import static io.camunda.zeebe.auth.Authorization.USER_TOKEN_CLAIMS;

import io.camunda.security.configuration.SecurityConfiguration;
import java.util.HashMap;
import java.util.Map;

public class BrokerRequestAuthorizationConverter {

  private final boolean isGroupsClaimConfigured;

  public BrokerRequestAuthorizationConverter(final SecurityConfiguration securityConfiguration) {
    isGroupsClaimConfigured = isGroupsClaimConfigured(securityConfiguration);
  }

  protected boolean isGroupsClaimConfigured(final SecurityConfiguration securityConfiguration) {
    final var authenticationConfiguration = securityConfiguration.getAuthentication();
    return authenticationConfiguration.getMethod() == OIDC
        && authenticationConfiguration.getOidc().isGroupsClaimConfigured();
  }

  public Map<String, Object> convert(final CamundaAuthentication authentication) {
    if (authentication.isAnonymous()) {
      return Map.of(AUTHORIZED_ANONYMOUS_USER, true);
    }

    final var authorization = new HashMap<String, Object>();
    final var username = authentication.authenticatedUsername();
    final var clientId = authentication.authenticatedClientId();
    final var groups = authentication.authenticatedGroupIds();
    final var claims = authentication.claims();

    if (username != null) {
      authorization.put(AUTHORIZED_USERNAME, username);
    }

    if (clientId != null) {
      authorization.put(AUTHORIZED_CLIENT_ID, clientId);
    }

    if (isGroupsClaimConfigured) {
      authorization.put(USER_GROUPS_CLAIMS, groups);
    }

    if (claims != null) {
      authorization.put(USER_TOKEN_CLAIMS, claims);
    }

    return authorization;
  }
}
