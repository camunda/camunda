/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.analysis;

import lombok.Data;

@Data
public class VariableTermDto {

  private String variableName;
  private String variableTerm;
  private Long instanceCount;
  private Double outlierRatio;
  private Double nonOutlierRatio;
  private Double outlierToAllInstancesRatio;

  public VariableTermDto(
      String variableName,
      String variableTerm,
      Long instanceCount,
      Double outlierRatio,
      Double nonOutlierRatio,
      Double outlierToAllInstancesRatio) {
    this.variableName = variableName;
    this.variableTerm = variableTerm;
    this.instanceCount = instanceCount;
    this.outlierRatio = outlierRatio;
    this.nonOutlierRatio = nonOutlierRatio;
    this.outlierToAllInstancesRatio = outlierToAllInstancesRatio;
  }

  public VariableTermDto() {}
}
