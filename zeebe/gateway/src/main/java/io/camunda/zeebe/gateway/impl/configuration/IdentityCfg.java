/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.configuration;

import java.util.Objects;

public final class IdentityCfg {
  private String issuerBackendUrl;
  private String audience = "zeebe-api";
  private OAuthType type = OAuthType.KEYCLOAK;
  private String baseUrl;

  public String getIssuerBackendUrl() {
    return issuerBackendUrl;
  }

  public void setIssuerBackendUrl(final String issuerBackendUrl) {
    this.issuerBackendUrl = issuerBackendUrl;
  }

  public String getAudience() {
    return audience;
  }

  public void setAudience(final String audience) {
    this.audience = audience;
  }

  public OAuthType getType() {
    return type;
  }

  public void setType(final OAuthType type) {
    this.type = type;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(final String baseUrl) {
    this.baseUrl = baseUrl;
  }

  @Override
  public int hashCode() {
    return Objects.hash(issuerBackendUrl, audience, type, baseUrl);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final IdentityCfg that = (IdentityCfg) o;
    return Objects.equals(issuerBackendUrl, that.issuerBackendUrl)
        && Objects.equals(audience, that.audience)
        && type == that.type
        && Objects.equals(baseUrl, that.baseUrl);
  }

  @Override
  public String toString() {
    return "IdentityCfg{"
        + "issuerBackendUrl='"
        + issuerBackendUrl
        + '\''
        + ", audience='"
        + audience
        + '\''
        + ", type="
        + type
        + ", baseUrl='"
        + baseUrl
        + '\''
        + '}';
  }

  public enum OAuthType {
    KEYCLOAK,
    AUTH0
  }
}
