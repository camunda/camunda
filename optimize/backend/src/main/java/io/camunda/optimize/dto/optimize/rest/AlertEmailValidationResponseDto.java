/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import io.camunda.optimize.service.exceptions.OptimizeAlertEmailValidationException;
import java.util.Objects;

public class AlertEmailValidationResponseDto extends ErrorResponseDto {

  private final String invalidAlertEmails;

  public AlertEmailValidationResponseDto(
      final OptimizeAlertEmailValidationException optimizeAlertEmailValidationException) {
    super(
        optimizeAlertEmailValidationException.getErrorCode(),
        optimizeAlertEmailValidationException.getMessage(),
        optimizeAlertEmailValidationException.getMessage());
    invalidAlertEmails = String.join(", ", optimizeAlertEmailValidationException.getAlertEmails());
  }

  public String getInvalidAlertEmails() {
    return invalidAlertEmails;
  }

  @Override
  public String toString() {
    return "AlertEmailValidationResponseDto(invalidAlertEmails=" + getInvalidAlertEmails() + ")";
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final AlertEmailValidationResponseDto that = (AlertEmailValidationResponseDto) o;
    return Objects.equals(invalidAlertEmails, that.invalidAlertEmails);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), invalidAlertEmails);
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof AlertEmailValidationResponseDto;
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String invalidAlertEmails = "invalidAlertEmails";
  }
}
