/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TargetValueUnit {

  MILLIS("millis"),
  SECONDS("seconds"),
  MINUTES("minutes"),
  HOURS("hours"),
  DAYS("days"),
  WEEKS("weeks"),
  MONTHS("months"),
  YEARS("years"),
  ;

  private final String id;

  TargetValueUnit(final String id) {
    this.id = id;
  }

  @JsonValue
  public String getId() {
    return id;
  }
}
