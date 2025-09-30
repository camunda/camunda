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
import java.util.Objects;
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
    return Objects.hash(enabled, cleanupMode, batchSize, processDefinitionSpecificConfiguration);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProcessCleanupConfiguration that = (ProcessCleanupConfiguration) o;
    return enabled == that.enabled
        && Objects.equals(cleanupMode, that.cleanupMode)
        && Objects.equals(batchSize, that.batchSize)
        && Objects.equals(
            processDefinitionSpecificConfiguration, that.processDefinitionSpecificConfiguration);
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
