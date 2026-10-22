/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector.swim;

import java.util.List;

/**
 * The decision to fail over from a failed region to a surviving one, with the evidence.
 *
 * @param failedRegion the data region that is gone
 * @param targetRegion the healthy data region with the highest priority
 * @param leaderId the system partition leader that decided
 * @param term the term of that leader
 * @param detectedAtMillis when the region was first detected as gone, by the leader's wall clock;
 *     the leader waited for the recovery window from then on before deciding
 * @param decidedAtMillis when it decided, by the leader's wall clock; informational only
 * @param goneBrokers the brokers of the failed region that were out of sight, and for how long
 */
record RegionFailoverDecision(
    String failedRegion,
    String targetRegion,
    String leaderId,
    long term,
    long detectedAtMillis,
    long decidedAtMillis,
    List<GoneBroker> goneBrokers) {

  RegionFailoverDecision {
    goneBrokers = List.copyOf(goneBrokers);
  }

  RegionFailoverDecision withDetectedAt(final long detectedAtMillis) {
    return new RegionFailoverDecision(
        failedRegion, targetRegion, leaderId, term, detectedAtMillis, decidedAtMillis, goneBrokers);
  }

  CommittedFailover committed() {
    return new CommittedFailover(failedRegion, targetRegion, decidedAtMillis);
  }

  /** The part of a committed failover that tells whether a later failure is a new one. */
  record CommittedFailover(String failedRegion, String targetRegion, long decidedAtMillis) {}
}
