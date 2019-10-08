/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest.report;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedEntityDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportEvaluationResult;

@Getter
@Setter
@FieldNameConstants(asEnum = true)
public class AuthorizedEvaluationResultDto<Result extends ReportResultDto, ReportDefinition extends ReportDefinitionDto>
  extends AuthorizedEntityDto {

  protected Result result;
  @JsonUnwrapped
  protected ReportDefinition reportDefinition;

  public static AuthorizedEvaluationResultDto<?, ?> from(final AuthorizedReportEvaluationResult reportEvaluationResult) {
    return new AuthorizedEvaluationResultDto<>(
      reportEvaluationResult.getCurrentUserRole(),
      reportEvaluationResult.getEvaluationResult().getResultAsDto(),
      reportEvaluationResult.getEvaluationResult().getReportDefinition()
    );
  }

  protected AuthorizedEvaluationResultDto() {
  }

  public AuthorizedEvaluationResultDto(final RoleType currentUserRole, final Result result,
                                       final ReportDefinition reportDefinition) {
    super(currentUserRole);
    this.result = result;
    this.reportDefinition = reportDefinition;
  }
}
