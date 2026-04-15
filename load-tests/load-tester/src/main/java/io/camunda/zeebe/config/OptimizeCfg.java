/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.config;

public final class OptimizeCfg {
  private String baseUrl;
  private String keycloakUrl;
  private String realm;
  private String clientId;
  private String clientSecret;
  private String username;
  private String password;
  private String processDefinitionKey;
  private int evaluationIntervalSeconds = 60; // Default: 1 minute
  private int authRetryMaxAttempts = 30;
  private int authRetryDelaySeconds = 10;
  private boolean jwtAuthEnabled;

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

  public int getEvaluationIntervalSeconds() {
    return evaluationIntervalSeconds;
  }

  public void setEvaluationIntervalSeconds(final int evaluationIntervalSeconds) {
    this.evaluationIntervalSeconds = evaluationIntervalSeconds;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public int getAuthRetryMaxAttempts() {
    return authRetryMaxAttempts;
  }

  public void setAuthRetryMaxAttempts(final int authRetryMaxAttempts) {
    this.authRetryMaxAttempts = authRetryMaxAttempts;
  }

  public int getAuthRetryDelaySeconds() {
    return authRetryDelaySeconds;
  }

  public void setAuthRetryDelaySeconds(final int authRetryDelaySeconds) {
    this.authRetryDelaySeconds = authRetryDelaySeconds;
  }

  public boolean isJwtAuthEnabled() {
    return jwtAuthEnabled;
  }

  public void setJwtAuthEnabled(final boolean jwtAuthEnabled) {
    this.jwtAuthEnabled = jwtAuthEnabled;
  }
}
