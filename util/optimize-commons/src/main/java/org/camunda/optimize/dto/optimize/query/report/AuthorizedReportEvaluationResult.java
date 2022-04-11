/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.camunda.optimize.dto.optimize.AuthorizedEntityDto;
import org.camunda.optimize.dto.optimize.RoleType;

@Data
@EqualsAndHashCode(callSuper=true)
public class AuthorizedReportEvaluationResult extends AuthorizedEntityDto {
  private ReportEvaluationResult evaluationResult;

  public AuthorizedReportEvaluationResult(final ReportEvaluationResult evaluationResult, final RoleType currentUserRole) {
    super(currentUserRole);
    this.evaluationResult = evaluationResult;
  }
}
