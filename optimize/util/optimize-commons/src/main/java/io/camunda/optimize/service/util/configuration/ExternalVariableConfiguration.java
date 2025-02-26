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

  private IndexRolloverConfiguration variableIndexRollover;

  @JsonProperty("import")
  private ExternalVariableImportConfiguration importConfiguration;

  public ExternalVariableConfiguration() {}

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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "ExternalVariableConfiguration(variableIndexRollover="
        + getVariableIndexRollover()
        + ", importConfiguration="
        + getImportConfiguration()
        + ")";
  }
}
