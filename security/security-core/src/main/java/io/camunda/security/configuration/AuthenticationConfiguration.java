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
   * The groups claim to read the groups of a token of {@code issuer} from: the one of the first
   * provider with that {@code issuer-uri}, trailing slash ignored. With a single provider, that
   * provider verifies every token whatever its issuer, so its claim applies even without an {@code
   * issuer-uri}. A provider without its own {@code groups-claim}, and a token of no known issuer,
   * use the one of the {@code oidc} block.
   *
   * @param issuer the {@code iss} of a token, or {@code null}
   * @return the groups claim, or {@code null} when the groups of the token's user come from Camunda
   */
  public String getGroupsClaim(final String issuer) {
    final var providers = oidcProviders();
    if (providers.size() == 1) {
      return groupsClaimOf(providers.get(0));
    }
    return providers.stream()
        .filter(provider -> hasIssuer(provider, issuer))
        .findFirst()
        .map(this::groupsClaimOf)
        .orElseGet(() -> groupsClaimOf(oidcAuthenticationConfiguration));
  }

  private String groupsClaimOf(final OidcAuthenticationConfiguration provider) {
    if (provider.isGroupsClaimConfigured()) {
      return provider.getGroupsClaim();
    }
    return oidcAuthenticationConfiguration.isGroupsClaimConfigured()
        ? oidcAuthenticationConfiguration.getGroupsClaim()
        : null;
  }

  private static boolean hasIssuer(
      final OidcAuthenticationConfiguration configuration, final String issuer) {
    final var issuerUri = configuration.getIssuerUri();
    return issuer != null
        && issuerUri != null
        && withoutTrailingSlash(issuerUri).equals(withoutTrailingSlash(issuer));
  }

  private static String withoutTrailingSlash(final String uri) {
    return uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
  }

  /**
   * @return whether at least one OIDC provider gives the groups of its users in a groups claim.
   *     Such groups exist only in the identity provider, so Camunda can't require them to exist. A
   *     provider without its own groups claim uses the one of the default provider.
   */
  public boolean isExternalGroupsEnabled() {
    return method == AuthenticationMethod.OIDC
        && oidcProviders().stream().anyMatch(provider -> groupsClaimOf(provider) != null);
  }

  /**
   * @return whether Camunda manages the groups of the users of at least one OIDC provider, which is
   *     when that provider has no groups claim. Without OIDC, Camunda manages all groups.
   */
  public boolean isCamundaGroupsEnabled() {
    return method != AuthenticationMethod.OIDC
        || oidcProviders().stream().anyMatch(provider -> groupsClaimOf(provider) == null);
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
