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
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + (isSameSiteFlagEnabled() ? 79 : 97);
    final Object $cookieSecureMode = getCookieSecureMode();
    result = result * PRIME + ($cookieSecureMode == null ? 43 : $cookieSecureMode.hashCode());
    final Object $maxSize = getMaxSize();
    result = result * PRIME + ($maxSize == null ? 43 : $maxSize.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof CookieConfiguration)) {
      return false;
    }
    final CookieConfiguration other = (CookieConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (isSameSiteFlagEnabled() != other.isSameSiteFlagEnabled()) {
      return false;
    }
    final Object this$cookieSecureMode = getCookieSecureMode();
    final Object other$cookieSecureMode = other.getCookieSecureMode();
    if (this$cookieSecureMode == null
        ? other$cookieSecureMode != null
        : !this$cookieSecureMode.equals(other$cookieSecureMode)) {
      return false;
    }
    final Object this$maxSize = getMaxSize();
    final Object other$maxSize = other.getMaxSize();
    if (this$maxSize == null ? other$maxSize != null : !this$maxSize.equals(other$maxSize)) {
      return false;
    }
    return true;
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
    FALSE,
    ;

    public boolean resolveSecureValue(final String requestScheme) {
      switch (this) {
        default:
        case AUTO:
          return RestConstants.HTTPS_SCHEME.equalsIgnoreCase(requestScheme);
        case TRUE:
          return true;
        case FALSE:
          return false;
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
