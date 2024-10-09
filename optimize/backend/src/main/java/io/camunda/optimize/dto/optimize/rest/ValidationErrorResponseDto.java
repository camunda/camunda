/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ValidationErrorResponseDto extends ErrorResponseDto {

  private List<ValidationError> validationErrors;

  public ValidationErrorResponseDto(
      final String errorMessage, final List<ValidationError> validationErrors) {
    super(errorMessage);
    this.validationErrors = validationErrors;
  }

  protected ValidationErrorResponseDto() {}

  public List<ValidationError> getValidationErrors() {
    return validationErrors;
  }

  public void setValidationErrors(final List<ValidationError> validationErrors) {
    this.validationErrors = validationErrors;
  }

  @Override
  public String toString() {
    return "ValidationErrorResponseDto(validationErrors=" + getValidationErrors() + ")";
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ValidationErrorResponseDto)) {
      return false;
    }
    final ValidationErrorResponseDto other = (ValidationErrorResponseDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final Object this$validationErrors = getValidationErrors();
    final Object other$validationErrors = other.getValidationErrors();
    if (this$validationErrors == null
        ? other$validationErrors != null
        : !this$validationErrors.equals(other$validationErrors)) {
      return false;
    }
    return true;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof ValidationErrorResponseDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $validationErrors = getValidationErrors();
    result = result * PRIME + ($validationErrors == null ? 43 : $validationErrors.hashCode());
    return result;
  }

  public static class ValidationError {

    private String property;
    private String errorMessage;

    public ValidationError(final String property, final String errorMessage) {
      this.property = property;
      this.errorMessage = errorMessage;
    }

    protected ValidationError() {}

    public String getProperty() {
      return property;
    }

    public void setProperty(final String property) {
      this.property = property;
    }

    public String getErrorMessage() {
      return errorMessage;
    }

    public void setErrorMessage(final String errorMessage) {
      this.errorMessage = errorMessage;
    }

    protected boolean canEqual(final Object other) {
      return other instanceof ValidationError;
    }

    @Override
    public int hashCode() {
      final int PRIME = 59;
      int result = 1;
      final Object $property = getProperty();
      result = result * PRIME + ($property == null ? 43 : $property.hashCode());
      final Object $errorMessage = getErrorMessage();
      result = result * PRIME + ($errorMessage == null ? 43 : $errorMessage.hashCode());
      return result;
    }

    @Override
    public boolean equals(final Object o) {
      if (o == this) {
        return true;
      }
      if (!(o instanceof ValidationError)) {
        return false;
      }
      final ValidationError other = (ValidationError) o;
      if (!other.canEqual((Object) this)) {
        return false;
      }
      final Object this$property = getProperty();
      final Object other$property = other.getProperty();
      if (this$property == null ? other$property != null : !this$property.equals(other$property)) {
        return false;
      }
      final Object this$errorMessage = getErrorMessage();
      final Object other$errorMessage = other.getErrorMessage();
      if (this$errorMessage == null
          ? other$errorMessage != null
          : !this$errorMessage.equals(other$errorMessage)) {
        return false;
      }
      return true;
    }

    @Override
    public String toString() {
      return "ValidationErrorResponseDto.ValidationError(property="
          + getProperty()
          + ", errorMessage="
          + getErrorMessage()
          + ")";
    }
  }
}
