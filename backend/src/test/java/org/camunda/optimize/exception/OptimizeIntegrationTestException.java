/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.exception;

public class OptimizeIntegrationTestException extends RuntimeException {

  public OptimizeIntegrationTestException() {
    super();
  }

  public OptimizeIntegrationTestException(Exception e) {
    super(e);
  }

  public OptimizeIntegrationTestException(String message) {
    super(message);
  }

  public OptimizeIntegrationTestException(String message, Exception e) {
    super(message, e);
  }
}
