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
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;

@Data
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants(asEnum = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthorizedSingleReportEvaluationResponseDto<T, D extends ReportDefinitionDto<?>>
  extends AuthorizedReportEvaluationResponseDto<D> {

  protected ReportResultResponseDto<T> result;

  public AuthorizedSingleReportEvaluationResponseDto(final RoleType currentUserRole,
                                                     final ReportResultResponseDto<T> result,
                                                     final D reportDefinition) {
    super(currentUserRole, reportDefinition);
    this.result = result;
  }

}
