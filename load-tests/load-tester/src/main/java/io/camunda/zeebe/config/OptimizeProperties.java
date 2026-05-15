/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.config;

import java.time.Duration;

public class OptimizeProperties {

  private boolean enabled = true;
  private String baseUrl = "http://optimize:8090";
  private String keycloakUrl = "http://keycloak:18080";
  private String realm = "camunda-platform";
  private String clientId = "optimize";
  private String clientSecret = "";
  private String processDefinitionKey = "";
  private Duration evaluationInterval = Duration.ofSeconds(60);
  private Duration initialDelay = Duration.ofSeconds(10);
  private int authRetryMaxAttempts = 30;
  private Duration authRetryDelay = Duration.ofSeconds(10);
  private Duration tokenRefreshSkew = Duration.ofSeconds(30);
  private Duration requestTimeout = Duration.ofSeconds(30);

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
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

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public Duration getEvaluationInterval() {
    return evaluationInterval;
  }

  public void setEvaluationInterval(final Duration evaluationInterval) {
    this.evaluationInterval = evaluationInterval;
  }

  public Duration getInitialDelay() {
    return initialDelay;
  }

  public void setInitialDelay(final Duration initialDelay) {
    this.initialDelay = initialDelay;
  }

  public int getAuthRetryMaxAttempts() {
    return authRetryMaxAttempts;
  }

  public void setAuthRetryMaxAttempts(final int authRetryMaxAttempts) {
    this.authRetryMaxAttempts = authRetryMaxAttempts;
  }

  public Duration getAuthRetryDelay() {
    return authRetryDelay;
  }

  public void setAuthRetryDelay(final Duration authRetryDelay) {
    this.authRetryDelay = authRetryDelay;
  }

  public Duration getTokenRefreshSkew() {
    return tokenRefreshSkew;
  }

  public void setTokenRefreshSkew(final Duration tokenRefreshSkew) {
    this.tokenRefreshSkew = tokenRefreshSkew;
  }

  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  public void setRequestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
  }
}
