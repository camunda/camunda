/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.exception;

/**
 * Should be use to indicate an unexpected exception during execution. This exception extends {@link
 * RuntimeException} to be unchecked.
 */
public final class UncheckedExecutionException extends RuntimeException {

  public UncheckedExecutionException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
