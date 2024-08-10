/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.exceptions.evaluation;

import io.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.util.SuppressionConstants;

public class ReportEvaluationException extends OptimizeRuntimeException {

  protected AuthorizedReportDefinitionResponseDto reportDefinition;

  @SuppressWarnings(SuppressionConstants.UNUSED)
  public ReportEvaluationException() {
    super();
  }

  public ReportEvaluationException(
      AuthorizedReportDefinitionResponseDto reportDefinition, Exception e) {
    super(e.getMessage(), e);
    setReportDefinition(reportDefinition);
  }

  public void setReportDefinition(AuthorizedReportDefinitionResponseDto reportDefinition) {
    this.reportDefinition = reportDefinition;
  }

  public AuthorizedReportDefinitionResponseDto getReportDefinition() {
    return reportDefinition;
  }

  @Override
  public String getErrorCode() {
    return "reportEvaluationError";
  }
}
