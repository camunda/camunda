/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import io.camunda.optimize.rest.constants.RestConstants;
import io.camunda.optimize.util.SuppressionConstants;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class CookieConfiguration {

  @JsonProperty("same-site.enabled")
  private boolean sameSiteFlagEnabled;

  @JsonProperty("secure")
  private CookieSecureMode cookieSecureMode;

  @JsonProperty("maxSize")
  private Integer maxSize;

  public CookieConfiguration() {}

  public boolean resolveSecureFlagValue(final String requestScheme) {
    return Optional.ofNullable(cookieSecureMode)
        .map(mode -> mode.resolveSecureValue(requestScheme))
        .orElse(false);
  }

  @SuppressWarnings(SuppressionConstants.UNUSED)
  @JsonProperty("same-site")
  private void unpackSameSite(final Map<String, Boolean> sameSite) {
    sameSiteFlagEnabled = sameSite.get("enabled");
  }

  public boolean isSameSiteFlagEnabled() {
    return sameSiteFlagEnabled;
  }

  @JsonProperty("same-site.enabled")
  public void setSameSiteFlagEnabled(final boolean sameSiteFlagEnabled) {
    this.sameSiteFlagEnabled = sameSiteFlagEnabled;
  }

  public CookieSecureMode getCookieSecureMode() {
    return cookieSecureMode;
  }

  @JsonProperty("secure")
  public void setCookieSecureMode(final CookieSecureMode cookieSecureMode) {
    this.cookieSecureMode = cookieSecureMode;
  }

  public Integer getMaxSize() {
    return maxSize;
  }

  @JsonProperty("maxSize")
  public void setMaxSize(final Integer maxSize) {
    this.maxSize = maxSize;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof CookieConfiguration;
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
    return "CookieConfiguration(sameSiteFlagEnabled="
        + isSameSiteFlagEnabled()
        + ", cookieSecureMode="
        + getCookieSecureMode()
        + ", maxSize="
        + getMaxSize()
        + ")";
  }

  public enum CookieSecureMode {
    AUTO,
    TRUE,
    FALSE;

    public boolean resolveSecureValue(final String requestScheme) {
      switch (this) {
        case TRUE:
          return true;
        case FALSE:
          return false;
        case AUTO:
        default:
          return RestConstants.HTTPS_SCHEME.equalsIgnoreCase(requestScheme);
      }
    }

    @JsonValue
    public String getId() {
      return name().toLowerCase(Locale.ENGLISH);
    }

    @Override
    public String toString() {
      return getId();
    }
  }
}
