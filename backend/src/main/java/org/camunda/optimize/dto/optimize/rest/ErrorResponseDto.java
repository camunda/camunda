package org.camunda.optimize.dto.optimize.rest;

public class ErrorResponseDto {
  private String errorMessage;

  public ErrorResponseDto() {
  }

  public ErrorResponseDto(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public String getErrorMessage() {
    return errorMessage;
  }
}
