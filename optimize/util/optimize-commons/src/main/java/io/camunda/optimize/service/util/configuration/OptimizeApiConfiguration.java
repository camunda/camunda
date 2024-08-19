/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OptimizeApiConfiguration {

  @JsonProperty("accessToken")
  private String accessToken;

  @JsonProperty("jwtSetUri")
  private String jwtSetUri;

  @JsonProperty("audience")
  private String audience;

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

  protected boolean canEqual(final Object other) {
    return other instanceof OptimizeApiConfiguration;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $accessToken = getAccessToken();
    result = result * PRIME + ($accessToken == null ? 43 : $accessToken.hashCode());
    final Object $jwtSetUri = getJwtSetUri();
    result = result * PRIME + ($jwtSetUri == null ? 43 : $jwtSetUri.hashCode());
    final Object $audience = getAudience();
    result = result * PRIME + ($audience == null ? 43 : $audience.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof OptimizeApiConfiguration)) {
      return false;
    }
    final OptimizeApiConfiguration other = (OptimizeApiConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$accessToken = getAccessToken();
    final Object other$accessToken = other.getAccessToken();
    if (this$accessToken == null
        ? other$accessToken != null
        : !this$accessToken.equals(other$accessToken)) {
      return false;
    }
    final Object this$jwtSetUri = getJwtSetUri();
    final Object other$jwtSetUri = other.getJwtSetUri();
    if (this$jwtSetUri == null
        ? other$jwtSetUri != null
        : !this$jwtSetUri.equals(other$jwtSetUri)) {
      return false;
    }
    final Object this$audience = getAudience();
    final Object other$audience = other.getAudience();
    if (this$audience == null ? other$audience != null : !this$audience.equals(other$audience)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "OptimizeApiConfiguration(accessToken="
        + getAccessToken()
        + ", jwtSetUri="
        + getJwtSetUri()
        + ", audience="
        + getAudience()
        + ")";
  }
}
