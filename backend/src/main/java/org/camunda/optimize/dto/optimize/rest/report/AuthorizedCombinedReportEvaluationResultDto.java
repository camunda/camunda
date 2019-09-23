/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest.report;

import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;

public class AuthorizedCombinedReportEvaluationResultDto<T extends ProcessReportResultDto>
  extends AuthorizedEvaluationResultDto<CombinedProcessReportResultDataDto<T>, CombinedReportDefinitionDto> {

  public AuthorizedCombinedReportEvaluationResultDto() {
  }

  public AuthorizedCombinedReportEvaluationResultDto(final RoleType currentUserRole,
                                                     final CombinedProcessReportResultDataDto<T> reportResult,
                                                     final CombinedReportDefinitionDto reportDefinition) {
    super(currentUserRole, reportResult, reportDefinition);
  }
}
