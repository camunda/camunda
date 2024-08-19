/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ExternalVariableConfiguration {

  private VariableIngestionConfiguration variableIngestion;
  private IndexRolloverConfiguration variableIndexRollover;

  @JsonProperty("import")
  private ExternalVariableImportConfiguration importConfiguration;

  public ExternalVariableConfiguration() {}

  public VariableIngestionConfiguration getVariableIngestion() {
    return variableIngestion;
  }

  public void setVariableIngestion(final VariableIngestionConfiguration variableIngestion) {
    this.variableIngestion = variableIngestion;
  }

  public IndexRolloverConfiguration getVariableIndexRollover() {
    return variableIndexRollover;
  }

  public void setVariableIndexRollover(final IndexRolloverConfiguration variableIndexRollover) {
    this.variableIndexRollover = variableIndexRollover;
  }

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
    final int PRIME = 59;
    int result = 1;
    final Object $variableIngestion = getVariableIngestion();
    result = result * PRIME + ($variableIngestion == null ? 43 : $variableIngestion.hashCode());
    final Object $variableIndexRollover = getVariableIndexRollover();
    result =
        result * PRIME + ($variableIndexRollover == null ? 43 : $variableIndexRollover.hashCode());
    final Object $importConfiguration = getImportConfiguration();
    result = result * PRIME + ($importConfiguration == null ? 43 : $importConfiguration.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ExternalVariableConfiguration)) {
      return false;
    }
    final ExternalVariableConfiguration other = (ExternalVariableConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$variableIngestion = getVariableIngestion();
    final Object other$variableIngestion = other.getVariableIngestion();
    if (this$variableIngestion == null
        ? other$variableIngestion != null
        : !this$variableIngestion.equals(other$variableIngestion)) {
      return false;
    }
    final Object this$variableIndexRollover = getVariableIndexRollover();
    final Object other$variableIndexRollover = other.getVariableIndexRollover();
    if (this$variableIndexRollover == null
        ? other$variableIndexRollover != null
        : !this$variableIndexRollover.equals(other$variableIndexRollover)) {
      return false;
    }
    final Object this$importConfiguration = getImportConfiguration();
    final Object other$importConfiguration = other.getImportConfiguration();
    if (this$importConfiguration == null
        ? other$importConfiguration != null
        : !this$importConfiguration.equals(other$importConfiguration)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "ExternalVariableConfiguration(variableIngestion="
        + getVariableIngestion()
        + ", variableIndexRollover="
        + getVariableIndexRollover()
        + ", importConfiguration="
        + getImportConfiguration()
        + ")";
  }
}
