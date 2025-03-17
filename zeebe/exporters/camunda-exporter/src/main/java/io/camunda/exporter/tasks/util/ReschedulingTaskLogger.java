/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.util;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.event.Level;

/**
 * To avoid log pollution with similar error messages this logger logs on ERROR level every 10th
 * occurrence and all others on DEBUG level. Additionally, for some exception classes like
 * `ConnectException` it logs them on WARN level.
 */
public class ReschedulingTaskLogger {
  private static final Integer DEFAULT_SKIP_ENTRIES_COUNT = 10;
  private static final Set<Class<? extends Exception>> BACKGROUND_SUPPRESSED_EXCEPTIONS =
      Set.of(SocketTimeoutException.class, ConnectException.class, SocketException.class);
  private Integer skipEntriesCount = DEFAULT_SKIP_ENTRIES_COUNT;
  private final Logger logger;
  private Integer failureCount = 0;
  private String lastErrorMessage;
  private Object[] lastErrorMessageArgs;

  public ReschedulingTaskLogger(final Logger logger) {
    this.logger = logger;
  }

  public ReschedulingTaskLogger(final Integer skipEntriesCount, final Logger logger) {
    this.skipEntriesCount = skipEntriesCount;
    this.logger = logger;
  }

  public void logError(final String errorMessage, final Throwable error, final Object... args) {
    if (lastErrorMessage != null
        && lastErrorMessage.equals(errorMessage)
        && Arrays.equals(args, lastErrorMessageArgs)) {
      failureCount++;
    } else {
      failureCount = 1;
      lastErrorMessage = errorMessage;
      lastErrorMessageArgs = args;
    }
    // only log the error message if it's different from the last one, or if it's the same, only log
    // every n-th time
    final Level logLevel;
    if (failureCount % skipEntriesCount == 1) {
      if (shouldBeSupressed(error)) {
        logLevel = Level.WARN;
      } else {
        logLevel = Level.ERROR;
      }
    } else {
      logLevel = Level.DEBUG;
    }
    logger.atLevel(logLevel).setCause(error).log(errorMessage, args);
  }

  private boolean shouldBeSupressed(final Throwable error) {
    return BACKGROUND_SUPPRESSED_EXCEPTIONS.contains(error.getClass())
        || (error.getCause() != null
            && BACKGROUND_SUPPRESSED_EXCEPTIONS.contains(error.getCause().getClass()));
  }
}
