/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.health;

/**
 * A health issue contains information about the cause for unhealthy/dead components. It can either
 * be a string message, a {@link Throwable} or another {@link HealthReport}.
 */
public final class HealthIssue {

  private final String message;
  private final Throwable throwable;
  private final HealthReport cause;

  private HealthIssue(final String message, final Throwable throwable, final HealthReport cause) {
    this.message = message;
    this.throwable = throwable;
    this.cause = cause;
  }

  public static HealthIssue of(final String message) {
    return new HealthIssue(message, null, null);
  }

  public static HealthIssue of(final Throwable throwable) {
    return new HealthIssue(null, throwable, null);
  }

  public static HealthIssue of(final HealthReport cause) {
    return new HealthIssue(null, null, cause);
  }

  @Override
  public String toString() {
    if (cause != null) {
      return cause.toString();
    }
    if (message != null) {
      return "'" + message + "'";
    }
    if (throwable != null) {
      return throwable.toString();
    }
    return "unknown";
  }

  public String getMessage() {
    return message;
  }

  public Throwable getThrowable() {
    return throwable;
  }

  public HealthReport getCause() {
    return cause;
  }
}
