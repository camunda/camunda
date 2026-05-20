/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.security.configuration.SecurityConfiguration;

public final class AuthenticationProperties {
  public static final String METHOD = "camunda.security.authentication.method";
  public static final String API_UNPROTECTED = "camunda.security.authentication.unprotected-api";
  public static final String OIDC_CLIENT_ID = "camunda.security.authentication.oidc.client-id";
  public static final String OIDC_CLIENT_SECRET =
      "camunda.security.authentication.oidc.client-secret";
  public static final String OIDC_ISSUER_URI = "camunda.security.authentication.oidc.issuer-uri";
  public static final String OIDC_REDIRECT_URI =
      "camunda.security.authentication.oidc.redirect-uri";
  public static final String OIDC_AUTHORIZATION_URI =
      "camunda.security.authentication.oidc.authorization-uri";
  public static final String OIDC_TOKEN_URI = "camunda.security.authentication.oidc.token-uri";
  public static final String OIDC_JWK_SET_URI = "camunda.security.authentication.oidc.jwk-set-uri";

  private AuthenticationProperties() {}

  public static String getUnprotectedApiEnvVar() {
    return API_UNPROTECTED.replace(".", "_").replace("-", "").toUpperCase();
  }

  /**
   * Mirrors a {@code camunda.security.*} property into the host-registered {@link
   * SecurityConfiguration} bean. The test framework pre-registers {@code SecurityConfiguration} as
   * a Spring singleton (see {@code TestStandaloneBroker}), which bypasses Spring's
   * {@code @ConfigurationProperties} binding for that instance; {@code withProperty(...)} updates
   * Spring's property sources, so a separate manual mirror is needed to keep the typed bean in
   * sync.
   *
   * <p>Adding a new key here is the right move when an integration test sets it via {@code
   * withProperty(...)} and the host-side OIDC code path reads from {@link SecurityConfiguration}.
   * CSL beans bind directly from the property sources and do not need mirroring.
   */
  public static void applyToSecurityConfig(
      final SecurityConfiguration securityConfig, final String key, final Object value) {
    if (securityConfig == null) {
      // TestStandaloneBroker's super-class construction triggers withProperty(...) before the
      // securityConfig field is initialised; tolerate that early window — the constructor will
      // populate the typed bean before any meaningful test-time mutation runs.
      return;
    }
    final var oidc = securityConfig.getAuthentication().getOidc();
    switch (key) {
      case METHOD ->
          securityConfig
              .getAuthentication()
              .setMethod(AuthenticationMethod.parse(String.valueOf(value)));
      case API_UNPROTECTED ->
          securityConfig
              .getAuthentication()
              .setUnprotectedApi(Boolean.parseBoolean(String.valueOf(value)));
      case OIDC_CLIENT_ID -> oidc.setClientId(String.valueOf(value));
      case OIDC_CLIENT_SECRET -> oidc.setClientSecret(String.valueOf(value));
      case OIDC_ISSUER_URI -> oidc.setIssuerUri(String.valueOf(value));
      case OIDC_REDIRECT_URI -> oidc.setRedirectUri(String.valueOf(value));
      case OIDC_AUTHORIZATION_URI -> oidc.setAuthorizationUri(String.valueOf(value));
      case OIDC_TOKEN_URI -> oidc.setTokenUri(String.valueOf(value));
      case OIDC_JWK_SET_URI -> oidc.setJwkSetUri(String.valueOf(value));
      default -> {}
    }
  }
}
