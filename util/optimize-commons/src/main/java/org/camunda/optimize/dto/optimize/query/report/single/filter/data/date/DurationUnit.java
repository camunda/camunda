/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.filter.data.date;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * This Enum is a subset of the values available in {@link java.time.temporal.ChronoUnit}.
 * It reflects the values allowed for duration filters on the Optimize Report API.
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
  MILLIS("millis"),
  ;

  private final String id;

  DurationUnit(final String id) {
    this.id = id;
  }

  @JsonValue
  public String getId() {
    return id;
  }
}
