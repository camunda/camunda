/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.report;

import io.camunda.optimize.dto.optimize.RoleType;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AuthorizedSingleReportEvaluationResponseDto<T, D extends ReportDefinitionDto<?>>
    extends AuthorizedReportEvaluationResponseDto<D> {

  protected ReportResultResponseDto<T> result;

  public AuthorizedSingleReportEvaluationResponseDto(
      final RoleType currentUserRole,
      final ReportResultResponseDto<T> result,
      final D reportDefinition) {
    super(currentUserRole, reportDefinition);
    this.result = result;
  }

  protected AuthorizedSingleReportEvaluationResponseDto() {}

  public enum Fields {
    result
  }
}
