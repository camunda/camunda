/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol.automatic;

import java.time.Duration;

/**
 * Parameters of the automatic write rate limit. All values are starting points, the load test is
 * expected to give the real values.
 *
 * @param recordsPerCpuSecond records per second that one CPU handles on a leader, exporters
 *     included ({@code R})
 * @param leaderCost relative CPU cost of a leader replica ({@code LEADER})
 * @param followerCost relative CPU cost of a follower replica ({@code FOLLOWER})
 * @param window evaluation window of the controller
 * @param decreaseFactor multiplicative decrease of the ceiling
 * @param increaseStepRatio additive increase of the ceiling, as a fraction of the start value
 * @param throttledRatioThreshold share of throttled CFS periods above which a window counts as CPU
 *     throttled
 * @param throttledWindows consecutive CPU throttled windows that trigger the CPU guard
 */
public record AutomaticWriteRateLimitCfg(
    double recordsPerCpuSecond,
    double leaderCost,
    double followerCost,
    Duration window,
    double decreaseFactor,
    double increaseStepRatio,
    double throttledRatioThreshold,
    int throttledWindows) {

  public static AutomaticWriteRateLimitCfg defaults() {
    return new AutomaticWriteRateLimitCfg(
        10_000, 1, 0.6, Duration.ofSeconds(10), 0.9, 0.05, 0.1, 2);
  }

  /** CPU cost of one replica for an assumed share {@code lambda} of led replicas. */
  double costPerReplica(final double lambda) {
    return lambda * leaderCost + (1 - lambda) * followerCost;
  }
}
