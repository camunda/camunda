/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.rest.report;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthorizedCombinedReportEvaluationResponseDto<T>
  extends AuthorizedReportEvaluationResponseDto<CombinedReportDefinitionRequestDto> {

  protected CombinedProcessReportResultDataDto<T> result;

  public AuthorizedCombinedReportEvaluationResponseDto(final RoleType currentUserRole,
                                                       final CombinedReportDefinitionRequestDto reportDefinition,
                                                       final CombinedProcessReportResultDataDto<T> result) {
    super(currentUserRole, reportDefinition);
    this.result = result;
  }
}
