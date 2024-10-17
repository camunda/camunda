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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
