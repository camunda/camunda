/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public class ExternalVariableConfiguration {

  @JsonProperty("import")
  private ExternalVariableImportConfiguration importConfiguration;

  public ExternalVariableConfiguration() {}

  public ExternalVariableImportConfiguration getImportConfiguration() {
    return importConfiguration;
  }

  @JsonProperty("import")
  public void setImportConfiguration(
      final ExternalVariableImportConfiguration importConfiguration) {
    this.importConfiguration = importConfiguration;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ExternalVariableConfiguration;
  }

  @Override
  public int hashCode() {
    return Objects.hash(importConfiguration);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ExternalVariableConfiguration that = (ExternalVariableConfiguration) o;
    return Objects.equals(importConfiguration, that.importConfiguration);
  }

  @Override
  public String toString() {
    return "ExternalVariableConfiguration(importConfiguration=" + getImportConfiguration() + ")";
  }
}
