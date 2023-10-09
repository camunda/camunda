/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.exceptions;

import java.util.Set;

public class OptimizeAlertEmailValidationException extends OptimizeRuntimeException {

  public static final String ERROR_CODE = "invalidAlertEmailAddresses";
  public static final String ERROR_MESSAGE = "Users with the following email addresses are not available for receiving alerts: ";

  private final Set<String> invalidAlertEmails;

  public OptimizeAlertEmailValidationException(Set<String> alertEmails) {
    super(ERROR_MESSAGE + alertEmails);
    this.invalidAlertEmails = alertEmails;
  }

  public Set<String> getAlertEmails() {
    return invalidAlertEmails;
  }

  @Override
  public String getErrorCode() {
    return ERROR_CODE;
  }
}
