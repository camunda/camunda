/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util;

import io.camunda.zeebe.auth.impl.Authorization;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import java.util.List;
import java.util.Map;

public class AuthorizationUtil {

  /**
   * Creates an {@link AuthInfo} instance that contains the authorizations claims.
   *
   * @param authorizedTenantIds the authorized tenant IDs
   * @return an encoded authorization record that contains the authorizations claims
   */
  public static AuthInfo getAuthInfo(final String... authorizedTenantIds) {
    final var auth = new AuthInfo();
    auth.setAuthInfo(Map.of(Authorization.AUTHORIZED_TENANTS, List.of(authorizedTenantIds)));
    return auth;
  }
}
