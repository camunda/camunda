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
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $result = getResult();
    result = result * PRIME + ($result == null ? 43 : $result.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof AuthorizedCombinedReportEvaluationResponseDto)) {
      return false;
    }
    final AuthorizedCombinedReportEvaluationResponseDto<?> other =
        (AuthorizedCombinedReportEvaluationResponseDto<?>) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final Object this$result = getResult();
    final Object other$result = other.getResult();
    if (this$result == null ? other$result != null : !this$result.equals(other$result)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "AuthorizedCombinedReportEvaluationResponseDto(result=" + getResult() + ")";
  }
}
