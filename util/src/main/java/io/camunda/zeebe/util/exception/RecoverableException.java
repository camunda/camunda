/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.exception;

/**
 * A recoverable exception should wrap any exception, where it makes sense to apply any retry
 * strategy.
 */
public class RecoverableException extends RuntimeException {

  public RecoverableException(final String message) {
    super(message);
  }

  public RecoverableException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public RecoverableException(final Throwable cause) {
    super(cause);
  }
}
