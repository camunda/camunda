/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.analysis;

public class VariableTermDto {

  private String variableName;
  private String variableTerm;
  private Long instanceCount;
  private Double outlierRatio;
  private Double nonOutlierRatio;
  private Double outlierToAllInstancesRatio;

  public VariableTermDto(
      final String variableName,
      final String variableTerm,
      final Long instanceCount,
      final Double outlierRatio,
      final Double nonOutlierRatio,
      final Double outlierToAllInstancesRatio) {
    this.variableName = variableName;
    this.variableTerm = variableTerm;
    this.instanceCount = instanceCount;
    this.outlierRatio = outlierRatio;
    this.nonOutlierRatio = nonOutlierRatio;
    this.outlierToAllInstancesRatio = outlierToAllInstancesRatio;
  }

  public VariableTermDto() {}

  public String getVariableName() {
    return variableName;
  }

  public void setVariableName(final String variableName) {
    this.variableName = variableName;
  }

  public String getVariableTerm() {
    return variableTerm;
  }

  public void setVariableTerm(final String variableTerm) {
    this.variableTerm = variableTerm;
  }

  public Long getInstanceCount() {
    return instanceCount;
  }

  public void setInstanceCount(final Long instanceCount) {
    this.instanceCount = instanceCount;
  }

  public Double getOutlierRatio() {
    return outlierRatio;
  }

  public void setOutlierRatio(final Double outlierRatio) {
    this.outlierRatio = outlierRatio;
  }

  public Double getNonOutlierRatio() {
    return nonOutlierRatio;
  }

  public void setNonOutlierRatio(final Double nonOutlierRatio) {
    this.nonOutlierRatio = nonOutlierRatio;
  }

  public Double getOutlierToAllInstancesRatio() {
    return outlierToAllInstancesRatio;
  }

  public void setOutlierToAllInstancesRatio(final Double outlierToAllInstancesRatio) {
    this.outlierToAllInstancesRatio = outlierToAllInstancesRatio;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof VariableTermDto;
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
    return "VariableTermDto(variableName="
        + getVariableName()
        + ", variableTerm="
        + getVariableTerm()
        + ", instanceCount="
        + getInstanceCount()
        + ", outlierRatio="
        + getOutlierRatio()
        + ", nonOutlierRatio="
        + getNonOutlierRatio()
        + ", outlierToAllInstancesRatio="
        + getOutlierToAllInstancesRatio()
        + ")";
  }
}
