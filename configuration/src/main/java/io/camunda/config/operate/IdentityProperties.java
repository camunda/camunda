/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.config.operate;

public class IdentityProperties {

  private static final int DEFAULT_RESOURCE_PERMISSIONS_UPDATE_PERIOD = 30; // seconds

  private String baseUrl;
  private String issuerUrl;
  private String issuerBackendUrl;
  private String redirectRootUrl;
  private String clientId;
  private String clientSecret;
  private String audience;

  private long resourcePermissionsUpdatePeriod = DEFAULT_RESOURCE_PERMISSIONS_UPDATE_PERIOD;

  public String getBaseUrl() {
    return baseUrl;
  }

  public IdentityProperties setBaseUrl(final String baseUrl) {
    this.baseUrl = baseUrl;
    return this;
  }

  public String getIssuerUrl() {
    return issuerUrl;
  }

  public void setIssuerUrl(final String issuerUrl) {
    this.issuerUrl = issuerUrl;
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

  public String getIssuerBackendUrl() {
    return issuerBackendUrl;
  }

  public void setIssuerBackendUrl(final String issuerBackendUrl) {
    this.issuerBackendUrl = issuerBackendUrl;
  }

  public String getRedirectRootUrl() {
    return redirectRootUrl;
  }

  public void setRedirectRootUrl(final String redirectRootUrl) {
    this.redirectRootUrl = redirectRootUrl;
  }

  public String getAudience() {
    return audience;
  }

  public void setAudience(final String audience) {
    this.audience = audience;
  }

  public long getResourcePermissionsUpdatePeriod() {
    return resourcePermissionsUpdatePeriod;
  }

  public IdentityProperties setResourcePermissionsUpdatePeriod(
      final long resourcePermissionsUpdatePeriod) {
    this.resourcePermissionsUpdatePeriod = resourcePermissionsUpdatePeriod;
    return this;
  }
}
