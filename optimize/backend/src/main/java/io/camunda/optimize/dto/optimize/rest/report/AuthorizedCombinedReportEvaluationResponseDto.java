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
import java.util.Objects;

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

  protected AuthorizedCombinedReportEvaluationResponseDto() {}

  public CombinedProcessReportResultDataDto<T> getResult() {
    return result;
  }

  public void setResult(final CombinedProcessReportResultDataDto<T> result) {
    this.result = result;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof AuthorizedCombinedReportEvaluationResponseDto;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), result);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final AuthorizedCombinedReportEvaluationResponseDto<?> that =
        (AuthorizedCombinedReportEvaluationResponseDto<?>) o;
    return Objects.equals(result, that.result);
  }

  @Override
  public String toString() {
    return "AuthorizedCombinedReportEvaluationResponseDto(result=" + getResult() + ")";
  }
}
