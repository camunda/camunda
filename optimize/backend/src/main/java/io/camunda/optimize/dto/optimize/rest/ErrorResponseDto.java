/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import java.util.Objects;

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

  public String getErrorCode() {
    return errorCode;
  }

  public void setErrorCode(final String errorCode) {
    this.errorCode = errorCode;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(final String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public String getDetailedMessage() {
    return detailedMessage;
  }

  public void setDetailedMessage(final String detailedMessage) {
    this.detailedMessage = detailedMessage;
  }

  public AuthorizedReportDefinitionResponseDto getReportDefinition() {
    return reportDefinition;
  }

  public void setReportDefinition(final AuthorizedReportDefinitionResponseDto reportDefinition) {
    this.reportDefinition = reportDefinition;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ErrorResponseDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ErrorResponseDto that = (ErrorResponseDto) o;
    return Objects.equals(errorCode, that.errorCode)
        && Objects.equals(errorMessage, that.errorMessage)
        && Objects.equals(detailedMessage, that.detailedMessage)
        && Objects.equals(reportDefinition, that.reportDefinition);
  }

  @Override
  public int hashCode() {
    return Objects.hash(errorCode, errorMessage, detailedMessage, reportDefinition);
  }

  @Override
  public String toString() {
    return "ErrorResponseDto(errorCode="
        + getErrorCode()
        + ", errorMessage="
        + getErrorMessage()
        + ", detailedMessage="
        + getDetailedMessage()
        + ", reportDefinition="
        + getReportDefinition()
        + ")";
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String errorCode = "errorCode";
    public static final String errorMessage = "errorMessage";
    public static final String detailedMessage = "detailedMessage";
    public static final String reportDefinition = "reportDefinition";
  }
}
