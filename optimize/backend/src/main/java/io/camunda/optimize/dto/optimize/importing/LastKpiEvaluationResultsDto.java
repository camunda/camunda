/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.importing;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import java.util.Map;

public class LastKpiEvaluationResultsDto implements OptimizeDto {

  final Map<String, String> reportIdToValue;

  public LastKpiEvaluationResultsDto(final Map<String, String> reportIdToValue) {
    this.reportIdToValue = reportIdToValue;
  }

  public Map<String, String> getReportIdToValue() {
    return reportIdToValue;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof LastKpiEvaluationResultsDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $reportIdToValue = getReportIdToValue();
    result = result * PRIME + ($reportIdToValue == null ? 43 : $reportIdToValue.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof LastKpiEvaluationResultsDto)) {
      return false;
    }
    final LastKpiEvaluationResultsDto other = (LastKpiEvaluationResultsDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (reportIdToValue == null
        ? other.reportIdToValue != null
        : !reportIdToValue.equals(other.reportIdToValue)) {
      return false;
    }
    return true;
  }
}
