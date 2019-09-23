/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest.report;

import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;

public class AuthorizedProcessReportEvaluationResultDto<T extends ProcessReportResultDto>
  extends AuthorizedEvaluationResultDto<T, SingleProcessReportDefinitionDto> {

  public AuthorizedProcessReportEvaluationResultDto() {
  }

  public AuthorizedProcessReportEvaluationResultDto(final T reportResult,
                                                    final SingleProcessReportDefinitionDto reportDefinition) {
    this(null, reportResult, reportDefinition);
  }

  public AuthorizedProcessReportEvaluationResultDto(final RoleType currentUserRole,
                                                    final T reportResult,
                                                    final SingleProcessReportDefinitionDto reportDefinition) {
    super(currentUserRole, reportResult, reportDefinition);
  }
}
