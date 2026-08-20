/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.exceptions;

public class OptimizeRuntimeException extends RuntimeException {

  public OptimizeRuntimeException() {
    super();
  }

  public OptimizeRuntimeException(final Throwable e) {
    super(e);
  }

  public OptimizeRuntimeException(final String detailedErrorMessage) {
    super(detailedErrorMessage);
  }

  public OptimizeRuntimeException(final String detailedErrorMessage, final Throwable e) {
    super(detailedErrorMessage, e);
  }

  public String getErrorCode() {
    return "serverError";
  }
}
