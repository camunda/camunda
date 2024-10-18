/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.report;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.camunda.optimize.dto.optimize.AuthorizedEntityDto;
import io.camunda.optimize.dto.optimize.RoleType;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;

public class AuthorizedReportEvaluationResponseDto<D extends ReportDefinitionDto<?>>
    extends AuthorizedEntityDto {

  @JsonUnwrapped protected D reportDefinition;

  public AuthorizedReportEvaluationResponseDto(
      final RoleType currentUserRole, final D reportDefinition) {
    super(currentUserRole);
    this.reportDefinition = reportDefinition;
  }

  protected AuthorizedReportEvaluationResponseDto() {}

  public D getReportDefinition() {
    return reportDefinition;
  }

  @JsonUnwrapped
  public void setReportDefinition(final D reportDefinition) {
    this.reportDefinition = reportDefinition;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof AuthorizedReportEvaluationResponseDto;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "AuthorizedReportEvaluationResponseDto(reportDefinition=" + getReportDefinition() + ")";
  }
}
