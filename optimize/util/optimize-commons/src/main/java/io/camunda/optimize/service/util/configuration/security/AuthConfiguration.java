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
import java.util.List;
import java.util.Map;
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

  @JsonProperty("superUserIds")
  private List<String> superUserIds;

  @JsonProperty("superGroupIds")
  private List<String> superGroupIds;

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

  public List<String> getSuperUserIds() {
    return superUserIds;
  }

  @JsonProperty("superUserIds")
  public void setSuperUserIds(final List<String> superUserIds) {
    this.superUserIds = superUserIds;
  }

  public List<String> getSuperGroupIds() {
    return superGroupIds;
  }

  @JsonProperty("superGroupIds")
  public void setSuperGroupIds(final List<String> superGroupIds) {
    this.superGroupIds = superGroupIds;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof AuthConfiguration;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $cloudAuthConfiguration = getCloudAuthConfiguration();
    result =
        result * PRIME
            + ($cloudAuthConfiguration == null ? 43 : $cloudAuthConfiguration.hashCode());
    final Object $ccsmAuthConfiguration = getCcsmAuthConfiguration();
    result =
        result * PRIME + ($ccsmAuthConfiguration == null ? 43 : $ccsmAuthConfiguration.hashCode());
    final Object $tokenLifeTime = getTokenLifeTime();
    result = result * PRIME + ($tokenLifeTime == null ? 43 : $tokenLifeTime.hashCode());
    final Object $tokenSecret = getTokenSecret();
    result = result * PRIME + ($tokenSecret == null ? 43 : $tokenSecret.hashCode());
    final Object $cookieConfiguration = getCookieConfiguration();
    result = result * PRIME + ($cookieConfiguration == null ? 43 : $cookieConfiguration.hashCode());
    final Object $superUserIds = getSuperUserIds();
    result = result * PRIME + ($superUserIds == null ? 43 : $superUserIds.hashCode());
    final Object $superGroupIds = getSuperGroupIds();
    result = result * PRIME + ($superGroupIds == null ? 43 : $superGroupIds.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof AuthConfiguration)) {
      return false;
    }
    final AuthConfiguration other = (AuthConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$cloudAuthConfiguration = getCloudAuthConfiguration();
    final Object other$cloudAuthConfiguration = other.getCloudAuthConfiguration();
    if (this$cloudAuthConfiguration == null
        ? other$cloudAuthConfiguration != null
        : !this$cloudAuthConfiguration.equals(other$cloudAuthConfiguration)) {
      return false;
    }
    final Object this$ccsmAuthConfiguration = getCcsmAuthConfiguration();
    final Object other$ccsmAuthConfiguration = other.getCcsmAuthConfiguration();
    if (this$ccsmAuthConfiguration == null
        ? other$ccsmAuthConfiguration != null
        : !this$ccsmAuthConfiguration.equals(other$ccsmAuthConfiguration)) {
      return false;
    }
    final Object this$tokenLifeTime = getTokenLifeTime();
    final Object other$tokenLifeTime = other.getTokenLifeTime();
    if (this$tokenLifeTime == null
        ? other$tokenLifeTime != null
        : !this$tokenLifeTime.equals(other$tokenLifeTime)) {
      return false;
    }
    final Object this$tokenSecret = getTokenSecret();
    final Object other$tokenSecret = other.getTokenSecret();
    if (this$tokenSecret == null
        ? other$tokenSecret != null
        : !this$tokenSecret.equals(other$tokenSecret)) {
      return false;
    }
    final Object this$cookieConfiguration = getCookieConfiguration();
    final Object other$cookieConfiguration = other.getCookieConfiguration();
    if (this$cookieConfiguration == null
        ? other$cookieConfiguration != null
        : !this$cookieConfiguration.equals(other$cookieConfiguration)) {
      return false;
    }
    final Object this$superUserIds = getSuperUserIds();
    final Object other$superUserIds = other.getSuperUserIds();
    if (this$superUserIds == null
        ? other$superUserIds != null
        : !this$superUserIds.equals(other$superUserIds)) {
      return false;
    }
    final Object this$superGroupIds = getSuperGroupIds();
    final Object other$superGroupIds = other.getSuperGroupIds();
    if (this$superGroupIds == null
        ? other$superGroupIds != null
        : !this$superGroupIds.equals(other$superGroupIds)) {
      return false;
    }
    return true;
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
        + ", superUserIds="
        + getSuperUserIds()
        + ", superGroupIds="
        + getSuperGroupIds()
        + ")";
  }
}
