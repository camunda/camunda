/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.health;

import java.util.Objects;

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

  public String getMessage() {
    return message;
  }

  public Throwable getThrowable() {
    return throwable;
  }

  public HealthReport getCause() {
    return cause;
  }

  @Override
  public int hashCode() {
    int result = message != null ? message.hashCode() : 0;
    result = 31 * result + (throwable != null ? throwable.hashCode() : 0);
    result = 31 * result + (cause != null ? cause.hashCode() : 0);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final HealthIssue that = (HealthIssue) o;

    if (!Objects.equals(message, that.message)) {
      return false;
    }
    if (!Objects.equals(throwable, that.throwable)) {
      return false;
    }
    return Objects.equals(cause, that.cause);
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
}
