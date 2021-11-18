/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util.configuration.tracking;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrackingConfiguration {
  @JsonProperty("enabled")
  private boolean enabled;
  @JsonProperty("mixpanel")
  private MixpanelConfiguration mixpanel = new MixpanelConfiguration();

  @JsonIgnore
  public String getMixpanelToken() {
    return mixpanel.getToken();
  }

}
