/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.filter.data.date;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * This Enum is a subset of the values available in {@link java.time.temporal.ChronoUnit}. It
 * reflects the values allowed for duration filters on the Optimize Report API.
 */
public enum DurationUnit {
  YEARS("years"),
  MONTHS("months"),
  WEEKS("weeks"),
  HALF_DAYS("halfDays"),
  DAYS("days"),
  HOURS("hours"),
  MINUTES("minutes"),
  SECONDS("seconds"),
  MILLIS("millis");

  private final String id;

  DurationUnit(final String id) {
    this.id = id;
  }

  @JsonValue
  public String getId() {
    return id;
  }
}
