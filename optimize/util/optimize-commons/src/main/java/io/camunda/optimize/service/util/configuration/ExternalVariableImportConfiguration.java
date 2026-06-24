/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import java.util.Objects;

public class ExternalVariableImportConfiguration {

  private boolean enabled;
  private int maxPageSize;

  protected ExternalVariableImportConfiguration() {}

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public int getMaxPageSize() {
    return maxPageSize;
  }

  public void setMaxPageSize(final int maxPageSize) {
    this.maxPageSize = maxPageSize;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ExternalVariableImportConfiguration;
  }

  @Override
  public int hashCode() {
    return Objects.hash(enabled, maxPageSize);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ExternalVariableImportConfiguration that = (ExternalVariableImportConfiguration) o;
    return enabled == that.enabled && maxPageSize == that.maxPageSize;
  }

  @Override
  public String toString() {
    return "ExternalVariableImportConfiguration(enabled="
        + isEnabled()
        + ", maxPageSize="
        + getMaxPageSize()
        + ")";
  }
}
