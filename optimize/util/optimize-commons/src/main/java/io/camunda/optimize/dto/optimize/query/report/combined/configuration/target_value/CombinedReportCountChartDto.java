/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.combined.configuration.target_value;

import java.util.Objects;

public class CombinedReportCountChartDto {

  private Boolean isBelow = false;
  private String value = "100";

  public CombinedReportCountChartDto(final Boolean isBelow, final String value) {
    this.isBelow = isBelow;
    this.value = value;
  }

  public CombinedReportCountChartDto() {}

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final CombinedReportCountChartDto that = (CombinedReportCountChartDto) o;
    return Objects.equals(isBelow, that.isBelow) && Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(isBelow, value);
  }

  @Override
  public String toString() {
    return "CombinedReportCountChartDto(isBelow=" + getIsBelow() + ", value=" + getValue() + ")";
  }

  public Boolean getIsBelow() {
    return isBelow;
  }

  public void setIsBelow(final Boolean isBelow) {
    this.isBelow = isBelow;
  }

  public String getValue() {
    return value;
  }

  public void setValue(final String value) {
    this.value = value;
  }
}
