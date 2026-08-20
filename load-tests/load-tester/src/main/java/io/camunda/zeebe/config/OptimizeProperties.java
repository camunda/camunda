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

  private boolean reportEvaluationEnabled = true;
  private String baseUrl = "http://optimize:8090";
  private String keycloakUrl = "http://keycloak:18080";
  private String realm = "camunda-platform";
  private String clientId = "optimize";
  private String clientSecret = "";
  private String audience = "optimize-api";
  private String processDefinitionKey = "";
  private Duration evaluationInterval = Duration.ofSeconds(60);
  private Duration initialDelay = Duration.ofSeconds(10);
  private Duration requestTimeout = Duration.ofSeconds(30);

  public boolean isReportEvaluationEnabled() {
    return reportEvaluationEnabled;
  }

  public void setReportEvaluationEnabled(final boolean reportEvaluationEnabled) {
    this.reportEvaluationEnabled = reportEvaluationEnabled;
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

  public String getAudience() {
    return audience;
  }

  public void setAudience(final String audience) {
    this.audience = audience;
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

  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  public void setRequestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
  }
}
