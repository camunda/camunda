/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.config;

import jakarta.validation.constraints.NotBlank;

public class ManagementIdentityProperties {

  @NotBlank(
      message =
          "Base URL must be provided (camunda.migration.identity.managementIdentity.base-url)")
  private String baseUrl;

  @NotBlank(
      message =
          "Issuer Backend URL must be provided (camunda.migration.identity.managementIdentity.issuer-backend-url)")
  private String issuerBackendUrl;

  @NotBlank(
      message =
          "Client ID must be provided (camunda.migration.identity.managementIdentity.client-id)")
  private String clientId;

  @NotBlank(
      message =
          "Client Secret must be provided (camunda.migration.identity.managementIdentity.client-secret)")
  private String clientSecret;

  @NotBlank(
      message =
          "Audience must be provided (camunda.migration.identity.managementIdentity.audience)")
  private String audience;

  private String issuerType;

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(final String baseUrl) {
    this.baseUrl = baseUrl;
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

  public String getIssuerBackendUrl() {
    return issuerBackendUrl;
  }

  public void setIssuerBackendUrl(final String issuerBackendUrl) {
    this.issuerBackendUrl = issuerBackendUrl;
  }

  public String getIssuerType() {
    return issuerType;
  }

  public void setIssuerType(final String issuerType) {
    this.issuerType = issuerType;
  }
}
