/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.leadership;

import java.time.Duration;

/**
 * The timing of the leases.
 *
 * @param leaseDuration how long an observer waits for an unchanged renewal counter before it treats
 *     the leader as failed. It is the floor for detecting a failed leader in another region.
 * @param renewInterval how often a leader renews, and how often a node reads the lease rows
 * @param fenceAfter how long a leader may go without a successful renewal before it stops leading
 *     by itself. It is shorter than the lease duration, so the leader stops before another node can
 *     claim.
 */
public record LeaseConfig(Duration leaseDuration, Duration renewInterval, Duration fenceAfter) {

  public LeaseConfig {
    if (renewInterval.isZero() || renewInterval.isNegative()) {
      throw new IllegalArgumentException("renew interval must be positive");
    }
    if (renewInterval.compareTo(fenceAfter) >= 0) {
      throw new IllegalArgumentException("renew interval must be shorter than fence after");
    }
    if (fenceAfter.compareTo(leaseDuration) >= 0) {
      throw new IllegalArgumentException("fence after must be shorter than the lease duration");
    }
  }

  /** Renews every third of the lease, and fences after half of it, as the proposal describes. */
  public static LeaseConfig of(final Duration leaseDuration) {
    return new LeaseConfig(leaseDuration, leaseDuration.dividedBy(3), leaseDuration.dividedBy(2));
  }
}
