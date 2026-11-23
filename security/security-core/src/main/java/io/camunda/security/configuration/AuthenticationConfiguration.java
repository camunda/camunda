/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

import io.camunda.security.entity.AuthenticationMethod;
import java.util.ArrayList;
import java.util.List;

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

  /**
   * @return whether at least one OIDC provider gives the groups of its users in a groups claim.
   *     Such groups exist only in the identity provider, so Camunda can't require them to exist.
   */
  public boolean isExternalGroupsEnabled() {
    return method == AuthenticationMethod.OIDC && isAnyProviderGroupsClaimConfigured();
  }

  /**
   * @return whether Camunda manages the groups of the users of at least one OIDC provider, which is
   *     when that provider has no groups claim. Without OIDC, Camunda manages all groups.
   */
  public boolean isCamundaGroupsEnabled() {
    return !(method == AuthenticationMethod.OIDC && isEveryProviderGroupsClaimConfigured());
  }

  /** Whether at least one OIDC provider has a groups claim, whatever the authentication method. */
  public boolean isAnyProviderGroupsClaimConfigured() {
    return oidcProviders().stream()
        .anyMatch(OidcAuthenticationConfiguration::isGroupsClaimConfigured);
  }

  /** Whether every OIDC provider has a groups claim, whatever the authentication method. */
  public boolean isEveryProviderGroupsClaimConfigured() {
    return oidcProviders().stream()
        .allMatch(OidcAuthenticationConfiguration::isGroupsClaimConfigured);
  }

  /**
   * The default provider counts when it is configured, or when it is the only one, which is the
   * case when no additional provider is.
   */
  private List<OidcAuthenticationConfiguration> oidcProviders() {
    final var providerList = new ArrayList<OidcAuthenticationConfiguration>();
    final var named =
        providers != null && providers.getOidc() != null
            ? providers.getOidc().values()
            : List.<OidcAuthenticationConfiguration>of();
    final var defaultClientId = oidcAuthenticationConfiguration.getClientId();
    if ((defaultClientId != null && !defaultClientId.isBlank()) || named.isEmpty()) {
      providerList.add(oidcAuthenticationConfiguration);
    }
    providerList.addAll(named);
    return providerList;
  }
}
