/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.refresh;

import org.jspecify.annotations.Nullable;

/**
 * Thrown by a client's check when it can tell why the new client can't be used: the service
 * rejected its credentials, or couldn't be reached.
 */
public final class ClientCheckException extends Exception {

  private final Outcome outcome;

  private ClientCheckException(
      final Outcome outcome, final String message, final @Nullable Throwable cause) {
    super(message, cause);
    this.outcome = outcome;
  }

  public static ClientCheckException authenticationFailed(
      final String message, final @Nullable Throwable cause) {
    return new ClientCheckException(Outcome.AUTHENTICATION_FAILED, message, cause);
  }

  public static ClientCheckException unreachable(
      final String message, final @Nullable Throwable cause) {
    return new ClientCheckException(Outcome.UNREACHABLE, message, cause);
  }

  public Outcome outcome() {
    return outcome;
  }

  /** Why a client couldn't take new values. */
  public enum Outcome {
    /** The service rejected the new credentials. */
    AUTHENTICATION_FAILED,
    /** The service couldn't be reached. */
    UNREACHABLE,
    /** The build or the check failed for another reason, or didn't say why. */
    FAILED
  }
}
