/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public class OptimizeApiConfiguration {

  @JsonProperty("accessToken")
  private String accessToken;

  @JsonProperty("jwtSetUri")
  private String jwtSetUri;

  @JsonProperty("audience")
  private String audience;

  /**
   * When {@code true}, non-public {@code /api/**} endpoints also accept a standard {@code
   * Authorization: Bearer <jwt>} header in addition to the existing Identity session cookie. Only
   * meaningful in self-managed (CCSM) mode. Defaults to {@code false}.
   */
  @JsonProperty("jwtAuthForApiEnabled")
  private boolean jwtAuthForApiEnabled = false;

  public OptimizeApiConfiguration() {}

  public String getAccessToken() {
    return accessToken;
  }

  @JsonProperty("accessToken")
  public void setAccessToken(final String accessToken) {
    this.accessToken = accessToken;
  }

  public String getJwtSetUri() {
    return jwtSetUri;
  }

  @JsonProperty("jwtSetUri")
  public void setJwtSetUri(final String jwtSetUri) {
    this.jwtSetUri = jwtSetUri;
  }

  public String getAudience() {
    return audience;
  }

  @JsonProperty("audience")
  public void setAudience(final String audience) {
    this.audience = audience;
  }

  public boolean isJwtAuthForApiEnabled() {
    return jwtAuthForApiEnabled;
  }

  @JsonProperty("jwtAuthForApiEnabled")
  public void setJwtAuthForApiEnabled(final boolean jwtAuthForApiEnabled) {
    this.jwtAuthForApiEnabled = jwtAuthForApiEnabled;
  }

  @Override
  public int hashCode() {
    return Objects.hash(accessToken, jwtSetUri, audience, jwtAuthForApiEnabled);
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final OptimizeApiConfiguration that = (OptimizeApiConfiguration) o;
    return jwtAuthForApiEnabled == that.jwtAuthForApiEnabled
        && Objects.equals(accessToken, that.accessToken)
        && Objects.equals(jwtSetUri, that.jwtSetUri)
        && Objects.equals(audience, that.audience);
  }

  @Override
  public String toString() {
    return "OptimizeApiConfiguration(accessToken="
        + getAccessToken()
        + ", jwtSetUri="
        + getJwtSetUri()
        + ", audience="
        + getAudience()
        + ", jwtAuthForApiEnabled="
        + isJwtAuthForApiEnabled()
        + ")";
  }
}
