/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Data;
import org.camunda.optimize.rest.constants.RestConstants;
import org.camunda.optimize.util.SuppressionConstants;

import java.util.Map;
import java.util.Optional;

@Data
public class CookieConfiguration {

  @JsonProperty("same-site.enabled")
  private boolean sameSiteFlagEnabled;
  @JsonProperty("secure")
  private CookieSecureMode cookieSecureMode;
  @JsonProperty("maxSize")
  private Integer maxSize;

  public boolean resolveSecureFlagValue(final String requestScheme) {
    return Optional.ofNullable(this.cookieSecureMode).map(mode -> mode.resolveSecureValue(requestScheme)).orElse(false);
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
      return name().toLowerCase();
    }

    @Override
    public String toString() {
      return getId();
    }
  }
}
