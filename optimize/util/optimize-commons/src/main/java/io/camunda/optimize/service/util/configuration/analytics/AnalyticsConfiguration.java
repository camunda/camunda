/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.analytics;

import com.fasterxml.jackson.annotation.JsonProperty;

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
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + (isEnabled() ? 79 : 97);
    final Object $mixpanel = getMixpanel();
    result = result * PRIME + ($mixpanel == null ? 43 : $mixpanel.hashCode());
    final Object $osano = getOsano();
    result = result * PRIME + ($osano == null ? 43 : $osano.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof AnalyticsConfiguration)) {
      return false;
    }
    final AnalyticsConfiguration other = (AnalyticsConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (isEnabled() != other.isEnabled()) {
      return false;
    }
    final Object this$mixpanel = getMixpanel();
    final Object other$mixpanel = other.getMixpanel();
    if (this$mixpanel == null ? other$mixpanel != null : !this$mixpanel.equals(other$mixpanel)) {
      return false;
    }
    final Object this$osano = getOsano();
    final Object other$osano = other.getOsano();
    if (this$osano == null ? other$osano != null : !this$osano.equals(other$osano)) {
      return false;
    }
    return true;
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
