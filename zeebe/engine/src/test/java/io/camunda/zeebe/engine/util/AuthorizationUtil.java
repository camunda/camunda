/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util;

import io.camunda.zeebe.auth.api.JwtAuthorizationBuilder;
import io.camunda.zeebe.auth.impl.Authorization;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo.AuthDataFormat;
import java.util.List;

public class AuthorizationUtil {

  /**
   * Creates an {@link AuthInfo} instance that contains an encoded authorization string that can be
   * set on the record metadata.
   *
   * @param authorizedTenantIds the authorized tenant IDs
   * @return an encoded authorization string that can be set on the record metadata
   */
  public static AuthInfo getAuthInfo(final String... authorizedTenantIds) {
    final String authorizationToken =
        Authorization.jwtEncoder()
            .withIssuer(JwtAuthorizationBuilder.DEFAULT_ISSUER)
            .withAudience(JwtAuthorizationBuilder.DEFAULT_AUDIENCE)
            .withSubject(JwtAuthorizationBuilder.DEFAULT_SUBJECT)
            .withClaim(Authorization.AUTHORIZED_TENANTS, List.of(authorizedTenantIds))
            .encode();
    final var auth = new AuthInfo();
    auth.setFormatProp(AuthDataFormat.JWT).setAuthData(authorizationToken);
    return auth;
  }
}
