/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.result.duration;

import lombok.Getter;
import lombok.Setter;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;

public class ProcessDurationReportNumberResultDto extends ProcessReportResultDto {

  @Getter @Setter private long data;

  @Override
  public ResultType getType() {
    return ResultType.DURATION_NUMBER;
  }
}
