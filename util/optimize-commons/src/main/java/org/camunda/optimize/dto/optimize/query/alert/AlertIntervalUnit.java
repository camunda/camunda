/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.alert;

import com.fasterxml.jackson.annotation.JsonValue;

public enum AlertIntervalUnit {
  SECONDS,
  MINUTES,
  HOURS,
  DAYS,
  WEEKS,
  MONTHS,
  ;

  @JsonValue
  public String getId() {
    return name().toLowerCase();
  }
}
