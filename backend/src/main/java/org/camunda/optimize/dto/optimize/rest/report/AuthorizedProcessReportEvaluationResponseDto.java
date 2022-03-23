/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.rest.report;

import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;

@NoArgsConstructor
public class AuthorizedProcessReportEvaluationResponseDto<T>
  extends AuthorizedSingleReportEvaluationResponseDto<T, SingleProcessReportDefinitionRequestDto> {

  public AuthorizedProcessReportEvaluationResponseDto(final RoleType currentUserRole,
                                                      final ReportResultResponseDto<T> reportResult,
                                                      final SingleProcessReportDefinitionRequestDto reportDefinition) {
    super(currentUserRole, reportResult, reportDefinition);
  }
}
