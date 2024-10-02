/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.exception;

public class OptimizeIntegrationTestException extends RuntimeException {

  public OptimizeIntegrationTestException() {
    super();
  }

  public OptimizeIntegrationTestException(Throwable cause) {
    super(cause);
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