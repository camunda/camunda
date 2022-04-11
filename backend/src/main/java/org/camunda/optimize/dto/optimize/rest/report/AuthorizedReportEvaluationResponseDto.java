/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.rest.report;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.AuthorizedEntityDto;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Data
@EqualsAndHashCode(callSuper = true)
public class AuthorizedReportEvaluationResponseDto<D extends ReportDefinitionDto<?>> extends AuthorizedEntityDto {
  @JsonUnwrapped
  protected D reportDefinition;

  public AuthorizedReportEvaluationResponseDto(final RoleType currentUserRole, final D reportDefinition) {
    super(currentUserRole);
    this.reportDefinition = reportDefinition;
  }
}
