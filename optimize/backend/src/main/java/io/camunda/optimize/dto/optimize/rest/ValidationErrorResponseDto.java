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
import java.util.Objects;

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
  protected boolean canEqual(final Object other) {
    return other instanceof ValidationErrorResponseDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final ValidationErrorResponseDto that = (ValidationErrorResponseDto) o;
    return Objects.equals(validationErrors, that.validationErrors);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), validationErrors);
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
      return Objects.hash(property, errorMessage);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final ValidationError that = (ValidationError) o;
      return Objects.equals(property, that.property)
          && Objects.equals(errorMessage, that.errorMessage);
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
