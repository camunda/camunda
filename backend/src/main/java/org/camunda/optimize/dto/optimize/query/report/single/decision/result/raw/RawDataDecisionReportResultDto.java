/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw;

import org.camunda.optimize.dto.optimize.query.report.single.decision.result.DecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.LimitedResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;

import java.util.List;

public class RawDataDecisionReportResultDto extends DecisionReportResultDto implements LimitedResultDto {

  protected List<RawDataDecisionInstanceDto> data;
  private Boolean isComplete = true;

  @Override
  public Boolean getIsComplete() {
    return isComplete;
  }

  public void setComplete(final Boolean complete) {
    isComplete = complete;
  }

  public List<RawDataDecisionInstanceDto> getData() {
    return data;
  }

  public void setData(List<RawDataDecisionInstanceDto> data) {
    this.data = data;
  }

  @Override
  public ResultType getType() {
    return ResultType.RAW;
  }

}
