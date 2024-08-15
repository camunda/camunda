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

  public ReportResultResponseDto<T> getResult() {
    return result;
  }

  public void setResult(final ReportResultResponseDto<T> result) {
    this.result = result;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof AuthorizedSingleReportEvaluationResponseDto;
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
    if (!(o instanceof AuthorizedSingleReportEvaluationResponseDto)) {
      return false;
    }
    final AuthorizedSingleReportEvaluationResponseDto<?, ?> other =
        (AuthorizedSingleReportEvaluationResponseDto<?, ?>) o;
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
    return "AuthorizedSingleReportEvaluationResponseDto(result=" + getResult() + ")";
  }

  public enum Fields {
    result
  }
}
