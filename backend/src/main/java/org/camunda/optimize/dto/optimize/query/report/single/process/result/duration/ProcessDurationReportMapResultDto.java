/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.result.duration;

import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportMapResult;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;

public class ProcessDurationReportMapResultDto extends ProcessReportMapResult<Long> {

  @Override
  public ResultType getType() {
    return ResultType.DURATION_MAP;
  }

}
