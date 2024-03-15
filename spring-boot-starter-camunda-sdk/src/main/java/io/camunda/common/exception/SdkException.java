/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.common.exception;

public class SdkException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public SdkException(final Throwable cause) {
    super(cause);
  }

  public SdkException(final String message) {
    super(message);
  }

  public SdkException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
