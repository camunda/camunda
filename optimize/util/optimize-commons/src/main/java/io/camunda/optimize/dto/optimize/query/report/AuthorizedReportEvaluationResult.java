/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report;

import io.camunda.optimize.dto.optimize.AuthorizedEntityDto;
import io.camunda.optimize.dto.optimize.RoleType;

public class AuthorizedReportEvaluationResult extends AuthorizedEntityDto {

  private ReportEvaluationResult evaluationResult;

  public AuthorizedReportEvaluationResult(
      final ReportEvaluationResult evaluationResult, final RoleType currentUserRole) {
    super(currentUserRole);
    this.evaluationResult = evaluationResult;
  }

  public ReportEvaluationResult getEvaluationResult() {
    return evaluationResult;
  }

  public void setEvaluationResult(final ReportEvaluationResult evaluationResult) {
    this.evaluationResult = evaluationResult;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof AuthorizedReportEvaluationResult;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $evaluationResult = getEvaluationResult();
    result = result * PRIME + ($evaluationResult == null ? 43 : $evaluationResult.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof AuthorizedReportEvaluationResult)) {
      return false;
    }
    final AuthorizedReportEvaluationResult other = (AuthorizedReportEvaluationResult) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final Object this$evaluationResult = getEvaluationResult();
    final Object other$evaluationResult = other.getEvaluationResult();
    if (this$evaluationResult == null
        ? other$evaluationResult != null
        : !this$evaluationResult.equals(other$evaluationResult)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "AuthorizedReportEvaluationResult(evaluationResult=" + getEvaluationResult() + ")";
  }
}
