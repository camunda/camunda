/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.qa.migration.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MigrationPerformanceTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(MigrationPerformanceTest.class);

  @Test
  public void testPerformanceDuration() {
    int timeoutInMinutes = 60;
    try {
      timeoutInMinutes = Integer.parseInt(System.getProperty("MIGRATION_TIMEOUT"));
    } catch (NumberFormatException e) {
      LOGGER.error(
          "Couldn't parse integer value of environment variable MIGRATION_TIMEOUT use default {} minutes.",
          timeoutInMinutes);
    }
    final long timeout = TimeUnit.MINUTES.toMillis(timeoutInMinutes);
    final Instant start = Instant.now();
    final Instant finish = Instant.now();
    final long timeElapsed = Duration.between(start, finish).toMillis();
    assertThat(timeElapsed)
        .as(
            "Needed "
                + TimeUnit.MILLISECONDS.toMinutes(timeElapsed)
                + " of maximal "
                + timeoutInMinutes
                + " minutes.")
        .isLessThan(timeout);
  }
}
