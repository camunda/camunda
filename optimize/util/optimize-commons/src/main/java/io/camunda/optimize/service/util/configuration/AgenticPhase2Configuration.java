/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import java.util.Objects;

public class AgenticPhase2Configuration {

  private Boolean enabled;

  public AgenticPhase2Configuration() {}

  /**
   * Whether the agentic Phase 2 imports and endpoints are active.
   *
   * <p>Absent configuration reads as disabled: Phase 2 is unreleased, so a configuration mistake
   * must not switch it on for existing users.
   */
  public boolean isEnabled() {
    return Boolean.TRUE.equals(enabled);
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(final Boolean enabled) {
    this.enabled = enabled;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(enabled);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    return Objects.equals(enabled, ((AgenticPhase2Configuration) o).enabled);
  }

  @Override
  public String toString() {
    return "AgenticPhase2Configuration(enabled=" + getEnabled() + ")";
  }
}
