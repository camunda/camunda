/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.analytics;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public class AnalyticsConfiguration {

  @JsonProperty("enabled")
  private boolean enabled;

  @JsonProperty("mixpanel")
  private MixpanelConfiguration mixpanel;

  @JsonProperty("osano")
  private OsanoConfiguration osano;

  public AnalyticsConfiguration(
      final boolean enabled, final MixpanelConfiguration mixpanel, final OsanoConfiguration osano) {
    this.enabled = enabled;
    this.mixpanel = mixpanel;
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

  public MixpanelConfiguration getMixpanel() {
    return mixpanel;
  }

  @JsonProperty("mixpanel")
  public void setMixpanel(final MixpanelConfiguration mixpanel) {
    this.mixpanel = mixpanel;
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
    return Objects.hash(mixpanel, osano);
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
    return Objects.equals(mixpanel, that.mixpanel) && Objects.equals(osano, that.osano);
  }

  @Override
  public String toString() {
    return "AnalyticsConfiguration(enabled="
        + isEnabled()
        + ", mixpanel="
        + getMixpanel()
        + ", osano="
        + getOsano()
        + ")";
  }
}
