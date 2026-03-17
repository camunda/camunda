/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth;

import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_ANONYMOUS_USER;
import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_CLIENT_ID;
import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_USERNAME;
import static io.camunda.zeebe.auth.Authorization.USER_GROUPS_CLAIMS;
import static io.camunda.zeebe.auth.Authorization.USER_TOKEN_CLAIMS;

import io.camunda.security.configuration.SecurityConfiguration;
import java.util.HashMap;
import java.util.Map;

public class BrokerRequestAuthorizationConverter {

  private final boolean camundaGroupsEnabled;
  private final boolean authorizationClaimsEnabled;

  public BrokerRequestAuthorizationConverter(final SecurityConfiguration securityConfiguration) {
    camundaGroupsEnabled = securityConfiguration.getAuthentication().isCamundaGroupsEnabled();
    authorizationClaimsEnabled =
        securityConfiguration.getAuthorizations().isEnabled()
            || securityConfiguration.getMultiTenancy().isChecksEnabled();
  }

  public Map<String, Object> convert(final CamundaAuthentication authentication) {
    final var authorization = new HashMap<String, Object>();

    if (authentication.isAnonymous()) {
      authorization.put(AUTHORIZED_ANONYMOUS_USER, true);
      return authorization;
    }

    // Identity claims are always sent because the exporter reads them from the record to populate
    // the audit log actor (see AuditLogActor.of). Skipping these would cause audit log entries to
    // have no actor information.
    final var username = authentication.authenticatedUsername();
    final var clientId = authentication.authenticatedClientId();

    if (username != null) {
      authorization.put(AUTHORIZED_USERNAME, username);
    }

    if (clientId != null) {
      authorization.put(AUTHORIZED_CLIENT_ID, clientId);
    }

    // Authorization claims (JWT token claims, group memberships) are only needed for authorization
    // and multi-tenancy checks in the engine. Skip them when both are disabled.
    if (authorizationClaimsEnabled) {
      if (!camundaGroupsEnabled) {
        authorization.put(USER_GROUPS_CLAIMS, authentication.authenticatedGroupIds());
      }

      final var claims = authentication.claims();
      if (claims != null) {
        authorization.put(USER_TOKEN_CLAIMS, claims);
      }
    }

    return authorization;
  }
}
