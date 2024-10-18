/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import io.camunda.optimize.service.exceptions.OptimizeAlertEmailValidationException;

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
    if (o == this) {
      return true;
    }
    if (!(o instanceof AlertEmailValidationResponseDto)) {
      return false;
    }
    final AlertEmailValidationResponseDto other = (AlertEmailValidationResponseDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final Object this$invalidAlertEmails = getInvalidAlertEmails();
    final Object other$invalidAlertEmails = other.getInvalidAlertEmails();
    if (this$invalidAlertEmails == null
        ? other$invalidAlertEmails != null
        : !this$invalidAlertEmails.equals(other$invalidAlertEmails)) {
      return false;
    }
    return true;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof AlertEmailValidationResponseDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $invalidAlertEmails = getInvalidAlertEmails();
    result = result * PRIME + ($invalidAlertEmails == null ? 43 : $invalidAlertEmails.hashCode());
    return result;
  }

  public static final class Fields {

    public static final String invalidAlertEmails = "invalidAlertEmails";
  }
}
