/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util;

import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import java.util.List;
import java.util.Map;

public class AuthorizationUtil {

  /**
   * Creates an {@link AuthInfo} that can be set on the record metadata.
   *
   * @param authorizedTenantIds the authorized tenant IDs
   * @return the auth info authorizing the given tenants
   */
  public static AuthInfo getAuthInfo(final String... authorizedTenantIds) {
    return AuthInfo.ofClaims(
        Map.of(Authorization.AUTHORIZED_TENANTS, List.of(authorizedTenantIds)));
  }

  public static AuthInfo getUsernameAuthInfo(
      final String username, final String... authorizedTenantIds) {
    return AuthInfo.ofClaims(
        Map.of(
            Authorization.AUTHORIZED_USERNAME,
            username,
            Authorization.AUTHORIZED_TENANTS,
            List.of(authorizedTenantIds)));
  }

  public static AuthInfo getAuthInfo(final String username) {
    return AuthInfo.ofClaims(Map.of(Authorization.AUTHORIZED_USERNAME, username));
  }

  public static AuthInfo getAuthInfoWithClaim(final String claim, final Object claimValue) {
    return AuthInfo.ofClaims(Map.of(claim, claimValue));
  }

  public static AuthInfo getClientIdAuthInfo(
      final String clientId, final String... authorizedTenantIds) {
    return AuthInfo.ofClaims(
        Map.of(
            Authorization.AUTHORIZED_CLIENT_ID,
            clientId,
            Authorization.AUTHORIZED_TENANTS,
            List.of(authorizedTenantIds)));
  }

  public static AuthInfo getClaimsFreeAuthInfo() {
    return AuthInfo.ofClaims(Map.of());
  }
}
