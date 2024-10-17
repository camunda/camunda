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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = false)
public class DecisionCleanupConfiguration {

  @JsonProperty("enabled")
  private boolean enabled;

  @JsonProperty("perDecisionDefinitionConfig")
  private Map<String, DecisionDefinitionCleanupConfiguration>
      decisionDefinitionSpecificConfiguration = new HashMap<>();

  public DecisionCleanupConfiguration(final boolean enabled) {
    this.enabled = enabled;
  }

  public DecisionCleanupConfiguration() {}

  public Set<String> getAllDecisionSpecificConfigurationKeys() {
    return new HashSet<>(decisionDefinitionSpecificConfiguration.keySet());
  }

  public boolean isEnabled() {
    return enabled;
  }

  @JsonProperty("enabled")
  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public Map<String, DecisionDefinitionCleanupConfiguration>
      getDecisionDefinitionSpecificConfiguration() {
    return decisionDefinitionSpecificConfiguration;
  }

  public void setDecisionDefinitionSpecificConfiguration(
      final Map<String, DecisionDefinitionCleanupConfiguration>
          decisionDefinitionSpecificConfiguration) {
    this.decisionDefinitionSpecificConfiguration =
        Optional.ofNullable(decisionDefinitionSpecificConfiguration).orElse(new HashMap<>());
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DecisionCleanupConfiguration;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + (isEnabled() ? 79 : 97);
    final Object $decisionDefinitionSpecificConfiguration =
        getDecisionDefinitionSpecificConfiguration();
    result =
        result * PRIME
            + ($decisionDefinitionSpecificConfiguration == null
                ? 43
                : $decisionDefinitionSpecificConfiguration.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DecisionCleanupConfiguration)) {
      return false;
    }
    final DecisionCleanupConfiguration other = (DecisionCleanupConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (isEnabled() != other.isEnabled()) {
      return false;
    }
    final Object this$decisionDefinitionSpecificConfiguration =
        getDecisionDefinitionSpecificConfiguration();
    final Object other$decisionDefinitionSpecificConfiguration =
        other.getDecisionDefinitionSpecificConfiguration();
    if (this$decisionDefinitionSpecificConfiguration == null
        ? other$decisionDefinitionSpecificConfiguration != null
        : !this$decisionDefinitionSpecificConfiguration.equals(
            other$decisionDefinitionSpecificConfiguration)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "DecisionCleanupConfiguration(enabled="
        + isEnabled()
        + ", decisionDefinitionSpecificConfiguration="
        + getDecisionDefinitionSpecificConfiguration()
        + ")";
  }
}
