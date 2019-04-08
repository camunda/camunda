/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;

public class ErrorResponseDto {
  private String errorMessage;
  private ReportDefinitionDto reportDefinition;

  public ErrorResponseDto() {
  }

  public ErrorResponseDto(String errorMessage, ReportDefinitionDto reportDefinition) {
    this.errorMessage = errorMessage;
    this.reportDefinition = reportDefinition;
  }

  public ErrorResponseDto(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public ReportDefinitionDto getReportDefinition() {
    return reportDefinition;
  }

  public void setReportDefinition(ReportDefinitionDto reportDefinition) {
    this.reportDefinition = reportDefinition;
  }
}
