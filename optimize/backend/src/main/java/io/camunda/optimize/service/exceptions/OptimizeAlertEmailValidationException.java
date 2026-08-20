/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.exceptions;

import java.util.Set;

public class OptimizeAlertEmailValidationException extends OptimizeRuntimeException {

  public static final String ERROR_CODE = "invalidAlertEmailAddresses";
  public static final String ERROR_MESSAGE =
      "Users with the following email addresses are not available for receiving alerts: ";

  private final Set<String> invalidAlertEmails;

  public OptimizeAlertEmailValidationException(final Set<String> alertEmails) {
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
