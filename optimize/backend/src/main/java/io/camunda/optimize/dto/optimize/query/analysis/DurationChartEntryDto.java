/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.analysis;

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
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $key = getKey();
    result = result * PRIME + ($key == null ? 43 : $key.hashCode());
    final Object $value = getValue();
    result = result * PRIME + ($value == null ? 43 : $value.hashCode());
    result = result * PRIME + (isOutlier() ? 79 : 97);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DurationChartEntryDto)) {
      return false;
    }
    final DurationChartEntryDto other = (DurationChartEntryDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$key = getKey();
    final Object other$key = other.getKey();
    if (this$key == null ? other$key != null : !this$key.equals(other$key)) {
      return false;
    }
    final Object this$value = getValue();
    final Object other$value = other.getValue();
    if (this$value == null ? other$value != null : !this$value.equals(other$value)) {
      return false;
    }
    if (isOutlier() != other.isOutlier()) {
      return false;
    }
    return true;
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
