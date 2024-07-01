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
import lombok.Data;

@Data
public class CookieConfiguration {

  @JsonProperty("same-site.enabled")
  private boolean sameSiteFlagEnabled;

  @JsonProperty("secure")
  private CookieSecureMode cookieSecureMode;

  @JsonProperty("maxSize")
  private Integer maxSize;

  public boolean resolveSecureFlagValue(final String requestScheme) {
    return Optional.ofNullable(this.cookieSecureMode)
        .map(mode -> mode.resolveSecureValue(requestScheme))
        .orElse(false);
  }

  @SuppressWarnings(SuppressionConstants.UNUSED)
  @JsonProperty("same-site")
  private void unpackSameSite(final Map<String, Boolean> sameSite) {
    this.sameSiteFlagEnabled = sameSite.get("enabled");
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
