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

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.configuration.SecurityConfiguration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts authentication information into the claims map embedded in broker requests.
 *
 * <p>Identity claims (username, clientId) are always included because the exporter reads them from
 * the record to populate the audit log actor (see {@code AuditLogActor.of}). Skipping these would
 * cause audit log entries to have no actor information.
 *
 * <p>Authorization claims (JWT token claims, group memberships) are only needed for authorization
 * and multi-tenancy checks in the engine, and are skipped when both are disabled. Callers can check
 * {@link #shouldIncludeAuthorizationClaims()} to avoid computing these values when not needed.
 */
public class BrokerRequestAuthorizationConverter {

  private final boolean camundaGroupsEnabled;
  private final boolean shouldIncludeAuthorizationClaims;

  public BrokerRequestAuthorizationConverter(final SecurityConfiguration securityConfiguration) {
    camundaGroupsEnabled = securityConfiguration.getAuthentication().isCamundaGroupsEnabled();
    shouldIncludeAuthorizationClaims =
        securityConfiguration.getAuthorizations().isEnabled()
            || securityConfiguration.getMultiTenancy().isChecksEnabled();
  }

  /** Returns whether authorization claims (token claims, groups) are needed in broker requests. */
  public boolean shouldIncludeAuthorizationClaims() {
    return shouldIncludeAuthorizationClaims;
  }

  public Map<String, Object> convert(final CamundaAuthentication authentication) {
    return convert(
        authentication.isAnonymous(),
        authentication.authenticatedUsername(),
        authentication.authenticatedClientId(),
        authentication.authenticatedGroupIds(),
        authentication.claims());
  }

  public Map<String, Object> convert(
      final boolean isAnonymous,
      final String username,
      final String clientId,
      final List<String> groups,
      final Map<String, Object> tokenClaims) {

    if (isAnonymous) {
      return Map.of(AUTHORIZED_ANONYMOUS_USER, true);
    }

    final var claims = new HashMap<String, Object>();

    if (username != null) {
      claims.put(AUTHORIZED_USERNAME, username);
    }

    if (clientId != null) {
      claims.put(AUTHORIZED_CLIENT_ID, clientId);
    }

    if (shouldIncludeAuthorizationClaims) {
      if (!camundaGroupsEnabled && groups != null) {
        claims.put(USER_GROUPS_CLAIMS, groups);
      }

      if (tokenClaims != null) {
        claims.put(USER_TOKEN_CLAIMS, tokenClaims);
      }
    }

    return claims;
  }
}
