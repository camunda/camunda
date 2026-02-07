/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.schedule;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.chrono.ChronoZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.function.Supplier;

/** Represents a schedule which can be either a CRON expression or an ISO8601 duration interval. */
public sealed interface Schedule {

  String NONE = "none";

  /*
   * Provide the next execution of the scheduler
   */
  Optional<Instant> nextExecution(Instant from);

  /*
   * Provide the next execution of the scheduler, using the provided interval supplier if needed
   */
  default Optional<Instant> nextExecution(
      final Instant from, final Supplier<Duration> intervalSupplier) {
    return nextExecution(from);
  }

  /*
   * In reality the previous execution will be acquired from the checkpoint state
   */
  Optional<Instant> previousExecution(Instant from);

  /**
   * @param expression the expression string to parse
   * @return the typed {@link Schedule}
   * @throws IllegalArgumentException if the expression is invalid
   */
  static Schedule parseSchedule(final String expression) throws IllegalArgumentException {
    if (expression == null || expression.isBlank()) {
      return none();
    }
    if (expression.equalsIgnoreCase(NONE)) {
      return none();
    }
    try {
      final var cron =
          new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.SPRING53))
              .parse(expression);
      return new CronSchedule(cron);
    } catch (final IllegalArgumentException e) {
      // not a cron expression, try parsing as ISO8601 duration
      try {
        final var duration = Duration.parse(expression);
        return new IntervalSchedule(duration);
      } catch (final DateTimeParseException ex) {
        throw new IllegalArgumentException(
            "Invalid expression for schedule: must be one of CRON, ISO8601, NONE or AUTO. Given: "
                + expression,
            ex);
      }
    }
  }

  static Schedule none() {
    return new NoneSchedule();
  }

  record CronSchedule(Cron cronExpr) implements Schedule {

    @Override
    public Optional<Instant> nextExecution(final Instant from) {
      return ExecutionTime.forCron(cronExpr)
          .nextExecution(from.atZone(ZoneId.systemDefault()))
          .map(ChronoZonedDateTime::toInstant);
    }

    @Override
    public Optional<Instant> previousExecution(final Instant from) {
      return ExecutionTime.forCron(cronExpr)
          .lastExecution(from.atZone(ZoneId.systemDefault()))
          .map(ChronoZonedDateTime::toInstant);
    }
  }

  record IntervalSchedule(Duration interval) implements Schedule {

    @Override
    public Optional<Instant> nextExecution(final Instant from) {
      return Optional.of(from.plus(interval));
    }

    @Override
    public Optional<Instant> previousExecution(final Instant from) {
      return Optional.of(from.minus(interval));
    }
  }

  record NoneSchedule() implements Schedule {

    @Override
    public Optional<Instant> nextExecution(final Instant from) {
      return Optional.empty();
    }

    @Override
    public Optional<Instant> previousExecution(final Instant from) {
      return Optional.empty();
    }
  }
}
