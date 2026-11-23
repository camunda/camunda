/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

import io.camunda.security.entity.AuthenticationMethod;

public class AuthenticationConfiguration {
  public static final AuthenticationMethod DEFAULT_METHOD = AuthenticationMethod.BASIC;
  public static final boolean DEFAULT_UNPROTECTED_API = false;

  private AuthenticationMethod method = DEFAULT_METHOD;
  private String authenticationRefreshInterval = "PT30S";
  private OidcAuthenticationConfiguration oidcAuthenticationConfiguration =
      new OidcAuthenticationConfiguration();
  private boolean unprotectedApi = DEFAULT_UNPROTECTED_API;

  private ProvidersConfiguration providers;

  public boolean getUnprotectedApi() {
    return unprotectedApi;
  }

  public void setUnprotectedApi(final boolean value) {
    unprotectedApi = value;
  }

  public AuthenticationMethod getMethod() {
    return method;
  }

  public void setMethod(final AuthenticationMethod method) {
    this.method = method;
  }

  public String getAuthenticationRefreshInterval() {
    return authenticationRefreshInterval;
  }

  public void setAuthenticationRefreshInterval(final String authenticationRefreshInterval) {
    this.authenticationRefreshInterval = authenticationRefreshInterval;
  }

  public OidcAuthenticationConfiguration getOidc() {
    return oidcAuthenticationConfiguration;
  }

  public void setOidc(final OidcAuthenticationConfiguration configuration) {
    oidcAuthenticationConfiguration = configuration;
  }

  public ProvidersConfiguration getProviders() {
    return providers;
  }

  public void setProviders(final ProvidersConfiguration providers) {
    this.providers = providers;
  }

  /**
   * @param issuer the {@code iss} of a token, or {@code null}
   * @return the configuration of the OIDC provider that issued the token, or the default one when
   *     no provider has this issuer. Issuers that differ only by a trailing slash are the same.
   */
  public OidcAuthenticationConfiguration oidcForIssuer(final String issuer) {
    if (issuer == null) {
      return oidcAuthenticationConfiguration;
    }
    if (hasIssuer(oidcAuthenticationConfiguration, issuer)) {
      return oidcAuthenticationConfiguration;
    }
    if (providers != null && providers.getOidc() != null) {
      for (final var provider : providers.getOidc().values()) {
        if (hasIssuer(provider, issuer)) {
          return provider;
        }
      }
    }
    return oidcAuthenticationConfiguration;
  }

  private static boolean hasIssuer(
      final OidcAuthenticationConfiguration configuration, final String issuer) {
    final var issuerUri = configuration.getIssuerUri();
    return issuerUri != null
        && withoutTrailingSlash(issuerUri).equals(withoutTrailingSlash(issuer));
  }

  private static String withoutTrailingSlash(final String uri) {
    return uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
  }
}
