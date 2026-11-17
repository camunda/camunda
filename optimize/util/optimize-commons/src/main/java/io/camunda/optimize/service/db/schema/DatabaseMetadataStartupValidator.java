/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema;

import io.camunda.optimize.service.exceptions.OptimizeMetadataReadException;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.BackoffCalculator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;

/**
 * Runs the startup validation of the Optimize metadata document and retries it with backoff while
 * the document cannot be read, e.g. because the database is still recovering the metadata index
 * shard after a restart. Any other failure, such as a schema version mismatch, is not retried.
 */
public final class DatabaseMetadataStartupValidator {

  static final Duration DEFAULT_MAX_WAIT = Duration.ofSeconds(600);

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(DatabaseMetadataStartupValidator.class);

  private final BackoffCalculator backoffCalculator;
  private final Duration maxWait;
  private final Clock clock;
  private final Sleeper sleeper;

  public DatabaseMetadataStartupValidator(
      final BackoffCalculator backoffCalculator, final Integer maxWaitInSeconds) {
    this(backoffCalculator, resolveMaxWait(maxWaitInSeconds), Clock.systemUTC(), Thread::sleep);
  }

  DatabaseMetadataStartupValidator(
      final BackoffCalculator backoffCalculator,
      final Duration maxWait,
      final Clock clock,
      final Sleeper sleeper) {
    this.backoffCalculator = backoffCalculator;
    this.maxWait = maxWait;
    this.clock = clock;
    this.sleeper = sleeper;
  }

  static Duration resolveMaxWait(final Integer maxWaitInSeconds) {
    if (maxWaitInSeconds == null) {
      LOG.warn(
          "No max wait configured for reading the Optimize metadata document, using the default of [{}].",
          DEFAULT_MAX_WAIT);
      return DEFAULT_MAX_WAIT;
    }
    return Duration.ofSeconds(Math.max(0, maxWaitInSeconds));
  }

  public void validate(final Runnable metadataValidation) {
    backoffCalculator.resetBackoff();
    final Instant deadline = clock.instant().plus(maxWait);
    int attempt = 0;
    while (true) {
      attempt++;
      try {
        metadataValidation.run();
        return;
      } catch (final OptimizeMetadataReadException e) {
        final long remainingMillis = Duration.between(clock.instant(), deadline).toMillis();
        if (remainingMillis <= 0) {
          throw new OptimizeRuntimeException(
              String.format(
                  "Could not read the Optimize metadata document after [%d] attempt(s) within [%s].",
                  attempt, maxWait),
              e);
        }
        final long sleepMillis = Math.min(backoffCalculator.calculateSleepTime(), remainingMillis);
        LOG.warn(
            "Could not read the Optimize metadata document (attempt [{}]), retrying in [{}] ms. Cause: {}",
            attempt,
            sleepMillis,
            rootCauseMessage(e));
        sleep(sleepMillis, e);
      }
    }
  }

  private void sleep(final long millis, final OptimizeMetadataReadException lastFailure) {
    try {
      sleeper.sleep(millis);
    } catch (final InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      final OptimizeRuntimeException exception =
          new OptimizeRuntimeException(
              "Interrupted while waiting to retry reading the Optimize metadata document.",
              lastFailure);
      exception.addSuppressed(interrupted);
      throw exception;
    }
  }

  private static String rootCauseMessage(final Throwable throwable) {
    Throwable cause = throwable;
    while (cause.getCause() != null && cause.getCause() != cause) {
      cause = cause.getCause();
    }
    return cause.getMessage();
  }

  @FunctionalInterface
  interface Sleeper {
    void sleep(long millis) throws InterruptedException;
  }
}
