/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.db;

import io.zeebe.util.exception.RecoverableException;

/** Wraps the exceptions which are thrown by the database implementation. */
public class ZeebeDbException extends RecoverableException {

  public ZeebeDbException(Throwable cause) {
    super(cause);
  }

  public ZeebeDbException(String message, Throwable cause) {
    super(message, cause);
  }
}
