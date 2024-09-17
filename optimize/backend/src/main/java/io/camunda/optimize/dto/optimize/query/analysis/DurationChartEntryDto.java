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
public class DurationChartEntryDto {

  private Long key;
  private Long value;
  private boolean outlier;

  public DurationChartEntryDto(Long key, Long value, boolean outlier) {
    this.key = key;
    this.value = value;
    this.outlier = outlier;
  }

  public DurationChartEntryDto() {}
}
