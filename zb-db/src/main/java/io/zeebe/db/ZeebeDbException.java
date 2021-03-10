/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.db;

import io.zeebe.util.exception.RecoverableException;

/** Wraps the exceptions which are thrown by the database implementation. */
public final class ZeebeDbException extends RecoverableException {

  public ZeebeDbException(final Throwable cause) {
    super(cause);
  }

  public ZeebeDbException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
