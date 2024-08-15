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
    final int PRIME = 59;
    int result = 1;
    final Object $variableName = getVariableName();
    result = result * PRIME + ($variableName == null ? 43 : $variableName.hashCode());
    final Object $variableTerm = getVariableTerm();
    result = result * PRIME + ($variableTerm == null ? 43 : $variableTerm.hashCode());
    final Object $instanceCount = getInstanceCount();
    result = result * PRIME + ($instanceCount == null ? 43 : $instanceCount.hashCode());
    final Object $outlierRatio = getOutlierRatio();
    result = result * PRIME + ($outlierRatio == null ? 43 : $outlierRatio.hashCode());
    final Object $nonOutlierRatio = getNonOutlierRatio();
    result = result * PRIME + ($nonOutlierRatio == null ? 43 : $nonOutlierRatio.hashCode());
    final Object $outlierToAllInstancesRatio = getOutlierToAllInstancesRatio();
    result =
        result * PRIME
            + ($outlierToAllInstancesRatio == null ? 43 : $outlierToAllInstancesRatio.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof VariableTermDto)) {
      return false;
    }
    final VariableTermDto other = (VariableTermDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$variableName = getVariableName();
    final Object other$variableName = other.getVariableName();
    if (this$variableName == null
        ? other$variableName != null
        : !this$variableName.equals(other$variableName)) {
      return false;
    }
    final Object this$variableTerm = getVariableTerm();
    final Object other$variableTerm = other.getVariableTerm();
    if (this$variableTerm == null
        ? other$variableTerm != null
        : !this$variableTerm.equals(other$variableTerm)) {
      return false;
    }
    final Object this$instanceCount = getInstanceCount();
    final Object other$instanceCount = other.getInstanceCount();
    if (this$instanceCount == null
        ? other$instanceCount != null
        : !this$instanceCount.equals(other$instanceCount)) {
      return false;
    }
    final Object this$outlierRatio = getOutlierRatio();
    final Object other$outlierRatio = other.getOutlierRatio();
    if (this$outlierRatio == null
        ? other$outlierRatio != null
        : !this$outlierRatio.equals(other$outlierRatio)) {
      return false;
    }
    final Object this$nonOutlierRatio = getNonOutlierRatio();
    final Object other$nonOutlierRatio = other.getNonOutlierRatio();
    if (this$nonOutlierRatio == null
        ? other$nonOutlierRatio != null
        : !this$nonOutlierRatio.equals(other$nonOutlierRatio)) {
      return false;
    }
    final Object this$outlierToAllInstancesRatio = getOutlierToAllInstancesRatio();
    final Object other$outlierToAllInstancesRatio = other.getOutlierToAllInstancesRatio();
    if (this$outlierToAllInstancesRatio == null
        ? other$outlierToAllInstancesRatio != null
        : !this$outlierToAllInstancesRatio.equals(other$outlierToAllInstancesRatio)) {
      return false;
    }
    return true;
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
