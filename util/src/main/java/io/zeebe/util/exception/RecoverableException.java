/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.exception;

/**
 * A recoverable exception should wrap any exception, where it makes sense to apply any retry
 * strategy.
 */
public class RecoverableException extends RuntimeException {

  public RecoverableException(String message) {
    super(message);
  }

  public RecoverableException(String message, Throwable cause) {
    super(message, cause);
  }

  public RecoverableException(Throwable cause) {
    super(cause);
  }
}
