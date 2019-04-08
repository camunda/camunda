/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.exceptions;


public class OptimizeRuntimeException extends RuntimeException {
  public OptimizeRuntimeException () {
    super();
  }

  public OptimizeRuntimeException(String message) {
    super(message);
  }

  public OptimizeRuntimeException(String message, Exception e) {
    super(message, e);
  }
}
