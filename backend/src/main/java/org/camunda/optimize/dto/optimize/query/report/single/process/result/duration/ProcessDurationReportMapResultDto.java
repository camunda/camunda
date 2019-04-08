/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.result.duration;

import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.LimitedResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;

import java.util.LinkedHashMap;
import java.util.Map;

public class ProcessDurationReportMapResultDto extends ProcessReportResultDto implements LimitedResultDto {

  private Map<String, AggregationResultDto> data = new LinkedHashMap<>();
  private Boolean isComplete = true;

  @Override
  public Boolean getIsComplete() {
    return isComplete;
  }

  public void setComplete(final Boolean complete) {
    isComplete = complete;
  }

  public Map<String, AggregationResultDto> getData() {
    return data;
  }

  public void setData(Map<String, AggregationResultDto> data) {
    this.data = data;
  }

  @Override
  public ResultType getType() {
    return ResultType.DURATION_MAP;
  }
}
