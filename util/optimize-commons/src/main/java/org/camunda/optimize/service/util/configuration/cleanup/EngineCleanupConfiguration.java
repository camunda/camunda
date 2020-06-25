/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util.configuration.cleanup;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;

import java.time.Period;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.HISTORY_CLEANUP;

@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@JsonIgnoreProperties(ignoreUnknown = false)
public class EngineCleanupConfiguration {
  @JsonProperty("enabled")
  private boolean enabled;
  @JsonProperty("ttl")
  private Period defaultTtl;
  @JsonProperty("processDataCleanupMode")
  private CleanupMode defaultProcessDataCleanupMode;
  @JsonProperty("perProcessDefinitionConfig")
  private Map<String, ProcessDefinitionCleanupConfiguration> processDefinitionSpecificConfiguration = new HashMap<>();
  @JsonProperty("perDecisionDefinitionConfig")
  private Map<String, DecisionDefinitionCleanupConfiguration> decisionDefinitionSpecificConfiguration = new HashMap<>();

  public EngineCleanupConfiguration(final boolean enabled, final Period defaultTtl,
                                    final CleanupMode defaultProcessDataCleanupMode) {
    this.enabled = enabled;
    this.defaultTtl = defaultTtl;
    this.defaultProcessDataCleanupMode = defaultProcessDataCleanupMode;
  }

  public void validate() {
    if (defaultTtl == null) {
      throw new OptimizeConfigurationException(HISTORY_CLEANUP + ".engineDataCleanup.ttl must be set");
    }
    if (defaultProcessDataCleanupMode == null) {
      throw new OptimizeConfigurationException(HISTORY_CLEANUP + ".engineDataCleanup.mode must be set");
    }
  }

  public Set<String> getAllProcessSpecificConfigurationKeys() {
    return new HashSet<>(processDefinitionSpecificConfiguration.keySet());
  }

  public ProcessDefinitionCleanupConfiguration getProcessDefinitionCleanupConfigurationForKey(final String processDefinitionKey) {
    final Optional<ProcessDefinitionCleanupConfiguration> keySpecificConfig =
      Optional.ofNullable(processDefinitionSpecificConfiguration)
        .flatMap(map -> Optional.ofNullable(map.get(processDefinitionKey)));

    return new ProcessDefinitionCleanupConfiguration(
      keySpecificConfig.flatMap(config -> Optional.ofNullable(config.getTtl())).orElse(getDefaultTtl()),
      keySpecificConfig.flatMap(config -> Optional.ofNullable(config.getProcessDataCleanupMode())).orElse(
        getDefaultProcessDataCleanupMode())
    );
  }

  public Set<String> getAllDecisionSpecificConfigurationKeys() {
    return new HashSet<>(decisionDefinitionSpecificConfiguration.keySet());
  }

  public DecisionDefinitionCleanupConfiguration getDecisionDefinitionCleanupConfigurationForKey(final String decisionDefinitionKey) {
    final Optional<DecisionDefinitionCleanupConfiguration> keySpecificConfig =
      Optional.ofNullable(decisionDefinitionSpecificConfiguration)
        .flatMap(map -> Optional.ofNullable(map.get(decisionDefinitionKey)));

    return new DecisionDefinitionCleanupConfiguration(
      keySpecificConfig.flatMap(config -> Optional.ofNullable(config.getTtl())).orElse(getDefaultTtl())
    );
  }

  public void setProcessDefinitionSpecificConfiguration(
    Map<String, ProcessDefinitionCleanupConfiguration> processDefinitionSpecificConfiguration) {
    this.processDefinitionSpecificConfiguration = Optional.ofNullable(processDefinitionSpecificConfiguration)
      .orElse(new HashMap<>());
  }

  public void setDecisionDefinitionSpecificConfiguration(
    Map<String, DecisionDefinitionCleanupConfiguration> decisionDefinitionSpecificConfiguration) {
    this.decisionDefinitionSpecificConfiguration = Optional.ofNullable(decisionDefinitionSpecificConfiguration)
      .orElse(new HashMap<>());
  }
}
