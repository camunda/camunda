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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }
}
