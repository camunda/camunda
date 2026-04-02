/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import com.google.common.util.concurrent.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExporterBackpressure {
  private static final Logger logger = LoggerFactory.getLogger(ExporterBackpressure.class);
  private volatile boolean rateLimit = false;
  private volatile int cost = 1;
  private final RateLimiter rateLimiter = RateLimiter.create(1_000_000);

  public void backpressure() {
    if (rateLimit) {
      final var w = rateLimiter.acquire(cost);
      if (w > 0) {
        // TODO metrics for how long we had to wait due to backpressure
        // logger.info("Applying backpressure with cost of {}, waited {} seconds", cost, w);
      }
    }
  }

  public void enable(final int cost) {
    if (this.cost != cost) {
      logger.warn("Enabling exporter backpressure with cost of {}", cost);
      this.cost = cost;
    }
    rateLimit = true;
  }

  public void disable() {
    if (rateLimit) {
      logger.info("Disabling exporter backpressure");
    }
    rateLimit = false;
  }
}
