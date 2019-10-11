/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest.report;

import lombok.Getter;
import lombok.Setter;
import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.SingleReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;

import java.util.Map;

@Getter
@Setter
public class CombinedProcessReportResultDataDto<RESULT extends SingleReportResultDto> implements ReportResultDto {
  protected Map<String, AuthorizedProcessReportEvaluationResultDto<RESULT>> data;

  protected CombinedProcessReportResultDataDto() {
  }

  public CombinedProcessReportResultDataDto(final Map<String, AuthorizedProcessReportEvaluationResultDto<RESULT>> data) {
    this.data = data;
  }

  @Override
  public ResultType getType() {
    // type is in single results
    return null;
  }
}
