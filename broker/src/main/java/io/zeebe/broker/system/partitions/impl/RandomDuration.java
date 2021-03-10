/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.system.partitions.impl;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

public final class RandomDuration {

  private RandomDuration() {}

  /**
   * Returns a pseudo-random duration between the given minimum and maximum duration.
   *
   * <p>If the max duration is smaller or equals to the minimum duration, then the minimum duration
   * is returned. This ensure to always have a base line or lower limit.
   *
   * <p>The random duration is minute based, so if the given duration differ only in seconds then
   * the minimum duration is returned
   *
   * @param minDuration the minimum duration, inclusive
   * @param maxDuration the maximum duration, exclusive
   * @return a pseudo-random duration between the minimum and maximum duration
   */
  public static Duration getRandomDurationMinuteBased(
      final Duration minDuration, final Duration maxDuration) {
    if (minDuration.toMinutes() >= maxDuration.toMinutes()) {
      return minDuration;
    }

    final var maxMinutes = maxDuration.minus(minDuration).toMinutes();

    final var threadLocalRandom = ThreadLocalRandom.current();
    final var randomMinutes = threadLocalRandom.nextLong(0, maxMinutes);

    // base min duration
    return minDuration.plusMinutes(randomMinutes);
  }
}
