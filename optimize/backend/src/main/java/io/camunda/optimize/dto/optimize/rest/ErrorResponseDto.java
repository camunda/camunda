/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

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
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $errorCode = getErrorCode();
    result = result * PRIME + ($errorCode == null ? 43 : $errorCode.hashCode());
    final Object $errorMessage = getErrorMessage();
    result = result * PRIME + ($errorMessage == null ? 43 : $errorMessage.hashCode());
    final Object $detailedMessage = getDetailedMessage();
    result = result * PRIME + ($detailedMessage == null ? 43 : $detailedMessage.hashCode());
    final Object $reportDefinition = getReportDefinition();
    result = result * PRIME + ($reportDefinition == null ? 43 : $reportDefinition.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ErrorResponseDto)) {
      return false;
    }
    final ErrorResponseDto other = (ErrorResponseDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$errorCode = getErrorCode();
    final Object other$errorCode = other.getErrorCode();
    if (this$errorCode == null
        ? other$errorCode != null
        : !this$errorCode.equals(other$errorCode)) {
      return false;
    }
    final Object this$errorMessage = getErrorMessage();
    final Object other$errorMessage = other.getErrorMessage();
    if (this$errorMessage == null
        ? other$errorMessage != null
        : !this$errorMessage.equals(other$errorMessage)) {
      return false;
    }
    final Object this$detailedMessage = getDetailedMessage();
    final Object other$detailedMessage = other.getDetailedMessage();
    if (this$detailedMessage == null
        ? other$detailedMessage != null
        : !this$detailedMessage.equals(other$detailedMessage)) {
      return false;
    }
    final Object this$reportDefinition = getReportDefinition();
    final Object other$reportDefinition = other.getReportDefinition();
    if (this$reportDefinition == null
        ? other$reportDefinition != null
        : !this$reportDefinition.equals(other$reportDefinition)) {
      return false;
    }
    return true;
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

  public static final class Fields {

    public static final String errorCode = "errorCode";
    public static final String errorMessage = "errorMessage";
    public static final String detailedMessage = "detailedMessage";
    public static final String reportDefinition = "reportDefinition";
  }
}
