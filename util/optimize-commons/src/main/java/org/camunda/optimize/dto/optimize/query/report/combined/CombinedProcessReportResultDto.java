/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.combined;

import lombok.Getter;
import lombok.Setter;
import org.camunda.optimize.dto.optimize.query.report.ReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.SingleReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;

import java.util.Map;
import java.util.Optional;

public class CombinedProcessReportResultDto<RESULT extends SingleReportResultDto> implements ReportResultDto {

  @Getter
  @Setter
  protected Map<String, ReportEvaluationResult<RESULT, SingleProcessReportDefinitionRequestDto>> data;

  @Getter
  @Setter
  private long instanceCount;

  protected CombinedProcessReportResultDto() {
  }

  public CombinedProcessReportResultDto(final Map<String, ReportEvaluationResult<RESULT,
    SingleProcessReportDefinitionRequestDto>> data, final long instanceCount) {
    this.data = data;
    this.instanceCount = instanceCount;
  }

  @Override
  public ResultType getType() {
    // type is in single results
    return null;
  }

  public Optional<ResultType> getSingleReportResultType() {
    return data.values().stream().findFirst().map(ReportEvaluationResult::getResultAsDto).map(ReportResultDto::getType);
  }
}
