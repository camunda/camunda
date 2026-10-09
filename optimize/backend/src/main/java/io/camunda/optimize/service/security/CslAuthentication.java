/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Shared CSL request detection for the security package. {@link SessionService} and {@link
 * CCSMTokenService} both decide the same thing — is the current request CSL authenticated — and the
 * bug this guards against came from those two answers drifting apart, so they resolve it here.
 */
final class CslAuthentication {

  private CslAuthentication() {}

  /**
   * True when CSL authenticated the current request: an {@link OAuth2AuthenticationToken} from the
   * {@code oauth2Login} webapp chain, or a {@link JwtAuthenticationToken} from the bearer API
   * chain. CSL supplies a converter for both, so its authenticated username is authoritative for
   * either and the legacy cookie/sub branches are not consulted.
   */
  static boolean isCslAuthenticatedRequest() {
    final var authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication instanceof OAuth2AuthenticationToken
        || authentication instanceof JwtAuthenticationToken;
  }
}
