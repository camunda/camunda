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

@JsonIgnoreProperties(ignoreUnknown = true)
public class CleanupConfiguration {

  @JsonProperty("cronTrigger")
  private String cronTrigger;

  @JsonProperty("ttl")
  private Period ttl;

  @JsonProperty("processDataCleanup")
  private ProcessCleanupConfiguration processDataCleanupConfiguration;

  @JsonProperty("decisionDataCleanup")
  private DecisionCleanupConfiguration decisionCleanupConfiguration;

  @JsonProperty("externalVariableCleanup")
  private ExternalVariableCleanupConfiguration externalVariableCleanupConfiguration;

  public CleanupConfiguration(final String cronTrigger, final Period ttl) {
    this(cronTrigger, ttl, new ProcessCleanupConfiguration(), new DecisionCleanupConfiguration());
  }

  public CleanupConfiguration(
      final String cronTrigger,
      final Period ttl,
      final ProcessCleanupConfiguration processDataCleanupConfiguration,
      final DecisionCleanupConfiguration decisionCleanupConfiguration) {
    setCronTrigger(cronTrigger);
    this.ttl = ttl;
    this.processDataCleanupConfiguration = processDataCleanupConfiguration;
    this.decisionCleanupConfiguration = decisionCleanupConfiguration;
  }

  protected CleanupConfiguration() {}

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
    return processDataCleanupConfiguration.isEnabled() || decisionCleanupConfiguration.isEnabled();
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

  public DecisionDefinitionCleanupConfiguration getDecisionDefinitionCleanupConfigurationForKey(
      final String decisionDefinitionKey) {
    final Optional<DecisionDefinitionCleanupConfiguration> keySpecificConfig =
        Optional.ofNullable(decisionCleanupConfiguration)
            .map(DecisionCleanupConfiguration::getDecisionDefinitionSpecificConfiguration)
            .flatMap(map -> Optional.ofNullable(map.get(decisionDefinitionKey)));

    return new DecisionDefinitionCleanupConfiguration(
        keySpecificConfig.flatMap(config -> Optional.ofNullable(config.getTtl())).orElse(getTtl()));
  }

  public String getCronTrigger() {
    return cronTrigger;
  }

  public final void setCronTrigger(final String cronTrigger) {
    this.cronTrigger =
        Optional.ofNullable(cronTrigger).map(CronNormalizerUtil::normalizeToSixParts).orElse(null);
  }

  public Period getTtl() {
    return ttl;
  }

  @JsonProperty("ttl")
  public void setTtl(final Period ttl) {
    this.ttl = ttl;
  }

  public ProcessCleanupConfiguration getProcessDataCleanupConfiguration() {
    return processDataCleanupConfiguration;
  }

  @JsonProperty("processDataCleanup")
  public void setProcessDataCleanupConfiguration(
      final ProcessCleanupConfiguration processDataCleanupConfiguration) {
    this.processDataCleanupConfiguration = processDataCleanupConfiguration;
  }

  public DecisionCleanupConfiguration getDecisionCleanupConfiguration() {
    return decisionCleanupConfiguration;
  }

  @JsonProperty("decisionDataCleanup")
  public void setDecisionCleanupConfiguration(
      final DecisionCleanupConfiguration decisionCleanupConfiguration) {
    this.decisionCleanupConfiguration = decisionCleanupConfiguration;
  }

  public ExternalVariableCleanupConfiguration getExternalVariableCleanupConfiguration() {
    return externalVariableCleanupConfiguration;
  }

  @JsonProperty("externalVariableCleanup")
  public void setExternalVariableCleanupConfiguration(
      final ExternalVariableCleanupConfiguration externalVariableCleanupConfiguration) {
    this.externalVariableCleanupConfiguration = externalVariableCleanupConfiguration;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof CleanupConfiguration;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $cronTrigger = getCronTrigger();
    result = result * PRIME + ($cronTrigger == null ? 43 : $cronTrigger.hashCode());
    final Object $ttl = getTtl();
    result = result * PRIME + ($ttl == null ? 43 : $ttl.hashCode());
    final Object $processDataCleanupConfiguration = getProcessDataCleanupConfiguration();
    result =
        result * PRIME
            + ($processDataCleanupConfiguration == null
                ? 43
                : $processDataCleanupConfiguration.hashCode());
    final Object $decisionCleanupConfiguration = getDecisionCleanupConfiguration();
    result =
        result * PRIME
            + ($decisionCleanupConfiguration == null
                ? 43
                : $decisionCleanupConfiguration.hashCode());
    final Object $externalVariableCleanupConfiguration = getExternalVariableCleanupConfiguration();
    result =
        result * PRIME
            + ($externalVariableCleanupConfiguration == null
                ? 43
                : $externalVariableCleanupConfiguration.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof CleanupConfiguration)) {
      return false;
    }
    final CleanupConfiguration other = (CleanupConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$cronTrigger = getCronTrigger();
    final Object other$cronTrigger = other.getCronTrigger();
    if (this$cronTrigger == null
        ? other$cronTrigger != null
        : !this$cronTrigger.equals(other$cronTrigger)) {
      return false;
    }
    final Object this$ttl = getTtl();
    final Object other$ttl = other.getTtl();
    if (this$ttl == null ? other$ttl != null : !this$ttl.equals(other$ttl)) {
      return false;
    }
    final Object this$processDataCleanupConfiguration = getProcessDataCleanupConfiguration();
    final Object other$processDataCleanupConfiguration = other.getProcessDataCleanupConfiguration();
    if (this$processDataCleanupConfiguration == null
        ? other$processDataCleanupConfiguration != null
        : !this$processDataCleanupConfiguration.equals(other$processDataCleanupConfiguration)) {
      return false;
    }
    final Object this$decisionCleanupConfiguration = getDecisionCleanupConfiguration();
    final Object other$decisionCleanupConfiguration = other.getDecisionCleanupConfiguration();
    if (this$decisionCleanupConfiguration == null
        ? other$decisionCleanupConfiguration != null
        : !this$decisionCleanupConfiguration.equals(other$decisionCleanupConfiguration)) {
      return false;
    }
    final Object this$externalVariableCleanupConfiguration =
        getExternalVariableCleanupConfiguration();
    final Object other$externalVariableCleanupConfiguration =
        other.getExternalVariableCleanupConfiguration();
    if (this$externalVariableCleanupConfiguration == null
        ? other$externalVariableCleanupConfiguration != null
        : !this$externalVariableCleanupConfiguration.equals(
            other$externalVariableCleanupConfiguration)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "CleanupConfiguration(cronTrigger="
        + getCronTrigger()
        + ", ttl="
        + getTtl()
        + ", processDataCleanupConfiguration="
        + getProcessDataCleanupConfiguration()
        + ", decisionCleanupConfiguration="
        + getDecisionCleanupConfiguration()
        + ", externalVariableCleanupConfiguration="
        + getExternalVariableCleanupConfiguration()
        + ")";
  }
}
