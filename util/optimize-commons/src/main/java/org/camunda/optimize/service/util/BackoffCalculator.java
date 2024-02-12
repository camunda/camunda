/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util;

import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class BackoffCalculator {
  private static final double BACKOFF_MULTIPLIER = 1.5;

  private long currentTimeToWait;
  private long initialBackoff;
  private OffsetDateTime nextRetryTime = OffsetDateTime.now().minusMinutes(1L);
  private long maximumBackoff;

  @Autowired
  public BackoffCalculator(final ConfigurationService configurationService) {
    this(configurationService.getMaximumBackoff(), configurationService.getInitialBackoff());
  }

  public BackoffCalculator(long maximumBackoffSeconds, long initialBackoffMillis) {
    this.maximumBackoff = maximumBackoffSeconds * 1000;
    this.currentTimeToWait = initialBackoffMillis;
    this.initialBackoff = initialBackoffMillis;
  }

  public boolean isMaximumBackoffReached() {
    return currentTimeToWait >= maximumBackoff;
  }

  public long calculateSleepTime() {
    currentTimeToWait = Math.min(Math.round(currentTimeToWait * BACKOFF_MULTIPLIER), maximumBackoff);
    nextRetryTime = OffsetDateTime.now().plus(Math.round(currentTimeToWait), ChronoUnit.MILLIS);
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
