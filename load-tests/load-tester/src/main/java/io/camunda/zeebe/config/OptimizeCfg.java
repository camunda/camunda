/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.config;

public final class OptimizeCfg {
  private String baseUrl = getEnvOrDefault("OPTIMIZE_BASE_URL", "http://localhost:8083");
  private String keycloakUrl = getEnvOrDefault("OPTIMIZE_KEYCLOAK_URL", "http://localhost:18080");
  private String realm = getEnvOrDefault("OPTIMIZE_REALM", "camunda-platform");
  private String clientId = getEnvOrDefault("OPTIMIZE_CLIENT_ID", "optimize");
  private String clientSecret = getEnvOrDefault("OPTIMIZE_CLIENT_SECRET", "demo-optimize-secret");
  private String username = getEnvOrDefault("OPTIMIZE_USERNAME", "demo");
  private String password = getEnvOrDefault("OPTIMIZE_PASSWORD", "demo");
  private String reportId;

  private static String getEnvOrDefault(final String envVar, final String defaultValue) {
    final String value = System.getenv(envVar);
    return value != null && !value.isEmpty() ? value : defaultValue;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(final String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getKeycloakUrl() {
    return keycloakUrl;
  }

  public void setKeycloakUrl(final String keycloakUrl) {
    this.keycloakUrl = keycloakUrl;
  }

  public String getRealm() {
    return realm;
  }

  public void setRealm(final String realm) {
    this.realm = realm;
  }

  public String getClientId() {
    return clientId;
  }

  public void setClientId(final String clientId) {
    this.clientId = clientId;
  }

  public String getClientSecret() {
    return clientSecret;
  }

  public void setClientSecret(final String clientSecret) {
    this.clientSecret = clientSecret;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(final String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(final String password) {
    this.password = password;
  }

  public String getReportId() {
    return reportId;
  }

  public void setReportId(final String reportId) {
    this.reportId = reportId;
  }
}
