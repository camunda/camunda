/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.combined.configuration.target_value;

import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class CombinedReportCountChartDto {

  private Boolean isBelow = false;
  private String value = "100";

  public CombinedReportCountChartDto(Boolean isBelow, String value) {
    this.isBelow = isBelow;
    this.value = value;
  }

  public CombinedReportCountChartDto() {}

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof final CombinedReportCountChartDto that)) {
      return false;
    }
    return Objects.equals(isBelow, that.isBelow) && Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(isBelow, value);
  }
}
