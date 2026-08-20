/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.businessvalue;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Kpi {
  CYCLE_TIME("cycleTime"),
  AUTOMATION_RATE("automationRate");

  private final String id;

  Kpi(final String id) {
    this.id = id;
  }

  @JsonValue
  public String getId() {
    return id;
  }

  public static Kpi fromId(final String id) {
    for (final Kpi kpi : values()) {
      if (kpi.id.equals(id)) {
        return kpi;
      }
    }
    throw new IllegalArgumentException(
        "Unknown kpi id [" + id + "]; must be one of: cycleTime, automationRate");
  }
}
