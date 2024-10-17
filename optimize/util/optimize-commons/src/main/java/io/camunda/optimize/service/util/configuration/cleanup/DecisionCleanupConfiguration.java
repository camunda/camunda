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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
