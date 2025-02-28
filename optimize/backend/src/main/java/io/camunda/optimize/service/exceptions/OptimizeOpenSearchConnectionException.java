/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.exceptions;

public class OptimizeOpenSearchConnectionException extends OptimizeRuntimeException {
  public static final String ERROR_CODE = "openSearchConnectionError";

  public OptimizeOpenSearchConnectionException(final String message, final Throwable e) {
    super(message, e);
  }

  @Override
  public String getErrorCode() {
    return ERROR_CODE;
  }
}
