/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.cleanup;

import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.HISTORY_CLEANUP;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import io.camunda.optimize.service.util.CronNormalizerUtil;
import java.time.Period;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CleanupConfiguration {
  @JsonProperty("cronTrigger")
  private String cronTrigger;

  @JsonProperty("ttl")
  private Period ttl;

  @JsonProperty("processDataCleanup")
  private ProcessCleanupConfiguration processDataCleanupConfiguration;

  @JsonProperty("externalVariableCleanup")
  private ExternalVariableCleanupConfiguration externalVariableCleanupConfiguration;

  public CleanupConfiguration(final String cronTrigger, final Period ttl) {
    this(cronTrigger, ttl, new ProcessCleanupConfiguration());
  }

  public CleanupConfiguration(
      final String cronTrigger,
      final Period ttl,
      final ProcessCleanupConfiguration processDataCleanupConfiguration) {
    setCronTrigger(cronTrigger);
    this.ttl = ttl;
    this.processDataCleanupConfiguration = processDataCleanupConfiguration;
  }

  public void validate() {
    if (ttl == null) {
      throw new OptimizeConfigurationException(HISTORY_CLEANUP + ".ttl must be set");
    }
    if (cronTrigger == null || cronTrigger.isEmpty()) {
      throw new OptimizeConfigurationException(
          HISTORY_CLEANUP + ".cronTrigger must be set and not empty");
    }
    processDataCleanupConfiguration.validate();
  }

  // not relevant for serialization but only for application logic
  @JsonIgnore
  public boolean isEnabled() {
    return processDataCleanupConfiguration.isEnabled();
  }

  public final void setCronTrigger(String cronTrigger) {
    this.cronTrigger =
        Optional.ofNullable(cronTrigger).map(CronNormalizerUtil::normalizeToSixParts).orElse(null);
  }

  public ProcessDefinitionCleanupConfiguration getProcessDefinitionCleanupConfigurationForKey(
      final String processDefinitionKey) {
    final Optional<ProcessDefinitionCleanupConfiguration> keySpecificConfig =
        Optional.ofNullable(processDataCleanupConfiguration)
            .map(ProcessCleanupConfiguration::getProcessDefinitionSpecificConfiguration)
            .flatMap(map -> Optional.ofNullable(map.get(processDefinitionKey)));

    return new ProcessDefinitionCleanupConfiguration(
        keySpecificConfig.flatMap(config -> Optional.ofNullable(config.getTtl())).orElse(getTtl()),
        keySpecificConfig
            .flatMap(config -> Optional.ofNullable(config.getCleanupMode()))
            .orElse(processDataCleanupConfiguration.getCleanupMode()));
  }
}
