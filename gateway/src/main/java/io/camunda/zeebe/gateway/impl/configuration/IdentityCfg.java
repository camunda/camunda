/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.configuration;

import java.util.Objects;

public final class IdentityCfg {
  private String issuerBackendUrl;
  private String audience = "zeebe-api";
  private OAuthType type = OAuthType.KEYCLOAK;

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

  @Override
  public int hashCode() {
    return Objects.hash(issuerBackendUrl, audience, type);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final IdentityCfg identityCfg = (IdentityCfg) o;
    return Objects.equals(issuerBackendUrl, identityCfg.issuerBackendUrl)
        && Objects.equals(audience, identityCfg.audience)
        && type == identityCfg.type;
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
        + '}';
  }

  public enum OAuthType {
    KEYCLOAK,
    AUTH0
  }
}
