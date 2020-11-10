/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.exceptions.evaluation;

import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;

public class ReportEvaluationException extends OptimizeRuntimeException {

  protected AuthorizedReportDefinitionResponseDto reportDefinition;

  public ReportEvaluationException() {
    super();
  }

  public ReportEvaluationException(String message) {
    super(message);
  }

  public ReportEvaluationException(String message, Exception e) {
    super(message, e);
  }

  public ReportEvaluationException(AuthorizedReportDefinitionResponseDto reportDefinition, Exception e) {
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
