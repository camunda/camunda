/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

/** Configuration of the {@link FailureDetectorType#TICK_TOCK tick-tock} failure detector. */
public final class TickTockCfg implements ConfigurationEntry {
  public static final Duration DEFAULT_TICK_INTERVAL = Duration.ofSeconds(1);
  public static final int DEFAULT_MISSED_TICKS_THRESHOLD = 5;

  private Duration tickInterval = DEFAULT_TICK_INTERVAL;
  private int missedTicksThreshold = DEFAULT_MISSED_TICKS_THRESHOLD;
  // when not set, half of the missed ticks threshold, rounded up
  private @Nullable Integer suspectMissedTicksThreshold;

  @Override
  public void init(final BrokerCfg globalConfig, final String brokerBase) {
    if (tickInterval.toMillis() < 1) {
      throw new IllegalArgumentException(
          "tickInterval %s must be at least 1ms".formatted(tickInterval));
    }
    if (missedTicksThreshold < 1) {
      throw new IllegalArgumentException(
          "missedTicksThreshold %d must be at least 1".formatted(missedTicksThreshold));
    }
    final var suspect = getEffectiveSuspectMissedTicksThreshold();
    if (suspect < 1 || suspect > missedTicksThreshold) {
      throw new IllegalArgumentException(
          "suspectMissedTicksThreshold %d must be at least 1 and at most missedTicksThreshold %d"
              .formatted(suspect, missedTicksThreshold));
    }
  }

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

  public @Nullable Integer getSuspectMissedTicksThreshold() {
    return suspectMissedTicksThreshold;
  }

  public void setSuspectMissedTicksThreshold(final @Nullable Integer suspectMissedTicksThreshold) {
    this.suspectMissedTicksThreshold = suspectMissedTicksThreshold;
  }

  /**
   * Returns how many ticks a broker may miss before it is suspected: the configured value, or half
   * of the missed ticks threshold, rounded up.
   */
  public int getEffectiveSuspectMissedTicksThreshold() {
    return suspectMissedTicksThreshold != null
        ? suspectMissedTicksThreshold
        : (missedTicksThreshold + 1) / 2;
  }

  @Override
  public String toString() {
    return "TickTockCfg{"
        + "tickInterval="
        + tickInterval
        + ", missedTicksThreshold="
        + missedTicksThreshold
        + ", suspectMissedTicksThreshold="
        + suspectMissedTicksThreshold
        + '}';
  }
}
