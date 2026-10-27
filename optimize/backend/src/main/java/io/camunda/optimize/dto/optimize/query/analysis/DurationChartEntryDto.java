/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.analysis;

import java.util.Objects;

public class DurationChartEntryDto {

  private Long key;
  private Long value;
  private boolean outlier;

  public DurationChartEntryDto(final Long key, final Long value, final boolean outlier) {
    this.key = key;
    this.value = value;
    this.outlier = outlier;
  }

  public DurationChartEntryDto() {}

  public Long getKey() {
    return key;
  }

  public void setKey(final Long key) {
    this.key = key;
  }

  public Long getValue() {
    return value;
  }

  public void setValue(final Long value) {
    this.value = value;
  }

  public boolean isOutlier() {
    return outlier;
  }

  public void setOutlier(final boolean outlier) {
    this.outlier = outlier;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DurationChartEntryDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DurationChartEntryDto that = (DurationChartEntryDto) o;
    return outlier == that.outlier
        && Objects.equals(key, that.key)
        && Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, value, outlier);
  }

  @Override
  public String toString() {
    return "DurationChartEntryDto(key="
        + getKey()
        + ", value="
        + getValue()
        + ", outlier="
        + isOutlier()
        + ")";
  }
}
