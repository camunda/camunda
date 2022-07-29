/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.timer;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import io.camunda.zeebe.model.bpmn.util.time.Interval;
import io.camunda.zeebe.model.bpmn.util.time.Timer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;

public class CronTimer implements Timer {

  private final Cron cron;

  private int repetitions;

  public CronTimer(final Cron cron) {
    this.cron = cron;
  }

  @Override
  public Interval getInterval() {
    return null;
  }

  @Override
  public int getRepetitions() {
    return repetitions;
  }

  @Override
  public long getDueDate(final long fromEpochMilli) {
    // set default value to -1
    repetitions = -1;

    final var next =
        ExecutionTime.forCron(cron)
            .nextExecution(
                ZonedDateTime.ofInstant(
                    Instant.ofEpochMilli(fromEpochMilli), ZoneId.systemDefault()))
            .map(ZonedDateTime::toInstant)
            .map(Instant::toEpochMilli);

    // set `repetitions` to 0 when the next execution time does not exist
    if (next.isEmpty()) {
      repetitions = 0;
    }

    return next.orElse(fromEpochMilli);
  }

  public static CronTimer parse(final String text) {
    try {
      final var cron =
          new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.SPRING53))
              .parse(text);
      return new CronTimer(cron);
    } catch (final IllegalArgumentException | NullPointerException ex) {
      throw new DateTimeParseException(ex.getMessage(), Objects.requireNonNullElse(text, ""), 0);
    }
  }
}
