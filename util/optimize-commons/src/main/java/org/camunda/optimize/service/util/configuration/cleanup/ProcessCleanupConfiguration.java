/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration.cleanup;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.HISTORY_CLEANUP_PROCESS_DATA;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = false)
public class ProcessCleanupConfiguration {
  @JsonProperty("enabled")
  private boolean enabled;
  @JsonProperty("cleanupMode")
  private CleanupMode cleanupMode = CleanupMode.ALL;
  @JsonProperty("batchSize")
  private int batchSize;
  @JsonProperty("perProcessDefinitionConfig")
  private Map<String, ProcessDefinitionCleanupConfiguration> processDefinitionSpecificConfiguration = new HashMap<>();

  public ProcessCleanupConfiguration(final boolean enabled) {
    this.enabled = enabled;
  }

  public ProcessCleanupConfiguration(final boolean enabled,
                                     final CleanupMode cleanupMode) {
    this.enabled = enabled;
    this.cleanupMode = cleanupMode;
  }

  public void validate() {
    if (cleanupMode == null) {
      throw new OptimizeConfigurationException(HISTORY_CLEANUP_PROCESS_DATA + ".cleanupMode must be set");
    }
  }

  public Set<String> getAllProcessSpecificConfigurationKeys() {
    return new HashSet<>(processDefinitionSpecificConfiguration.keySet());
  }

  public void setProcessDefinitionSpecificConfiguration(
    Map<String, ProcessDefinitionCleanupConfiguration> processDefinitionSpecificConfiguration) {
    this.processDefinitionSpecificConfiguration = Optional.ofNullable(processDefinitionSpecificConfiguration)
      .orElse(new HashMap<>());
  }

}
