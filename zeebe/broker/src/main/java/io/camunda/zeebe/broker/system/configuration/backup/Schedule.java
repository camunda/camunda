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

/** Represents a schedule which can be either a CRON expression or an ISO8601 duration interval. */
public sealed interface Schedule {

  /*
   * Provide the next execution of the scheduler
   */
  Optional<Instant> nextExecution(Instant from);

  /*
   * In reality the previous execution will be acquired from the checkpoint state
   */
  Optional<Instant> previousExecution(Instant from);

  static Schedule tryParse(final String expression) throws IllegalArgumentException {
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
}
