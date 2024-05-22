/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.filter.data.date;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum DateUnit {
  YEARS("years"),
  QUARTERS("quarters"),
  MONTHS("months"),
  WEEKS("weeks"),
  DAYS("days"),
  HOURS("hours"),
  MINUTES("minutes"),
  SECONDS("seconds"),
  ;

  private final String id;

  @JsonValue
  public String getId() {
    return id;
  }
}
