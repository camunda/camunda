/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import io.camunda.security.spring.spi.WebAppProviderPort;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

/**
 * Claims every request of a login session for the {@code optimize} component, and no other request.
 * Optimize serves one component, so the request path does not select it, the authentication does:
 * only a session that came out of the OIDC login is subject to the component check.
 *
 * <p>Bearer requests resolve to empty, which passes them through. Their authorization is the
 * audience check of the API chain, exactly as it is without CSL.
 */
public final class OptimizeWebAppProviderAdapter implements WebAppProviderPort {

  static final String OPTIMIZE_WEB_APP = "optimize";

  @Override
  public Optional<String> webAppFor(final HttpServletRequest request) {
    if (SecurityContextHolder.getContext().getAuthentication()
        instanceof OAuth2AuthenticationToken) {
      return Optional.of(OPTIMIZE_WEB_APP);
    }
    return Optional.empty();
  }
}
