/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest.report;

import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;

public class ProcessReportEvaluationResultDto<T extends ProcessReportResultDto>
  extends EvaluationResultDto<T, SingleProcessReportDefinitionDto> {

  public ProcessReportEvaluationResultDto() {
  }

  public ProcessReportEvaluationResultDto(final T reportResult,
                                          final SingleProcessReportDefinitionDto reportDefinition) {
    super(reportResult, reportDefinition);
  }
}
