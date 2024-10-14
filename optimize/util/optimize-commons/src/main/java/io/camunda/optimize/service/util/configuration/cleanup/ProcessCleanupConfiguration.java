/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.cleanup;

import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.HISTORY_CLEANUP_PROCESS_DATA;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import io.camunda.optimize.util.SuppressionConstants;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = false)
public class ProcessCleanupConfiguration {

  @JsonProperty("enabled")
  private boolean enabled;

  @JsonProperty("cleanupMode")
  private CleanupMode cleanupMode = CleanupMode.ALL;

  @JsonProperty("batchSize")
  private int batchSize;

  @JsonProperty("perProcessDefinitionConfig")
  private Map<String, ProcessDefinitionCleanupConfiguration>
      processDefinitionSpecificConfiguration = new HashMap<>();

  public ProcessCleanupConfiguration(final boolean enabled) {
    this.enabled = enabled;
  }

  public ProcessCleanupConfiguration(final boolean enabled, final CleanupMode cleanupMode) {
    this.enabled = enabled;
    this.cleanupMode = cleanupMode;
  }

  public ProcessCleanupConfiguration() {}

  public void validate() {
    if (cleanupMode == null) {
      throw new OptimizeConfigurationException(
          HISTORY_CLEANUP_PROCESS_DATA + ".cleanupMode must be set");
    }
  }

  public Set<String> getAllProcessSpecificConfigurationKeys() {
    return new HashSet<>(processDefinitionSpecificConfiguration.keySet());
  }

  public boolean isEnabled() {
    return enabled;
  }

  @JsonProperty("enabled")
  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public CleanupMode getCleanupMode() {
    return cleanupMode;
  }

  @JsonProperty("cleanupMode")
  public void setCleanupMode(final CleanupMode cleanupMode) {
    this.cleanupMode = cleanupMode;
  }

  public int getBatchSize() {
    return batchSize;
  }

  @JsonProperty("batchSize")
  public void setBatchSize(final int batchSize) {
    this.batchSize = batchSize;
  }

  public Map<String, ProcessDefinitionCleanupConfiguration>
      getProcessDefinitionSpecificConfiguration() {
    return processDefinitionSpecificConfiguration;
  }

  @SuppressWarnings(SuppressionConstants.UNUSED)
  public void setProcessDefinitionSpecificConfiguration(
      final Map<String, ProcessDefinitionCleanupConfiguration>
          processDefinitionSpecificConfiguration) {
    this.processDefinitionSpecificConfiguration =
        Optional.ofNullable(processDefinitionSpecificConfiguration).orElse(new HashMap<>());
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ProcessCleanupConfiguration;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + (isEnabled() ? 79 : 97);
    final Object $cleanupMode = getCleanupMode();
    result = result * PRIME + ($cleanupMode == null ? 43 : $cleanupMode.hashCode());
    result = result * PRIME + getBatchSize();
    final Object $processDefinitionSpecificConfiguration =
        getProcessDefinitionSpecificConfiguration();
    result =
        result * PRIME
            + ($processDefinitionSpecificConfiguration == null
                ? 43
                : $processDefinitionSpecificConfiguration.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ProcessCleanupConfiguration)) {
      return false;
    }
    final ProcessCleanupConfiguration other = (ProcessCleanupConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (isEnabled() != other.isEnabled()) {
      return false;
    }
    final Object this$cleanupMode = getCleanupMode();
    final Object other$cleanupMode = other.getCleanupMode();
    if (this$cleanupMode == null
        ? other$cleanupMode != null
        : !this$cleanupMode.equals(other$cleanupMode)) {
      return false;
    }
    if (getBatchSize() != other.getBatchSize()) {
      return false;
    }
    final Object this$processDefinitionSpecificConfiguration =
        getProcessDefinitionSpecificConfiguration();
    final Object other$processDefinitionSpecificConfiguration =
        other.getProcessDefinitionSpecificConfiguration();
    if (this$processDefinitionSpecificConfiguration == null
        ? other$processDefinitionSpecificConfiguration != null
        : !this$processDefinitionSpecificConfiguration.equals(
            other$processDefinitionSpecificConfiguration)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "ProcessCleanupConfiguration(enabled="
        + isEnabled()
        + ", cleanupMode="
        + getCleanupMode()
        + ", batchSize="
        + getBatchSize()
        + ", processDefinitionSpecificConfiguration="
        + getProcessDefinitionSpecificConfiguration()
        + ")";
  }
}
