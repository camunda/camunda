/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.rest;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

@Data
@FieldNameConstants
public class ErrorResponseDto {
  private String errorCode;
  private String errorMessage;
  private String detailedMessage;
  private AuthorizedReportDefinitionResponseDto reportDefinition;

  public ErrorResponseDto() {
  }

  public ErrorResponseDto(String errorCode, String errorMessage, String detailedMessage,
                          AuthorizedReportDefinitionResponseDto reportDefinition) {
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
    this.detailedMessage = detailedMessage;
    this.reportDefinition = reportDefinition;
  }

  public ErrorResponseDto(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public ErrorResponseDto(String errorCode, String errorMessage) {
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
  }

  public ErrorResponseDto(String errorCode, String errorMessage, String detailedMessage) {
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
    this.detailedMessage = detailedMessage;
  }
}
