/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.backup;

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

  String AUTO = "auto";
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

  ScheduleType getType();

  /**
   * @param expression the expression string to parse
   * @param mustMatch if true, the expression must be a valid cron or ISO8601 duration
   * @return the typed {@link Schedule}
   * @throws IllegalArgumentException if the expression is invalid
   */
  static Schedule parseSchedule(final String expression, final boolean mustMatch)
      throws IllegalArgumentException {
    if (!mustMatch && expression == null
        || expression.isBlank()
        || expression.equalsIgnoreCase(NONE)) {
      return new NoneSchedule();
    }
    if (!mustMatch && expression.equalsIgnoreCase(AUTO)) {
      return new AutoSchedule();
    }
    return mustParseSchedule(expression);
  }

  private static Schedule mustParseSchedule(final String expression) {
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
            "Invalid schedule expression: must be either a valid CRON expression or an ISO8601 duration",
            ex);
      }
    }
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

    @Override
    public ScheduleType getType() {
      return ScheduleType.CRON;
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

    @Override
    public ScheduleType getType() {
      return ScheduleType.INTERVAL;
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

    @Override
    public ScheduleType getType() {
      return ScheduleType.NONE;
    }
  }

  record AutoSchedule() implements Schedule {

    @Override
    public Optional<Instant> nextExecution(final Instant from) {
      return Optional.empty();
    }

    @Override
    public Optional<Instant> nextExecution(
        final Instant from, final Supplier<Duration> intervalSupplier) {
      return Optional.of(from.plus(intervalSupplier.get()));
    }

    @Override
    public Optional<Instant> previousExecution(final Instant from) {
      return Optional.empty();
    }

    @Override
    public ScheduleType getType() {
      return ScheduleType.AUTO;
    }
  }

  enum ScheduleType {
    CRON,
    INTERVAL,
    NONE,
    AUTO
  }
}
