/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.exceptions.evaluation;

import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.util.SuppressionConstants;

public class ReportEvaluationException extends OptimizeRuntimeException {

  protected AuthorizedReportDefinitionResponseDto reportDefinition;

  @SuppressWarnings(SuppressionConstants.UNUSED)
  public ReportEvaluationException() {
    super();
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
