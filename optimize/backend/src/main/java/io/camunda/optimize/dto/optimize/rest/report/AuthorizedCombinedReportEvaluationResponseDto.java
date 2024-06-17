/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.report;

import io.camunda.optimize.dto.optimize.RoleType;
import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthorizedCombinedReportEvaluationResponseDto<T>
    extends AuthorizedReportEvaluationResponseDto<CombinedReportDefinitionRequestDto> {

  protected CombinedProcessReportResultDataDto<T> result;

  public AuthorizedCombinedReportEvaluationResponseDto(
      final RoleType currentUserRole,
      final CombinedReportDefinitionRequestDto reportDefinition,
      final CombinedProcessReportResultDataDto<T> result) {
    super(currentUserRole, reportDefinition);
    this.result = result;
  }
}
