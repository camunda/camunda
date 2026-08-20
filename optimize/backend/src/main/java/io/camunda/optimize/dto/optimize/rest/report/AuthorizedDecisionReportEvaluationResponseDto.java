/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.report;

import io.camunda.optimize.dto.optimize.RoleType;
import io.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;

public class AuthorizedDecisionReportEvaluationResponseDto<T>
    extends AuthorizedSingleReportEvaluationResponseDto<
        T, SingleDecisionReportDefinitionRequestDto> {

  public AuthorizedDecisionReportEvaluationResponseDto(
      final RoleType currentUserRole,
      final ReportResultResponseDto<T> reportResult,
      final SingleDecisionReportDefinitionRequestDto reportDefinition) {
    super(currentUserRole, reportResult, reportDefinition);
  }

  public AuthorizedDecisionReportEvaluationResponseDto() {}
}
