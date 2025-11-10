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
import static io.camunda.zeebe.auth.Authorization.IS_CAMUNDA_GROUPS_ENABLED;
import static io.camunda.zeebe.auth.Authorization.IS_CAMUNDA_USERS_ENABLED;
import static io.camunda.zeebe.auth.Authorization.USER_GROUPS_CLAIMS;
import static io.camunda.zeebe.auth.Authorization.USER_TOKEN_CLAIMS;

import io.camunda.security.configuration.SecurityConfiguration;
import java.util.HashMap;
import java.util.Map;

public class BrokerRequestAuthorizationConverter {

  private final boolean camundaGroupsEnabled;
  private final boolean camundaUsersEnabled;

  public BrokerRequestAuthorizationConverter(final SecurityConfiguration securityConfiguration) {
    camundaGroupsEnabled = isCamundaGroupsEnabled(securityConfiguration);
    camundaUsersEnabled = isCamundaUsersEnabled(securityConfiguration);
  }

  protected boolean isCamundaGroupsEnabled(final SecurityConfiguration securityConfiguration) {
    final var authenticationConfiguration = securityConfiguration.getAuthentication();
    return !(authenticationConfiguration.getMethod() == OIDC
        && authenticationConfiguration.getOidc().isGroupsClaimConfigured());
  }

  protected boolean isCamundaUsersEnabled(final SecurityConfiguration securityConfiguration) {
    final var authenticationConfiguration = securityConfiguration.getAuthentication();
    return authenticationConfiguration.getMethod() != OIDC;
  }

  public Map<String, Object> convert(final CamundaAuthentication authentication) {

    final var authorization = new HashMap<String, Object>();
    authorization.put(IS_CAMUNDA_GROUPS_ENABLED, camundaGroupsEnabled);
    authorization.put(IS_CAMUNDA_USERS_ENABLED, camundaUsersEnabled);
    if (authentication.isAnonymous()) {
      authorization.put(AUTHORIZED_ANONYMOUS_USER, true);
      return authorization;
    }

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

    if (!camundaGroupsEnabled) {
      authorization.put(USER_GROUPS_CLAIMS, groups);
    }

    if (claims != null) {
      authorization.put(USER_TOKEN_CLAIMS, claims);
    }

    return authorization;
  }
}
