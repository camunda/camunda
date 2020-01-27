/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.filter.data.date;

import com.fasterxml.jackson.annotation.JsonValue;

public enum DateFilterUnit {
  YEARS("years"),
  QUARTERS("quarters"),
  MONTHS("months"),
  WEEKS("weeks"),
  DAYS("days"),
  HOURS("hours"),
  MINUTES("minutes"),
  ;

  private final String id;

  DateFilterUnit(final String id) {
    this.id = id;
  }

  @JsonValue
  public String getId() {
    return id;
  }
}
