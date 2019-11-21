/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.exceptions;

public class OptimizeException extends Exception {

  public OptimizeException() {
    super();
  }

  public OptimizeException(String detailedErrorMessage) {
    super(detailedErrorMessage);
  }

  public OptimizeException(String detailedErrorMessage, Exception e) {
    super(detailedErrorMessage, e);
  }

  public String getErrorCode() {
    return "serverError";
  }
}
