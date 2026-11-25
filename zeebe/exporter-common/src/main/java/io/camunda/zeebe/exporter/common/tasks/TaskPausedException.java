/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.tasks;

/**
 * Signals that a background task stopped because exporting is paused. It is a control signal, not a
 * failure, so it carries no stack trace and {@link ReschedulingTask} does not log it as an error.
 */
public final class TaskPausedException extends RuntimeException {

  public TaskPausedException(final String message) {
    super(message, null, false, false);
  }

  /** Returns true if the error, or any cause in its chain, is a {@link TaskPausedException}. */
  public static boolean isCauseOf(final Throwable error) {
    for (Throwable current = error; current != null; current = current.getCause()) {
      if (current instanceof TaskPausedException) {
        return true;
      }
    }
    return false;
  }
}
