/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.appint.config;

public class ConfigValidator {

  public static void validate(final Config config) {
    if (config == null) {
      throw new IllegalArgumentException("Configuration cannot be null.");
    }
    if (config.getUrl() == null || config.getUrl().isBlank()) {
      throw new IllegalArgumentException("Url cannot be null or blank.");
    }
    validateAuthentication(config);
  }

  private static void validateAuthentication(final Config config) {
    final OAuthConfig oauth = config.getOauth();
    if (oauth == null) {
      return;
    }
    if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
      throw new IllegalArgumentException(
          "Both apiKey and oauth authentication are configured; only one may be set.");
    }
    if (oauth.getClientId() == null || oauth.getClientId().isBlank()) {
      throw new IllegalArgumentException("OAuth clientId cannot be null or blank.");
    }
    if (oauth.getClientSecret() == null || oauth.getClientSecret().isBlank()) {
      throw new IllegalArgumentException("OAuth clientSecret cannot be null or blank.");
    }
    if (oauth.getAuthorizationServerUrl() == null || oauth.getAuthorizationServerUrl().isBlank()) {
      throw new IllegalArgumentException("OAuth authorizationServerUrl cannot be null or blank.");
    }
  }
}
