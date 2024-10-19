/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.cleanup;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ExternalVariableCleanupConfiguration {

  @JsonProperty("enabled")
  private boolean enabled;

  public ExternalVariableCleanupConfiguration(final boolean enabled) {
    this.enabled = enabled;
  }

  protected ExternalVariableCleanupConfiguration() {}

  public boolean isEnabled() {
    return enabled;
  }

  @JsonProperty("enabled")
  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ExternalVariableCleanupConfiguration;
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
    return "ExternalVariableCleanupConfiguration(enabled=" + isEnabled() + ")";
  }
}
