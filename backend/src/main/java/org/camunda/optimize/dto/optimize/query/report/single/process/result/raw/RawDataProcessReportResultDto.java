/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.result.raw;

import lombok.Data;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.LimitedResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;

import java.util.List;

@Data
public class RawDataProcessReportResultDto extends ProcessReportResultDto implements LimitedResultDto {

  protected List<RawDataProcessInstanceDto> data;
  private Boolean isComplete = true;

  @Override
  public ResultType getType() {
    return ResultType.RAW;
  }

}
