/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.combined;

import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;
import org.camunda.optimize.service.es.report.result.ReportEvaluationResult;

import java.util.Map;

public class CombinedProcessReportResultDto<RESULT extends ProcessReportResultDto> implements ReportResultDto {

  protected Map<String, ReportEvaluationResult<RESULT, SingleProcessReportDefinitionDto>> data;

  protected CombinedProcessReportResultDto() {
  }

  public CombinedProcessReportResultDto(final Map<String, ReportEvaluationResult<RESULT, SingleProcessReportDefinitionDto>> data) {
    this.data = data;
  }

  public Map<String, ReportEvaluationResult<RESULT, SingleProcessReportDefinitionDto>> getData() {
    return data;
  }

  public void setData(Map<String, ReportEvaluationResult<RESULT, SingleProcessReportDefinitionDto>> data) {
    this.data = data;
  }

  @Override
  public ResultType getType() {
    // type is in single results
    return null;
  }
}
