/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.dto.optimize.rest.report;

import io.camunda.optimize.dto.optimize.RoleType;
import io.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class AuthorizedDecisionReportEvaluationResponseDto<T>
    extends AuthorizedSingleReportEvaluationResponseDto<
        T, SingleDecisionReportDefinitionRequestDto> {

  public AuthorizedDecisionReportEvaluationResponseDto(
      final RoleType currentUserRole,
      final ReportResultResponseDto<T> reportResult,
      final SingleDecisionReportDefinitionRequestDto reportDefinition) {
    super(currentUserRole, reportResult, reportDefinition);
  }
}
