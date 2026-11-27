/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.health;

import java.util.Comparator;

/**
 * Enum cases are ordered based on the "severity" of the status. The ordinal field of the
 * enumeration implicitly determines its severity.
 */
public enum HealthStatus {
  HEALTHY,
  /**
   * The component works, but something worth an operator's attention was observed, for example a
   * latency outlier. Readiness, the topology and the partition health that clients see treat it as
   * healthy; only the health tree and its history show it.
   */
  DEGRADED,
  UNHEALTHY,
  DEAD;

  /** The comparator just uses the ordinal field to order the statuses */
  public static final Comparator<HealthStatus> COMPARATOR =
      Comparator.comparingInt(HealthStatus::ordinal);

  /**
   * Symmetric binary operations to reduce HealthStatus
   *
   * @return an HealthStatus using the following rules:
   *     <ul>
   *       <li>if any of the two is DEAD -> DEAD
   *       <li>if any of the two is UNHEALTHY -> UNHEALTHY
   *       <li>if any of the two is DEGRADED -> DEGRADED
   *       <li>if both are HEALTHY -> HEALTHY
   *     </ul>
   */
  public HealthStatus combine(final HealthStatus other) {
    return compareTo(other) > 0 ? this : other;
  }

  /** Whether everything outside the health tree should treat this status as healthy. */
  public boolean isHealthyOrDegraded() {
    return this == HEALTHY || this == DEGRADED;
  }
}
