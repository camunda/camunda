/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import lombok.Data;

@Data
public class ErrorResponseDto {

  private String errorCode;
  private String errorMessage;
  private String detailedMessage;
  private AuthorizedReportDefinitionResponseDto reportDefinition;

  public ErrorResponseDto() {}

  public ErrorResponseDto(
      final String errorCode,
      final String errorMessage,
      final String detailedMessage,
      final AuthorizedReportDefinitionResponseDto reportDefinition) {
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
    this.detailedMessage = detailedMessage;
    this.reportDefinition = reportDefinition;
  }

  public ErrorResponseDto(final String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public ErrorResponseDto(final String errorCode, final String errorMessage) {
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
  }

  public ErrorResponseDto(
      final String errorCode, final String errorMessage, final String detailedMessage) {
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
    this.detailedMessage = detailedMessage;
  }

  public static final class Fields {

    public static final String errorCode = "errorCode";
    public static final String errorMessage = "errorMessage";
    public static final String detailedMessage = "detailedMessage";
    public static final String reportDefinition = "reportDefinition";
  }
}
