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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
