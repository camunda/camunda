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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = false)
public class DecisionCleanupConfiguration {
  @JsonProperty("enabled")
  private boolean enabled;
  @JsonProperty("perDecisionDefinitionConfig")
  private Map<String, DecisionDefinitionCleanupConfiguration> decisionDefinitionSpecificConfiguration = new HashMap<>();

  public DecisionCleanupConfiguration(final boolean enabled) {
    this.enabled = enabled;
  }

  public Set<String> getAllDecisionSpecificConfigurationKeys() {
    return new HashSet<>(decisionDefinitionSpecificConfiguration.keySet());
  }

  public void setDecisionDefinitionSpecificConfiguration(
    Map<String, DecisionDefinitionCleanupConfiguration> decisionDefinitionSpecificConfiguration) {
    this.decisionDefinitionSpecificConfiguration = Optional.ofNullable(decisionDefinitionSpecificConfiguration)
      .orElse(new HashMap<>());
  }
}
