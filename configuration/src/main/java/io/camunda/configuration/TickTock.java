/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.time.Duration;

/**
 * The tick-tock failure detector: the system partition leader commits a tick every interval, and
 * every other broker acknowledges each tick, which the leader commits with its next tick.
 */
public class TickTock {

  /** How often the system partition leader commits a tick. */
  private Duration tickInterval = Duration.ofSeconds(1);

  /**
   * How many ticks may commit since a broker was last seen before it is considered dead. A healthy
   * broker is seen one tick late, as its acknowledgement is committed with the next tick.
   */
  private int missedTicksThreshold = 5;

  /**
   * How many ticks may commit since a broker was last seen before it is suspected. Must be at most
   * the missed ticks threshold. Defaults to half of the missed ticks threshold, rounded up.
   */
  private Integer suspectMissedTicksThreshold;

  public Duration getTickInterval() {
    return tickInterval;
  }

  public void setTickInterval(final Duration tickInterval) {
    this.tickInterval = tickInterval;
  }

  public int getMissedTicksThreshold() {
    return missedTicksThreshold;
  }

  public void setMissedTicksThreshold(final int missedTicksThreshold) {
    this.missedTicksThreshold = missedTicksThreshold;
  }

  public Integer getSuspectMissedTicksThreshold() {
    return suspectMissedTicksThreshold;
  }

  public void setSuspectMissedTicksThreshold(final Integer suspectMissedTicksThreshold) {
    this.suspectMissedTicksThreshold = suspectMissedTicksThreshold;
  }

  @Override
  public String toString() {
    return "TickTock{"
        + "tickInterval="
        + tickInterval
        + ", missedTicksThreshold="
        + missedTicksThreshold
        + ", suspectMissedTicksThreshold="
        + suspectMissedTicksThreshold
        + '}';
  }
}
