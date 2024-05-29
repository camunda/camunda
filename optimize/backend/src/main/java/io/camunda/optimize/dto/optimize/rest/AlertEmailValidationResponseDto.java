/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.dto.optimize.rest;

import io.camunda.optimize.service.exceptions.OptimizeAlertEmailValidationException;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;

@Data
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants
public class AlertEmailValidationResponseDto extends ErrorResponseDto {
  private final String invalidAlertEmails;

  public AlertEmailValidationResponseDto(
      final OptimizeAlertEmailValidationException optimizeAlertEmailValidationException) {
    super(
        optimizeAlertEmailValidationException.getErrorCode(),
        optimizeAlertEmailValidationException.getMessage(),
        optimizeAlertEmailValidationException.getMessage());
    this.invalidAlertEmails =
        String.join(", ", optimizeAlertEmailValidationException.getAlertEmails());
  }
}
