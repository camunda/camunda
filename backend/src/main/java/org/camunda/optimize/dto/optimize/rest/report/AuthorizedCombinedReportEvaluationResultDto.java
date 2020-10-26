/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest.report;

import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.report.SingleReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;

public class AuthorizedCombinedReportEvaluationResultDto<T extends SingleReportResultDto>
  extends AuthorizedEvaluationResultDto<CombinedProcessReportResultDataDto<T>, CombinedReportDefinitionRequestDto> {

  public AuthorizedCombinedReportEvaluationResultDto() {
  }

  public AuthorizedCombinedReportEvaluationResultDto(final RoleType currentUserRole,
                                                     final CombinedProcessReportResultDataDto<T> reportResult,
                                                     final CombinedReportDefinitionRequestDto reportDefinition) {
    super(currentUserRole, reportResult, reportDefinition);
  }
}
