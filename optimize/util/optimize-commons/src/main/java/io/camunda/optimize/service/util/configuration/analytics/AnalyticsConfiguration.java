/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.analytics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

// Removed Mixpanel integration; keep accepting the key so existing config files still load.
@JsonIgnoreProperties("mixpanel")
public class AnalyticsConfiguration {

  @JsonProperty("enabled")
  private boolean enabled;

  @JsonProperty("osano")
  private OsanoConfiguration osano;

  public AnalyticsConfiguration(final boolean enabled, final OsanoConfiguration osano) {
    this.enabled = enabled;
    this.osano = osano;
  }

  protected AnalyticsConfiguration() {}

  public boolean isEnabled() {
    return enabled;
  }

  @JsonProperty("enabled")
  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public OsanoConfiguration getOsano() {
    return osano;
  }

  @JsonProperty("osano")
  public void setOsano(final OsanoConfiguration osano) {
    this.osano = osano;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof AnalyticsConfiguration;
  }

  @Override
  public int hashCode() {
    return Objects.hash(osano);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final AnalyticsConfiguration that = (AnalyticsConfiguration) o;
    return Objects.equals(osano, that.osano);
  }

  @Override
  public String toString() {
    return "AnalyticsConfiguration(enabled=" + isEnabled() + ", osano=" + getOsano() + ")";
  }
}
