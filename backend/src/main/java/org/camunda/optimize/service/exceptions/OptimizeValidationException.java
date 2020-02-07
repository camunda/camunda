/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.exceptions;


public class OptimizeValidationException extends OptimizeRuntimeException {

  public static final String ERROR_CODE = "badRequestError";

  public OptimizeValidationException(String message) {
    super(message);
  }

  @Override
  public String getErrorCode() {
    return ERROR_CODE;
  }
}
