/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.cleanup;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.optimize.util.SuppressionConstants;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProcessCleanupConfiguration {
  @JsonProperty("enabled")
  private boolean enabled;

  @JsonProperty("batchSize")
  private int batchSize;

  @JsonProperty("perProcessDefinitionConfig")
  private Map<String, ProcessDefinitionCleanupConfiguration>
      processDefinitionSpecificConfiguration = new HashMap<>();

  public ProcessCleanupConfiguration(final boolean enabled) {
    this.enabled = enabled;
  }

  public Set<String> getAllProcessSpecificConfigurationKeys() {
    return new HashSet<>(processDefinitionSpecificConfiguration.keySet());
  }

  @SuppressWarnings(SuppressionConstants.UNUSED)
  public void setProcessDefinitionSpecificConfiguration(
      Map<String, ProcessDefinitionCleanupConfiguration> processDefinitionSpecificConfiguration) {
    this.processDefinitionSpecificConfiguration =
        Optional.ofNullable(processDefinitionSpecificConfiguration).orElse(new HashMap<>());
  }
}
