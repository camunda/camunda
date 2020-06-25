/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util.configuration.cleanup;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;

import java.time.Period;

import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.HISTORY_CLEANUP;

@Data
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@JsonIgnoreProperties(ignoreUnknown = false)
public class IngestedEventCleanupConfiguration {
  @JsonProperty("enabled")
  private boolean enabled;
  @JsonProperty("ttl")
  private Period defaultTtl;

  public void validate() {
    if (defaultTtl == null) {
      throw new OptimizeConfigurationException(HISTORY_CLEANUP + ".ingestedEventCleanup.ttl must be set");
    }
  }

}
