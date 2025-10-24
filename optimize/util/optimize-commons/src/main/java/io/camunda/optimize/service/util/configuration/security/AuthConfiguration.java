/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.optimize.util.SuppressionConstants;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class AuthConfiguration {

  @JsonProperty("cloud")
  private CloudAuthConfiguration cloudAuthConfiguration;

  @JsonProperty("ccsm")
  private CCSMAuthConfiguration ccsmAuthConfiguration;

  @JsonProperty("token.lifeMin")
  private Integer tokenLifeTime;

  @JsonProperty("token.secret")
  private String tokenSecret;

  @JsonProperty("cookie")
  private CookieConfiguration cookieConfiguration;

  public AuthConfiguration() {}

  @JsonIgnore
  public int getTokenLifeTimeMinutes() {
    return tokenLifeTime;
  }

  public Optional<String> getTokenSecret() {
    return Optional.ofNullable(tokenSecret);
  }

  @JsonProperty("token.secret")
  public void setTokenSecret(final String tokenSecret) {
    this.tokenSecret = tokenSecret;
  }

  @SuppressWarnings(SuppressionConstants.UNUSED)
  @JsonProperty("token")
  private void unpackToken(final Map<String, String> token) {
    tokenLifeTime = Integer.valueOf(token.get("lifeMin"));
    tokenSecret = token.get("secret");
  }

  public CloudAuthConfiguration getCloudAuthConfiguration() {
    return cloudAuthConfiguration;
  }

  @JsonProperty("cloud")
  public void setCloudAuthConfiguration(final CloudAuthConfiguration cloudAuthConfiguration) {
    this.cloudAuthConfiguration = cloudAuthConfiguration;
  }

  public CCSMAuthConfiguration getCcsmAuthConfiguration() {
    return ccsmAuthConfiguration;
  }

  @JsonProperty("ccsm")
  public void setCcsmAuthConfiguration(final CCSMAuthConfiguration ccsmAuthConfiguration) {
    this.ccsmAuthConfiguration = ccsmAuthConfiguration;
  }

  public Integer getTokenLifeTime() {
    return tokenLifeTime;
  }

  @JsonProperty("token.lifeMin")
  public void setTokenLifeTime(final Integer tokenLifeTime) {
    this.tokenLifeTime = tokenLifeTime;
  }

  public CookieConfiguration getCookieConfiguration() {
    return cookieConfiguration;
  }

  @JsonProperty("cookie")
  public void setCookieConfiguration(final CookieConfiguration cookieConfiguration) {
    this.cookieConfiguration = cookieConfiguration;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof AuthConfiguration;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final AuthConfiguration that = (AuthConfiguration) o;
    return Objects.equals(cloudAuthConfiguration, that.cloudAuthConfiguration)
        && Objects.equals(ccsmAuthConfiguration, that.ccsmAuthConfiguration)
        && Objects.equals(tokenLifeTime, that.tokenLifeTime)
        && Objects.equals(tokenSecret, that.tokenSecret)
        && Objects.equals(cookieConfiguration, that.cookieConfiguration);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        cloudAuthConfiguration,
        ccsmAuthConfiguration,
        tokenLifeTime,
        tokenSecret,
        cookieConfiguration);
  }

  @Override
  public String toString() {
    return "AuthConfiguration(cloudAuthConfiguration="
        + getCloudAuthConfiguration()
        + ", ccsmAuthConfiguration="
        + getCcsmAuthConfiguration()
        + ", tokenLifeTime="
        + getTokenLifeTime()
        + ", tokenSecret="
        + getTokenSecret()
        + ", cookieConfiguration="
        + getCookieConfiguration()
        + ")";
  }
}
