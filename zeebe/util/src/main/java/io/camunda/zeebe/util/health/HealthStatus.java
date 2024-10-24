/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.health;

import java.util.Comparator;

public enum HealthStatus {
  HEALTHY,
  UNHEALTHY,
  DEAD;

  public static final Comparator<HealthStatus> COMPARATOR =
      Comparator.comparingInt(HealthStatus::ordinal);

  public HealthStatus combine(final HealthStatus other) {
    return compareTo(other) > 0 ? this : other;
  }
}
