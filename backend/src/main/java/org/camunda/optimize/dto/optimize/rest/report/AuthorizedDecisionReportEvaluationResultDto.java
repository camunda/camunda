/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest.report;

import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.report.SingleReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;

@NoArgsConstructor
public class AuthorizedDecisionReportEvaluationResultDto<T extends SingleReportResultDto>
  extends AuthorizedEvaluationResultDto<T, SingleDecisionReportDefinitionRequestDto> {

  public AuthorizedDecisionReportEvaluationResultDto(final RoleType currentUserRole,
                                                     final T reportResult,
                                                     final SingleDecisionReportDefinitionRequestDto reportDefinition) {
    super(currentUserRole, reportResult, reportDefinition);
  }
}
