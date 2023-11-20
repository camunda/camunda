/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.CCSM_PROFILE;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.CLOUD_PROFILE;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.PLATFORM_PROFILE;

@Getter
@AllArgsConstructor
public enum OptimizeProfile {

  PLATFORM(PLATFORM_PROFILE),
  CCSM(CCSM_PROFILE),
  CLOUD(CLOUD_PROFILE);

  private String id;

  @JsonValue
  public String getId() {
    return this.name().toLowerCase();
  }

  public static OptimizeProfile toProfile(final String profileString) {
    return valueOf(profileString.toUpperCase());
  }

}
