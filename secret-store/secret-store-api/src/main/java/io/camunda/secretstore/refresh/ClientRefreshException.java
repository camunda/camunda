/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.refresh;

import io.camunda.secretstore.refresh.ClientCheckException.Outcome;
import org.jspecify.annotations.Nullable;

/** A client couldn't move to new values, and keeps using its current ones. */
public class ClientRefreshException extends RuntimeException {

  private final Outcome outcome;

  public ClientRefreshException(final String message, final @Nullable Throwable cause) {
    super(message, cause);
    outcome = cause instanceof final ClientCheckException check ? check.outcome() : Outcome.FAILED;
  }

  /** Whether the service rejected the new credentials, couldn't be reached, or neither. */
  public Outcome outcome() {
    return outcome;
  }
}
