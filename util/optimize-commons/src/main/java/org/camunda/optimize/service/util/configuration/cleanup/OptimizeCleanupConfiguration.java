/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util.configuration.cleanup;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.util.CronNormalizerUtil;

import java.util.Optional;

import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.HISTORY_CLEANUP;

@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@JsonIgnoreProperties(ignoreUnknown = false)
public class OptimizeCleanupConfiguration {
  @JsonProperty("cronTrigger")
  private String cronTrigger;
  @JsonProperty("engineDataCleanup")
  private EngineCleanupConfiguration engineDataCleanupConfiguration;
  @JsonProperty("ingestedEventCleanup")
  private IngestedEventCleanupConfiguration ingestedEventCleanupConfiguration;

  public OptimizeCleanupConfiguration(final String cronTrigger) {
   this(cronTrigger, new EngineCleanupConfiguration());
  }

  public OptimizeCleanupConfiguration(final String cronTrigger,
                                      final EngineCleanupConfiguration engineDataCleanupConfiguration) {
    this(cronTrigger, engineDataCleanupConfiguration, new IngestedEventCleanupConfiguration());
  }

  public OptimizeCleanupConfiguration(final String cronTrigger,
                                      final EngineCleanupConfiguration engineDataCleanupConfiguration,
                                      final IngestedEventCleanupConfiguration ingestedEventCleanupConfiguration) {
    setCronTrigger(cronTrigger);
    this.engineDataCleanupConfiguration = engineDataCleanupConfiguration;
    this.ingestedEventCleanupConfiguration = ingestedEventCleanupConfiguration;
  }

  public void validate() {
    if (cronTrigger == null || cronTrigger.isEmpty()) {
      throw new OptimizeConfigurationException(HISTORY_CLEANUP + ".cronTrigger must be set and not empty");
    }
    engineDataCleanupConfiguration.validate();
  }

  // not relevant for serialization only for application logic
  @JsonIgnore
  public boolean isEnabled() {
    return engineDataCleanupConfiguration.isEnabled() || ingestedEventCleanupConfiguration.isEnabled();
  }

  public final void setCronTrigger(String cronTrigger) {
    this.cronTrigger = Optional.ofNullable(cronTrigger).map(CronNormalizerUtil::normalizeToSixParts).orElse(null);
  }

}
