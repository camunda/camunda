/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest.report;

import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.SingleReportResultDto;

public class AuthorizedProcessReportEvaluationResultDto<T extends SingleReportResultDto>
  extends AuthorizedEvaluationResultDto<T, SingleProcessReportDefinitionRequestDto> {

  public AuthorizedProcessReportEvaluationResultDto() {
  }

  public AuthorizedProcessReportEvaluationResultDto(final T reportResult,
                                                    final SingleProcessReportDefinitionRequestDto reportDefinition) {
    this(null, reportResult, reportDefinition);
  }

  public AuthorizedProcessReportEvaluationResultDto(final RoleType currentUserRole,
                                                    final T reportResult,
                                                    final SingleProcessReportDefinitionRequestDto reportDefinition) {
    super(currentUserRole, reportResult, reportDefinition);
  }
}
