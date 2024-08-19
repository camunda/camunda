/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report;

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

  public double getMin() {
    return min;
  }

  public void setMin(final double min) {
    this.min = min;
  }

  public double getMax() {
    return max;
  }

  public void setMax(final double max) {
    this.max = max;
  }

  public String getMinAsString() {
    return minAsString;
  }

  public void setMinAsString(final String minAsString) {
    this.minAsString = minAsString;
  }

  public String getMaxAsString() {
    return maxAsString;
  }

  public void setMaxAsString(final String maxAsString) {
    this.maxAsString = maxAsString;
  }

  @Override
  public String toString() {
    return "MinMaxStatDto(min="
        + getMin()
        + ", max="
        + getMax()
        + ", minAsString="
        + getMinAsString()
        + ", maxAsString="
        + getMaxAsString()
        + ")";
  }
}
