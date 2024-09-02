/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class MinMaxStatDto {

  private double min;
  private double max;

  private String minAsString;
  private String maxAsString;

  public MinMaxStatDto(final double min, final double max) {
    this.min = min;
    this.max = max;
    minAsString = Double.toString(min);
    maxAsString = Double.toString(max);
  }

  public MinMaxStatDto(
      final double min, final double max, final String minAsString, final String maxAsString) {
    this.min = min;
    this.max = max;
    this.minAsString = minAsString;
    this.maxAsString = maxAsString;
  }

  public double getRange() {
    return max - min;
  }

  public boolean isMinValid() {
    return Double.isFinite(min);
  }

  public boolean isMaxValid() {
    return Double.isFinite(max);
  }

  public boolean isValidRange() {
    return isMinValid() && isMaxValid() && min != max;
  }

  public boolean isEmpty() {
    // occurs when there is no data to be evaluated for min and max fields
    return !isMinValid() && !isMaxValid();
  }
}
