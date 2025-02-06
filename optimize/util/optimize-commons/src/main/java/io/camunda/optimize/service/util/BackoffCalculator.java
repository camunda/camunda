/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class BackoffCalculator {

  private static final double BACKOFF_MULTIPLIER = 1.5;

  private long currentTimeToWait;
  private final long initialBackoff;
  private OffsetDateTime nextRetryTime = OffsetDateTime.now().minusMinutes(1L);
  private final long maximumBackoff;

  @Autowired
  public BackoffCalculator(final ConfigurationService configurationService) {
    this(configurationService.getMaximumBackoff(), configurationService.getInitialBackoff());
  }

  public BackoffCalculator(final long maximumBackoffSeconds, final long initialBackoffMillis) {
    maximumBackoff = maximumBackoffSeconds * 1000;
    currentTimeToWait = initialBackoffMillis;
    initialBackoff = initialBackoffMillis;
  }

  public boolean isMaximumBackoffReached() {
    return currentTimeToWait >= maximumBackoff;
  }

  public long calculateSleepTime() {
    currentTimeToWait =
        Math.min(Math.round((double) currentTimeToWait * BACKOFF_MULTIPLIER), maximumBackoff);
    nextRetryTime =
        OffsetDateTime.now().plus(Math.round((double) currentTimeToWait), ChronoUnit.MILLIS);
    return currentTimeToWait;
  }

  public long getTimeUntilNextRetry() {
    long backoffTime = OffsetDateTime.now().until(nextRetryTime, ChronoUnit.MILLIS);
    backoffTime = Math.max(0, backoffTime);
    return backoffTime;
  }

  public boolean isReadyForNextRetry() {
    return nextRetryTime.isBefore(LocalDateUtil.getCurrentDateTime());
  }

  public void resetBackoff() {
    currentTimeToWait = initialBackoff;
    nextRetryTime = OffsetDateTime.now().minusMinutes(1L);
  }

  public long getMaximumBackoffMilliseconds() {
    return maximumBackoff;
  }
}
