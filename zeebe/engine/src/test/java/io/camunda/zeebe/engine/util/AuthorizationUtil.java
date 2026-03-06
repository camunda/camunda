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
   * Creates an {@link AuthInfo} instance that contains an encoded authorization string that can be
   * set on the record metadata.
   *
   * @param authorizedTenantIds the authorized tenant IDs
   * @return an encoded authorization string that can be set on the record metadata
   */
  public static AuthInfo getAuthInfo(final String... authorizedTenantIds) {
    return AuthInfo.withClaims(
        Map.of(Authorization.AUTHORIZED_TENANTS, List.of(authorizedTenantIds)));
  }

  public static AuthInfo getUsernameAuthInfo(
      final String username, final String... authorizedTenantIds) {
    return AuthInfo.withClaims(
        Map.of(
            Authorization.AUTHORIZED_USERNAME,
            username,
            Authorization.AUTHORIZED_TENANTS,
            List.of(authorizedTenantIds)));
  }

  public static AuthInfo getAuthInfo(final String username) {
    return AuthInfo.withClaims(Map.of(Authorization.AUTHORIZED_USERNAME, username));
  }

  public static AuthInfo getAuthInfoWithClaim(final String claim, final Object claimValue) {
    return AuthInfo.withClaims(Map.of(claim, claimValue));
  }
}
