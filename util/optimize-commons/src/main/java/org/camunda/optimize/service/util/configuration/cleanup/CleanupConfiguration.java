/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
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

import java.time.Period;
import java.util.Optional;

import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.HISTORY_CLEANUP;

@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@JsonIgnoreProperties(ignoreUnknown = false)
public class CleanupConfiguration {
  @JsonProperty("cronTrigger")
  private String cronTrigger;
  @JsonProperty("ttl")
  private Period ttl;
  @JsonProperty("processDataCleanup")
  private ProcessCleanupConfiguration processDataCleanupConfiguration;
  @JsonProperty("decisionDataCleanup")
  private DecisionCleanupConfiguration decisionCleanupConfiguration;
  @JsonProperty("ingestedEventCleanup")
  private IngestedEventCleanupConfiguration ingestedEventCleanupConfiguration;
  @JsonProperty("externalVariableCleanup")
  private ExternalVariableCleanupConfiguration externalVariableCleanupConfiguration;

  public CleanupConfiguration(final String cronTrigger, final Period ttl) {
   this(cronTrigger, ttl, new ProcessCleanupConfiguration(), new DecisionCleanupConfiguration());
  }

  public CleanupConfiguration(final String cronTrigger,
                              final Period ttl,
                              final ProcessCleanupConfiguration processDataCleanupConfiguration,
                              final DecisionCleanupConfiguration decisionCleanupConfiguration) {
    this(cronTrigger, ttl, processDataCleanupConfiguration, decisionCleanupConfiguration, new IngestedEventCleanupConfiguration());
  }

  public CleanupConfiguration(final String cronTrigger,
                              final Period ttl,
                              final ProcessCleanupConfiguration processDataCleanupConfiguration,
                              final DecisionCleanupConfiguration decisionCleanupConfiguration,
                              final IngestedEventCleanupConfiguration ingestedEventCleanupConfiguration) {
    setCronTrigger(cronTrigger);
    this.ttl = ttl;
    this.processDataCleanupConfiguration = processDataCleanupConfiguration;
    this.decisionCleanupConfiguration = decisionCleanupConfiguration;
    this.ingestedEventCleanupConfiguration = ingestedEventCleanupConfiguration;
  }

  public void validate() {
    if (ttl == null) {
      throw new OptimizeConfigurationException(HISTORY_CLEANUP + ".ttl must be set");
    }
    if (cronTrigger == null || cronTrigger.isEmpty()) {
      throw new OptimizeConfigurationException(HISTORY_CLEANUP + ".cronTrigger must be set and not empty");
    }
    processDataCleanupConfiguration.validate();
  }

  // not relevant for serialization but only for application logic
  @JsonIgnore
  public boolean isEnabled() {
    return processDataCleanupConfiguration.isEnabled()
      || decisionCleanupConfiguration.isEnabled()
      || ingestedEventCleanupConfiguration.isEnabled();
  }

  public final void setCronTrigger(String cronTrigger) {
    this.cronTrigger = Optional.ofNullable(cronTrigger).map(CronNormalizerUtil::normalizeToSixParts).orElse(null);
  }

  public ProcessDefinitionCleanupConfiguration getProcessDefinitionCleanupConfigurationForKey(final String processDefinitionKey) {
    final Optional<ProcessDefinitionCleanupConfiguration> keySpecificConfig =
      Optional.ofNullable(processDataCleanupConfiguration)
        .map(ProcessCleanupConfiguration::getProcessDefinitionSpecificConfiguration)
        .flatMap(map -> Optional.ofNullable(map.get(processDefinitionKey)));

    return new ProcessDefinitionCleanupConfiguration(
      keySpecificConfig.flatMap(config -> Optional.ofNullable(config.getTtl())).orElse(getTtl()),
      keySpecificConfig.flatMap(config -> Optional.ofNullable(config.getCleanupMode()))
        .orElse(processDataCleanupConfiguration.getCleanupMode()));
  }

  public DecisionDefinitionCleanupConfiguration getDecisionDefinitionCleanupConfigurationForKey(final String decisionDefinitionKey) {
    final Optional<DecisionDefinitionCleanupConfiguration> keySpecificConfig =
      Optional.ofNullable(decisionCleanupConfiguration)
        .map(DecisionCleanupConfiguration::getDecisionDefinitionSpecificConfiguration)
        .flatMap(map -> Optional.ofNullable(map.get(decisionDefinitionKey)));

    return new DecisionDefinitionCleanupConfiguration(
      keySpecificConfig.flatMap(config -> Optional.ofNullable(config.getTtl())).orElse(getTtl())
    );
  }
}
