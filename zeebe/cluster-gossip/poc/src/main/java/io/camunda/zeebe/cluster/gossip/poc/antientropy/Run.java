/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.cluster.gossip.poc.antientropy;

import java.util.Comparator;

/**
 * Identifies one run of a member. SWIM has its own run identity (an instance id), but does not
 * expose it, so this transport needs its own. A later run of the same member is ordered by its
 * start time on that member's own clock; the nonce separates runs started in the same millisecond.
 * A member whose clock jumps backwards across a restart would look older than its previous run; the
 * proof of concept accepts that.
 */
record Run(long startedAt, long nonce) implements Comparable<Run> {

  private static final Comparator<Run> ORDER =
      Comparator.comparingLong(Run::startedAt).thenComparingLong(Run::nonce);

  @Override
  public int compareTo(final Run other) {
    return ORDER.compare(this, other);
  }

  boolean isNewerThan(final Run other) {
    return compareTo(other) > 0;
  }
}
