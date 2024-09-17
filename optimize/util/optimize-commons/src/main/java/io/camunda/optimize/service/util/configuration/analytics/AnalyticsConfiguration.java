/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.analytics;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AnalyticsConfiguration {

  @JsonProperty("enabled")
  private boolean enabled;

  @JsonProperty("mixpanel")
  private MixpanelConfiguration mixpanel;

  @JsonProperty("osano")
  private OsanoConfiguration osano;

  public AnalyticsConfiguration(
      boolean enabled, MixpanelConfiguration mixpanel, OsanoConfiguration osano) {
    this.enabled = enabled;
    this.mixpanel = mixpanel;
    this.osano = osano;
  }

  protected AnalyticsConfiguration() {}
}
