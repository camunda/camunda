/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

public final class RandomDuration {

  public static final Duration ONE_MINUTE = Duration.ofMinutes(1);

  private RandomDuration() {}

  /**
   * Returns a pseudo-random duration between the given minimum and maximum duration.
   *
   * <p>If the max duration is smaller or equals to the minimum duration, then the minimum duration
   * is returned. This ensure to always have a base line or lower limit.
   *
   * @param minDuration the minimum duration, inclusive
   * @param maxDuration the maximum duration, exclusive
   * @return a pseudo-random duration between the minimum and maximum duration
   */
  public static Duration getRandomDuration(Duration minDuration, Duration maxDuration) {
    if (minDuration.toMillis() >= maxDuration.toMillis()) {
      return minDuration;
    }

    final var maxMilliseconds = maxDuration.minus(minDuration).toMillis();

    final ThreadLocalRandom threadLocalRandom = ThreadLocalRandom.current();
    final var randomMiliseconds = threadLocalRandom.nextLong(0, maxMilliseconds);

    // base min duration
    return minDuration.plusMillis(randomMiliseconds);
  }
}
